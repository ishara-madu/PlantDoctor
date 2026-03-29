package com.pixeleye.plantdoctor.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.pixeleye.plantdoctor.BuildConfig
import com.pixeleye.plantdoctor.data.UserPreferencesRepository
import com.pixeleye.plantdoctor.data.api.DiagnosisResponse
import com.pixeleye.plantdoctor.data.api.PlantScanDto
import com.pixeleye.plantdoctor.data.api.SupabaseClientProvider
import com.pixeleye.plantdoctor.data.api.PlantScanRepository
import com.pixeleye.plantdoctor.data.api.UserQuotaRepository
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

sealed class DiagnosisState {
    data object Idle : DiagnosisState()
    data object Loading : DiagnosisState()
    data class Success(val result: DiagnosisResponse) : DiagnosisState()
    data class Error(val message: String) : DiagnosisState()
}

sealed class UploadState {
    data object Idle : UploadState()
    data object Uploading : UploadState()
    data class Success(val imageUrl: String) : UploadState()
    data class Error(val message: String) : UploadState()
}

/**
 * Intermediate model for parsing Gemini's structured JSON response.
 * Determines whether the image is a plant before committing to storage/DB writes.
 */
private data class GeminiAnalysisResponse(
    @SerializedName("is_plant") val isPlant: Boolean,
    @SerializedName("diagnosis_summary") val diagnosisSummary: String,
    @SerializedName("action_plan") val actionPlan: List<String>
)

class PlantDiagnosisViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val plantScanRepository: PlantScanRepository,
    private val userQuotaRepository: UserQuotaRepository
) : ViewModel() {

    companion object {
        private const val TAG = "PlantDiagnosisVM"
        private const val STORAGE_BUCKET = "plant-images"
        private const val TABLE_NAME = "plant_scans"
    }

    private val generativeModel: GenerativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY,
            systemInstruction = content {
                text("""You are an expert agricultural and gardening assistant. Analyze the provided image and determine whether it contains a plant.

You MUST return ONLY a single valid JSON object — no markdown fences, no prose, no extra text. The JSON must follow this exact structure:

{
  "is_plant": true or false,
  "diagnosis_summary": "A plain text description.",
  "action_plan": ["step 1", "step 2"]
}

Rules:
- If the image DOES contain a plant: set "is_plant" to true, provide a detailed diagnosis of any diseases, nutrient deficiencies, or pests in "diagnosis_summary", and list actionable treatment steps in "action_plan".
- If the image does NOT contain a plant: set "is_plant" to false, describe what the image actually shows in "diagnosis_summary" (e.g. "This appears to be a photo of a golden retriever."), and set "action_plan" to an empty array [].

CRITICAL SAFETY RULE: If you recommend any chemical treatments, pesticides, or fertilizers, DO NOT provide exact dosages (e.g., do not say 'mix 5ml per liter'). Instead, suggest the active ingredient or general type of treatment, and STRICTLY ADVISE the user to 'always read the manufacturer's label for the correct dosage and safety instructions'. You may, however, suggest the best time of day to apply the treatment (e.g., early morning or late evening).""".trimIndent())
            },
            generationConfig = generationConfig {
                temperature = 0.7f
                topK = 32
                topP = 0.95f
                maxOutputTokens = 8192
                responseMimeType = "application/json"
            }
        )
    }

    private val supabaseClient by lazy {
        SupabaseClientProvider.getClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        )
    }

    private val _diagnosisState = MutableStateFlow<DiagnosisState>(DiagnosisState.Idle)
    val diagnosisState: StateFlow<DiagnosisState> = _diagnosisState.asStateFlow()

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    private val _scanCount = MutableStateFlow(0)
    val scanCount: StateFlow<Int> = _scanCount.asStateFlow()

    suspend fun checkQuota(): Int {
        return try {
            val count = userQuotaRepository.checkQuota()
            _scanCount.value = count
            count
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check quota", e)
            throw e
        }
    }

    suspend fun incrementQuota() {
        try {
            userQuotaRepository.incrementQuota()
            _scanCount.value = _scanCount.value + 1
        } catch (e: Exception) {
            Log.e(TAG, "Failed to increment quota", e)
        }
    }

    fun analyzePlant(image: Bitmap, userNotes: String = "", imageUri: Uri? = null, locationStr: String? = null) {
        viewModelScope.launch {
            _diagnosisState.value = DiagnosisState.Loading
            _uploadState.value = UploadState.Idle

            try {
                // Read saved preferences to personalize the prompt
                val prefs = userPreferencesRepository.userPreferences.first()
                val country = prefs.country
                val aiLanguage = prefs.selectedAiLanguage

                val personalizationContext = buildString {
                    if (country.isNotBlank() || aiLanguage.isNotBlank() || locationStr != null) {
                        appendLine("CONTEXT:")
                        if (aiLanguage.isNotBlank()) {
                            appendLine("- You MUST provide your final JSON structured diagnosis ONLY in $aiLanguage.")
                        }

                        // Prefer dynamic robust location over static country preferences
                        val targetLocation = locationStr ?: country
                        if (targetLocation.isNotBlank()) {
                            appendLine("- The user is located at/in: $targetLocation. Suggest agricultural treatments, chemical compositions, and organic solutions that are locally available and commonly used in this region.")
                            appendLine("- Mention specific local brands, agrochemical suppliers, or farming practices relevant to $targetLocation when appropriate.")
                        }
                        appendLine()
                    }
                }

                val basePrompt = userNotes.ifBlank {
                    "Please analyze this plant image and identify any diseases, pests, or nutrient deficiencies. Provide a detailed treatment plan."
                }

                val fullPrompt = "$personalizationContext$basePrompt"

                Log.d(TAG, "Analyzing with context — locationStr=$locationStr, fallbackCountry=$country, aiLanguage=$aiLanguage")

                val inputContent = content {
                    image(image)
                    text(fullPrompt)
                }

                val response = generativeModel.generateContent(inputContent)
                val resultText = response.text
                    ?: throw Exception("No response generated from AI model.")

                Log.d(TAG, "Raw Gemini response: $resultText")

                // Parse into the intermediate model that includes is_plant
                val geminiResult = try {
                    Gson().fromJson(resultText, GeminiAnalysisResponse::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse Gemini JSON: $resultText", e)
                    throw Exception("AI returned an unexpected format. Please try again.")
                }

                // ── Decision gate: is this actually a plant? ────────────
                if (!geminiResult.isPlant) {
                    // Not a plant — show Gemini's descriptive feedback as an error and STOP.
                    // No storage upload. No DB record. No wasted resources.
                    Log.d(TAG, "Image rejected as non-plant: ${geminiResult.diagnosisSummary}")
                    _diagnosisState.value = DiagnosisState.Error(
                        geminiResult.diagnosisSummary.ifBlank {
                            "The image does not appear to contain a plant. Please try again with a plant photo."
                        }
                    )
                    return@launch
                }

                // ── It IS a plant — proceed with full diagnosis flow ─────
                val diagnosisResponse = DiagnosisResponse(
                    summary = geminiResult.diagnosisSummary,
                    actionPlan = geminiResult.actionPlan
                )

                _diagnosisState.value = DiagnosisState.Success(diagnosisResponse)

                // Format for Supabase storage
                val stringifiedTreatmentPlan = buildString {
                    append(geminiResult.diagnosisSummary)
                    if (geminiResult.actionPlan.isNotEmpty()) {
                        append("\n\nAction Plan:\n")
                        geminiResult.actionPlan.forEach { step ->
                            append("- $step\n")
                        }
                    }
                }.trim()

                // Upload to Supabase in background (only for confirmed plants)
                uploadToSupabase(
                    image = image,
                    imageUri = imageUri,
                    diseaseTitle = "Plant Analysis",
                    treatmentPlan = stringifiedTreatmentPlan
                )

            } catch (e: Exception) {
                Log.e(TAG, "Gemini analysis failed", e)
                _diagnosisState.value = DiagnosisState.Error(
                    e.message ?: "An unknown error occurred during analysis."
                )
            }
        }
    }

    private fun uploadToSupabase(
        image: Bitmap,
        imageUri: Uri?,
        diseaseTitle: String,
        treatmentPlan: String
    ) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading
            try {
                val currentUser = supabaseClient.auth.currentUserOrNull()
                val currentUserId = currentUser?.id 
                    ?: throw Exception("User is not logged in. Cannot upload scan.")

                val imageBytes = withContext(Dispatchers.IO) {
                    compressBitmapToJpeg(image)
                }

                val fileName = "${UUID.randomUUID()}.jpg"
                val bucket = supabaseClient.storage.from(STORAGE_BUCKET)
                bucket.upload(path = fileName, data = imageBytes, upsert = false)
                Log.d(TAG, "Image uploaded to storage: $fileName")

                val imageUrl = bucket.publicUrl(fileName)
                Log.d(TAG, "Public URL: $imageUrl")

                val scanDto = PlantScanDto(
                    userId = currentUserId,
                    imageUrl = imageUrl,
                    diseaseTitle = diseaseTitle,
                    treatmentPlan = treatmentPlan
                )
                plantScanRepository.insertScan(scanDto)
                Log.d(TAG, "Record inserted into repository and local DB")

                _uploadState.value = UploadState.Success(imageUrl)

            } catch (e: Exception) {
                Log.e("SupabaseError", "Supabase upload failed: ${e.message}", e)
                _uploadState.value = UploadState.Error(
                    e.message ?: "Failed to save scan to cloud."
                )
            }
        }
    }

    private fun compressBitmapToJpeg(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return stream.toByteArray()
    }

    fun resetState() {
        _diagnosisState.value = DiagnosisState.Idle
        _uploadState.value = UploadState.Idle
    }

    class Factory(
        private val userPreferencesRepository: UserPreferencesRepository,
        private val plantScanRepository: PlantScanRepository,
        private val userQuotaRepository: UserQuotaRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlantDiagnosisViewModel(userPreferencesRepository, plantScanRepository, userQuotaRepository) as T
        }
    }
}
