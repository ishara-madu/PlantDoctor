package com.pixeleye.plantdoctor.viewmodel

import android.content.Context
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
import com.pixeleye.plantdoctor.data.api.UserQuotaDto
import com.pixeleye.plantdoctor.utils.compressImageHighQuality
import com.pixeleye.plantdoctor.utils.decodeDownscaledBitmap
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
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.io.ByteArrayOutputStream
import java.io.IOException
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
    @SerializedName("organic_treatments") val organicTreatments: List<String>,
    @SerializedName("chemical_treatments") val chemicalTreatments: List<String>
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
                text("""You are an expert, highly authoritative agricultural and botanical pathologist. Analyze the provided image to diagnose plant health.

You MUST return ONLY a single valid JSON object. No markdown, no prose. The JSON must strictly follow this structure:

{
  "is_plant": true or false,
  "diagnosis_summary": "A detailed explanation of the disease, pest, or nutrient deficiency.",
  "organic_treatments": ["Organic step 1", "Natural step 2"],
  "chemical_treatments": ["Chemical step 1", "Agrochemical step 2"]
}

Rules:
1. If the image is NOT a plant: set "is_plant" to false, explain what it is in "diagnosis_summary", and leave both treatment arrays empty [].
2. If it IS a plant: set "is_plant" to true. Provide a precise "diagnosis_summary".
3. "organic_treatments": List actionable, natural, DIY, or organic farming methods (e.g., Neem oil, pruning, compost).
4. "chemical_treatments": List specific, commercially available agrochemical treatments, synthetic fertilizers, or pesticides.
5. CRITICAL SAFETY RULE: For chemical treatments, suggest the active ingredient or class of chemical, but STRICTLY advise the user to 'read the manufacturer's label for dosage and safety'.""".trimIndent())
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

    private val _snackbarEvent = MutableStateFlow<com.pixeleye.plantdoctor.ui.components.SnackbarState?>(null)
    val snackbarEvent: StateFlow<com.pixeleye.plantdoctor.ui.components.SnackbarState?> = _snackbarEvent.asStateFlow()

    suspend fun checkQuota(): UserQuotaDto {
        return try {
            val quota = userQuotaRepository.checkQuota()
            _scanCount.value = quota.dailyCount
            quota
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

    fun analyzePlant(image: Bitmap, userNotes: String = "", imageUri: Uri? = null, locationStr: String? = null, context: Context? = null, isPremium: Boolean = false) {
        viewModelScope.launch {
            _diagnosisState.value = DiagnosisState.Loading
            _uploadState.value = UploadState.Idle

            try {
                // 1. UNIVERSAL QUOTA CHECK (Fair Use Policy: 3 for Free, 50 for Pro)
                val currentQuota = try {
                    checkQuota()
                } catch (e: Exception) {
                    Log.e(TAG, "Quota check failed", e)
                    // If DB is down, we allow it for Free users but log it as a risk.
                    // For now, we'll be strict to protect the API costs.
                    null
                }

                if (currentQuota != null) {
                    val maxLimit = if (isPremium) 50 else 6
                    if (currentQuota.dailyCount >= maxLimit) {
                        val limitMsg = if (isPremium) {
                            "Daily fair-use limit of 50 scans reached for PRO users."
                        } else {
                            "Daily limit of 6 scans reached. Upgrade to PRO for 50 scans/day!"
                        }
                        _diagnosisState.value = DiagnosisState.Error(limitMsg)
                        return@launch
                    }
                }

                // Downscale image for Gemini if context+uri available (saves bandwidth, faster AI response)
                val inputImage = if (context != null && imageUri != null) {
                    withContext(Dispatchers.IO) {
                        decodeDownscaledBitmap(context, imageUri)
                    }
                } else {
                    image
                }

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
                    image(inputImage)
                    text(fullPrompt)
                }

                val response = withTimeout(20_000L) {
                    generativeModel.generateContent(inputContent)
                }

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
                    organicTreatments = geminiResult.organicTreatments,
                    chemicalTreatments = geminiResult.chemicalTreatments
                )

                _diagnosisState.value = DiagnosisState.Success(diagnosisResponse)

                // Format for Supabase storage
                val stringifiedTreatmentPlan = buildString {
                    append(geminiResult.diagnosisSummary)
                    if (geminiResult.organicTreatments.isNotEmpty()) {
                        append("\n\nOrganic Treatments:\n")
                        geminiResult.organicTreatments.forEach { step ->
                            append("- $step\n")
                        }
                    }
                    if (geminiResult.chemicalTreatments.isNotEmpty()) {
                        append("\n\nChemical Treatments:\n")
                        geminiResult.chemicalTreatments.forEach { step ->
                            append("- $step\n")
                        }
                    }
                }.trim()

                // ── SUCCESS! Universal Increment ────────────
                incrementQuota()

                // Free User IAM Trigger for 50% discount
                if (!isPremium) {
                    com.onesignal.OneSignal.InAppMessages.addTrigger("scan_done", "true")
                }

                // Upload to Supabase in background (only for confirmed plants)
                uploadToSupabase(
                    context = context,
                    image = inputImage,
                    imageUri = imageUri,
                    diseaseTitle = "Plant Analysis",
                    treatmentPlan = stringifiedTreatmentPlan
                )

            } catch (e: Exception) {
                Log.e(TAG, "Gemini analysis failed", e)
                val errorMessage = when (e) {
                    is java.net.UnknownHostException, is IOException -> "No internet connection. Please check your network."
                    is java.net.SocketTimeoutException, is kotlinx.coroutines.TimeoutCancellationException -> "The server took too long to respond. Please try again."
                    else -> "API Error: ${e.message ?: "An unknown error occurred during analysis."}"
                }
                _diagnosisState.value = DiagnosisState.Error(errorMessage)
            }
        }
    }

    private fun uploadToSupabase(
        context: Context?,
        image: Bitmap,
        imageUri: Uri?,
        diseaseTitle: String,
        treatmentPlan: String
    ) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading
            try {
                withTimeout(20_000L) {
                    val currentUser = supabaseClient.auth.currentUserOrNull()
                    val currentUserId = currentUser?.id 
                        ?: throw Exception("User is not logged in. Cannot upload scan.")

                    val imageBytes = withContext(Dispatchers.IO) {
                        if (context != null && imageUri != null) {
                            compressImageHighQuality(context, imageUri)
                        } else {
                            compressBitmapToJpeg(image)
                        }
                    }

                    val fileName = "${UUID.randomUUID()}.webp"
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
                }
            } catch (e: TimeoutCancellationException) {
                Log.e("SupabaseError", "Supabase upload timed out", e)
                _uploadState.value = UploadState.Error(
                    "Connection is too slow. Please check your internet and try again."
                )
            } catch (e: IOException) {
                Log.e("SupabaseError", "Supabase upload network error: ${e.message}", e)
                _uploadState.value = UploadState.Error(
                    "Network error during upload. Please check your internet and try again."
                )
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

    fun consumeSnackbarEvent() {
        _snackbarEvent.value = null
    }

    fun showSnackbar(message: String, type: com.pixeleye.plantdoctor.ui.components.SnackbarType) {
        _snackbarEvent.value = com.pixeleye.plantdoctor.ui.components.SnackbarState(message, type)
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
