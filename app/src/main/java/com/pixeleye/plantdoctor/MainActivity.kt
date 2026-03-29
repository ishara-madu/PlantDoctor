package com.pixeleye.plantdoctor

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pixeleye.plantdoctor.data.UserPreferencesRepository
import com.pixeleye.plantdoctor.data.api.PlantScanDto
import com.pixeleye.plantdoctor.ui.screens.CameraScreen
import com.pixeleye.plantdoctor.ui.screens.HomeScreen
import com.pixeleye.plantdoctor.ui.screens.LoginScreen
import com.pixeleye.plantdoctor.ui.screens.OnboardingScreen
import com.pixeleye.plantdoctor.ui.screens.PaywallScreen
import com.pixeleye.plantdoctor.ui.screens.ResultScreen
import com.pixeleye.plantdoctor.ui.screens.SettingsScreen
import com.pixeleye.plantdoctor.ui.screens.SplashScreen
import com.pixeleye.plantdoctor.ui.theme.PlantDoctorTheme
import com.pixeleye.plantdoctor.viewmodel.AuthState
import com.pixeleye.plantdoctor.viewmodel.AuthViewModel
import com.pixeleye.plantdoctor.viewmodel.DiagnosisState
import com.pixeleye.plantdoctor.viewmodel.HomeViewModel
import com.pixeleye.plantdoctor.viewmodel.PlantDiagnosisViewModel
import com.pixeleye.plantdoctor.viewmodel.SettingsViewModel
import com.pixeleye.plantdoctor.utils.LocationHelper
import com.pixeleye.plantdoctor.utils.rememberNetworkState
import com.pixeleye.plantdoctor.ui.screens.NoInternetScreen
import com.pixeleye.plantdoctor.data.local.AppDatabase
import com.pixeleye.plantdoctor.data.api.PlantScanRepository
import com.pixeleye.plantdoctor.data.api.BillingManager
import com.pixeleye.plantdoctor.data.api.SupabaseClientProvider
import com.pixeleye.plantdoctor.data.api.UserQuotaRepository
import com.pixeleye.plantdoctor.viewmodel.PremiumViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.gms.ads.MobileAds

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MobileAds.initialize(this) {}

        // Initialize RevenueCat
        val billingManager = BillingManager()
        billingManager.initialize(this, BuildConfig.REVENUECAT_API_KEY)

        val userPreferencesRepository = UserPreferencesRepository(applicationContext)

        setContent {
            PlantDoctorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PlantDoctorApp(
                    userPreferencesRepository = userPreferencesRepository,
                    billingManager = billingManager
                )
                }
            }
        }
    }
}

@Composable
fun PlantDoctorApp(
    billingManager: BillingManager,
    userPreferencesRepository: UserPreferencesRepository,
    authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.Factory(
            LocalContext.current.applicationContext as android.app.Application,
            billingManager
        )
    )
) {
    val isOnline by rememberNetworkState()

    if (!isOnline) {
        NoInternetScreen()
        return
    }

    val authState by authViewModel.authState.collectAsStateWithLifecycle()

    when (authState) {
        is AuthState.Loading -> {
            LoadingSplash()
        }

        is AuthState.Authenticated,
        is AuthState.Unauthenticated,
        is AuthState.Error -> {
            PlantDoctorNavHost(
                authViewModel = authViewModel,
                authState = authState,
                userPreferencesRepository = userPreferencesRepository,
                billingManager = billingManager,
                onSignOut = { authViewModel.signOut() }
            )
        }
    }
}

@Composable
private fun LoadingSplash() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp
        )
    }
}

@Composable
fun PlantDoctorNavHost(
    authViewModel: AuthViewModel,
    authState: AuthState,
    userPreferencesRepository: UserPreferencesRepository,
    billingManager: BillingManager,
    onSignOut: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val database = remember { AppDatabase.getDatabase(context) }
    val supabaseClient = remember {
        SupabaseClientProvider.getClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        )
    }
    val repository = remember { PlantScanRepository(supabaseClient, database.historyDao()) }
    val userQuotaRepository = remember { UserQuotaRepository(supabaseClient) }

    val diagnosisViewModel: PlantDiagnosisViewModel = viewModel(
        factory = PlantDiagnosisViewModel.Factory(userPreferencesRepository, repository, userQuotaRepository)
    )
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(repository)
    )
    val premiumViewModel: PremiumViewModel = viewModel(
        factory = PremiumViewModel.Factory(billingManager, userQuotaRepository)
    )

    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val diagnosisState by diagnosisViewModel.diagnosisState.collectAsStateWithLifecycle()
    val isPremium by premiumViewModel.isPremium.collectAsStateWithLifecycle()
    val prefs by userPreferencesRepository.userPreferences.collectAsStateWithLifecycle(initialValue = com.pixeleye.plantdoctor.data.UserPreferences())

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        composable("splash") {
            SplashScreen(
                authState = authState,
                userPreferencesRepository = userPreferencesRepository,
                onNavigate = { destination ->
                    navController.navigate(destination) {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }

        composable("login") {
            var isSaving by remember { mutableStateOf(false) }

            LoginScreen(
                isLoading = isSaving,
                errorMessage = when (authState) {
                    is AuthState.Error -> (authState as AuthState.Error).message
                    else -> null
                },
                onGoogleSignIn = {
                    if (authState is AuthState.Error) {
                        authViewModel.clearError()
                    }
                    authViewModel.signInWithGoogle(onPostAuth = {
                        premiumViewModel.syncPremiumStatus()
                    })
                }
            )

            // After successful sign-in, navigate (state update is now delayed until sync is complete)
            LaunchedEffect(authState) {
                if (authState is AuthState.Authenticated) {
                    navController.navigate("splash") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            }
        }

        composable("onboarding") {
            var isSaving by remember { mutableStateOf(false) }

            OnboardingScreen(
                onSaveAndContinue = { country, language, aiLanguage ->
                    isSaving = true
                    scope.launch {
                        userPreferencesRepository.saveUserPreferences(
                            country = country,
                            language = language,
                            selectedAiLanguage = aiLanguage,
                            onboardingCompleted = true
                        )
                        navController.navigate("home") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                },
                isSaving = isSaving
            )
        }

        composable("home") {
            val snackbarMessage by homeViewModel.snackbarEvent.collectAsStateWithLifecycle()

            HomeScreen(
                uiState = homeUiState,
                selectedAiLanguage = prefs.selectedAiLanguage,
                isPremium = isPremium,
                snackbarMessage = snackbarMessage,
                onSnackbarShown = { homeViewModel.consumeSnackbarEvent() },
                onScanPlantClick = {
                    navController.navigate("camera")
                },
                onViewResult = { scan: PlantScanDto ->
                    val encodedUrl = Uri.encode(scan.imageUrl)
                    val encodedTitle = Uri.encode(scan.diseaseTitle)
                    val encodedPlan = Uri.encode(scan.treatmentPlan)
                    navController.navigate(
                        "result?imageUrl=$encodedUrl&title=$encodedTitle&plan=$encodedPlan"
                    )
                },
                onDeleteScan = { scan ->
                    homeViewModel.deleteScan(scan)
                },
                onRetry = {
                    homeViewModel.fetchHistory()
                },
                onOpenSettings = {
                    navController.navigate("settings")
                },
                onOpenPaywall = {
                    navController.navigate("paywall")
                },
                onResume = {
                    homeViewModel.fetchHistory()
                }
            )
        }

        composable("camera") {
            CameraScreen(
                diagnosisViewModel = diagnosisViewModel,
                isPremium = isPremium,
                onImageCaptured = { uri ->
                    Log.d("PlantDoctor", "Image captured: $uri")
                    diagnosisViewModel.resetState()
                    val encodedUri = Uri.encode(uri.toString())
                    navController.navigate("result?imageUri=$encodedUri&showAd=true") {
                        popUpTo("camera") { inclusive = true }
                    }
                },
                onError = { errorMessage ->
                    Log.e("PlantDoctor", "Camera error: $errorMessage")
                    try {
                        navController.popBackStack()
                    } catch (e: Exception) {
                        Log.e("PlantDoctor", "Navigation error on camera error: ${e.message}")
                    }
                },
                onCancel = {
                    try {
                        navController.popBackStack()
                    } catch (e: Exception) {
                        Log.e("PlantDoctor", "Navigation error on cancel: ${e.message}")
                    }
                },
                onOpenPaywall = {
                    navController.navigate("paywall")
                }
            )
        }

        // Fresh capture result (image from camera → Gemini analysis)
        composable(
            route = "result?imageUri={imageUri}&showAd={showAd}",
            arguments = listOf(
                navArgument("imageUri") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("showAd") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val imageUriString = backStackEntry.arguments?.getString("imageUri")
            val showAd = backStackEntry.arguments?.getBoolean("showAd") ?: false
            val imageUri = imageUriString?.let {
                try { Uri.parse(it) } catch (e: Exception) {
                    Log.e("PlantDoctor", "Failed to parse URI: $it", e)
                    null
                }
            }

            LaunchedEffect(imageUriString) {
                if (imageUri != null && diagnosisState is DiagnosisState.Idle) {
                    try {
                        Log.d("PlantDoctor", "Starting image decode for URI: $imageUri")
                        val bitmap = withContext(Dispatchers.IO) {
                            val inputStream = context.contentResolver.openInputStream(imageUri)
                            val decoded = BitmapFactory.decodeStream(inputStream)
                            inputStream?.close()
                            decoded
                        }
                        if (bitmap != null) {
                            Log.d("PlantDoctor", "Bitmap decoded: ${bitmap.width}x${bitmap.height}")
                            val locationStr = LocationHelper.getRobustLocationString(context)
                            diagnosisViewModel.analyzePlant(bitmap, imageUri = imageUri, locationStr = locationStr, context = context)
                        } else {
                            Log.e("PlantDoctor", "Failed to decode bitmap from URI: $imageUri")
                        }
                    } catch (e: Exception) {
                        Log.e("PlantDoctor", "Error decoding image: ${e.message}", e)
                    }
                }
            }

            val isLoading = diagnosisState is DiagnosisState.Loading
            val diagnosisData = when (val state = diagnosisState) {
                is DiagnosisState.Success -> state.result
                is DiagnosisState.Error -> com.pixeleye.plantdoctor.data.api.DiagnosisResponse("Analysis failed: ${state.message}", emptyList())
                else -> null
            }

            ResultScreen(
                imageUri = imageUri,
                diagnosisTitle = "Plant Analysis",
                diagnosisData = diagnosisData,
                isLoading = isLoading,
                showAd = showAd,
                isPremium = isPremium,
                onBack = {
                    diagnosisViewModel.resetState()
                    homeViewModel.fetchHistory()
                    try {
                        navController.popBackStack()
                    } catch (e: Exception) {
                        Log.e("PlantDoctor", "Navigation error on back: ${e.message}")
                    }
                },
                onNewScan = {
                    diagnosisViewModel.resetState()
                    try {
                        navController.navigate("camera") {
                            popUpTo("home")
                        }
                    } catch (e: Exception) {
                        Log.e("PlantDoctor", "Navigation error on new scan: ${e.message}")
                    }
                },
                onOpenPaywall = {
                    navController.navigate("paywall")
                }
            )
        }

        // History item result (pre-saved data from Supabase)
        composable(
            route = "result?imageUrl={imageUrl}&title={title}&plan={plan}",
            arguments = listOf(
                navArgument("imageUrl") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("title") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("plan") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { backStackEntry ->
            val imageUrl = backStackEntry.arguments?.getString("imageUrl")
            val title = backStackEntry.arguments?.getString("title") ?: "Plant Analysis"
            val plan = backStackEntry.arguments?.getString("plan") ?: ""

            // Reconstruct DiagnosisResponse from history plain text
            // Support new format (Organic/Chemical sections) and legacy format (Action Plan section)
            val diagnosisData = if (plan.contains("Organic Treatments:") || plan.contains("Chemical Treatments:")) {
                val summaryEnd = listOfNotNull(
                    plan.indexOf("\n\nOrganic Treatments:\n").takeIf { it >= 0 },
                    plan.indexOf("\n\nChemical Treatments:\n").takeIf { it >= 0 }
                ).minOrNull() ?: plan.length
                val summary = plan.substring(0, summaryEnd).trim()

                val organicBlock = """Organic Treatments:\n(.*?)(?=\n\nChemical Treatments:|$)""".toRegex(RegexOption.DOT_MATCHES_ALL)
                    .find(plan)?.groupValues?.getOrNull(1) ?: ""
                val organicList = organicBlock.lines().map { it.removePrefix("- ").trim() }.filter { it.isNotBlank() }

                val chemicalBlock = """Chemical Treatments:\n(.*)""".toRegex(RegexOption.DOT_MATCHES_ALL)
                    .find(plan)?.groupValues?.getOrNull(1) ?: ""
                val chemicalList = chemicalBlock.lines().map { it.removePrefix("- ").trim() }.filter { it.isNotBlank() }

                com.pixeleye.plantdoctor.data.api.DiagnosisResponse(summary = summary, organicTreatments = organicList, chemicalTreatments = chemicalList)
            } else {
                val legacyPlanParts = plan.split("\n\nAction Plan:\n")
                val legacySummary = legacyPlanParts.getOrNull(0) ?: plan
                val legacyList = legacyPlanParts.getOrNull(1)?.lines()?.map { it.removePrefix("- ").trim() }?.filter { it.isNotBlank() } ?: emptyList()
                com.pixeleye.plantdoctor.data.api.DiagnosisResponse(summary = legacySummary, organicTreatments = legacyList)
            }

            val imageUri = imageUrl?.let {
                try { Uri.parse(it) } catch (_: Exception) { null }
            }

            ResultScreen(
                imageUri = imageUri,
                diagnosisTitle = title,
                diagnosisData = diagnosisData,
                isLoading = false,
                isPremium = isPremium,
                onBack = {
                    try {
                        navController.popBackStack()
                    } catch (e: Exception) {
                        Log.e("PlantDoctor", "Navigation error on back: ${e.message}")
                    }
                },
                onNewScan = {
                    try {
                        navController.navigate("camera") {
                            popUpTo("home")
                        }
                    } catch (e: Exception) {
                        Log.e("PlantDoctor", "Navigation error on new scan: ${e.message}")
                    }
                },
                onOpenPaywall = {
                    navController.navigate("paywall")
                }
            )
        }

        composable("settings") {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(userPreferencesRepository)
            )
            val currentPrefs by settingsViewModel.currentPrefs.collectAsStateWithLifecycle()
            val isSaving by settingsViewModel.isSaving.collectAsStateWithLifecycle()

            SettingsScreen(
                currentPrefs = currentPrefs,
                isSaving = isSaving,
                onSave = { country, language, aiLanguage ->
                    settingsViewModel.savePreferences(country, language, aiLanguage)
                },
                onLogout = {
                    premiumViewModel.setPremium(false)
                    scope.launch {
                        userPreferencesRepository.clearPreferences()
                        database.historyDao().clearAll()
                        authViewModel.signOut().await()
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                onBack = {
                    try {
                        navController.popBackStack()
                    } catch (e: Exception) {
                        Log.e("PlantDoctor", "Navigation error on back: ${e.message}")
                    }
                }
            )
        }

        composable("paywall") {
            val isProcessing by premiumViewModel.isLoading.collectAsStateWithLifecycle()

            PaywallScreen(
                isProcessing = isProcessing,
                onClose = {
                    try {
                        navController.popBackStack()
                    } catch (e: Exception) {
                        Log.e("PlantDoctor", "Navigation error on paywall close: ${e.message}")
                    }
                },
                onSubscribe = { plan ->
                    premiumViewModel.startPurchase(
                        activity = context as ComponentActivity,
                        billingManager = billingManager,
                        planId = plan,
                        onSuccess = {
                            premiumViewModel.upgradeToPremium()
                            android.widget.Toast.makeText(
                                context,
                                "Welcome to PRO!",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            try {
                                navController.popBackStack()
                            } catch (e: Exception) {
                                Log.e("PlantDoctor", "Navigation error after purchase: ${e.message}")
                            }
                        },
                        onError = { message ->
                            Log.e("PlantDoctor", "Purchase error: $message")
                            android.widget.Toast.makeText(
                                context,
                                message,
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                },
                onRestorePurchases = {
                    try {
                        premiumViewModel.restorePurchases(
                            billingManager = billingManager,
                            onSuccess = { isPro ->
                                if (isPro) {
                                    premiumViewModel.upgradeToPremium()
                                    android.widget.Toast.makeText(
                                        context,
                                        "Purchases restored! Welcome back to PRO!",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                    try {
                                        navController.popBackStack()
                                    } catch (e: Exception) {
                                        Log.e("PlantDoctor", "Navigation error after restore: ${e.message}")
                                    }
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        "No active PRO subscription found.",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            onError = { message ->
                                Log.e("PlantDoctor", "Restore error: $message")
                                android.widget.Toast.makeText(
                                    context,
                                    message,
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    } catch (e: Exception) {
                        Log.e("PlantDoctor", "Restore exception: ${e.message}", e)
                        android.widget.Toast.makeText(
                            context,
                            "Failed to restore purchases. Please try again.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                },
                onTermsClick = {
                    Log.d("PlantDoctor", "Terms tapped")
                    // TODO: open terms URL
                }
            )
        }
    }
}
