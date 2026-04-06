package com.pixeleye.plantdoctor.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.view.MotionEvent
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pixeleye.plantdoctor.utils.CameraUtils
import com.pixeleye.plantdoctor.utils.LocationHelper
import com.pixeleye.plantdoctor.viewmodel.PlantDiagnosisViewModel
import android.graphics.BitmapFactory
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.pixeleye.plantdoctor.viewmodel.DiagnosisState
import java.util.concurrent.TimeUnit
import com.google.android.gms.ads.rewarded.RewardedAd
import com.pixeleye.plantdoctor.utils.loadRewardedAd
import com.pixeleye.plantdoctor.utils.showRewardedAd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

// ── Public entry point ──────────────────────────────────────────
@Composable
fun CameraScreen(
    diagnosisViewModel: PlantDiagnosisViewModel,
    isPremium: Boolean = false,
    onImageCaptured: (Uri) -> Unit,
    onError: (String) -> Unit,
    onCancel: () -> Unit,
    onOpenPaywall: () -> Unit = {}
) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        CameraContent(
            diagnosisViewModel = diagnosisViewModel,
            isPremium = isPremium,
            onImageCaptured = onImageCaptured,
            onError = onError,
            onCancel = onCancel,
            onOpenPaywall = onOpenPaywall
        )
    } else {
        PermissionDeniedContent(
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onCancel = onCancel
        )
    }
}

// ── Main camera UI ──────────────────────────────────────────────
@Composable
private fun CameraContent(
    diagnosisViewModel: PlantDiagnosisViewModel,
    isPremium: Boolean,
    onImageCaptured: (Uri) -> Unit,
    onError: (String) -> Unit,
    onCancel: () -> Unit,
    onOpenPaywall: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember { PreviewView(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isCapturing by remember { mutableStateOf(false) }

    // ── Coordination ──────────────────────────────────────────
    val scope = rememberCoroutineScope()
    var capturedUri by remember { mutableStateOf<Uri?>(null) }
    var isConfirming by remember { mutableStateOf(false) }

    // ── Quota & Rewarded Ad state ─────────────────────────────
    var showLimitDialog by remember { mutableStateOf(false) }
    var showHardLimitDialog by remember { mutableStateOf(false) }
    var currentQuotaCount by remember { mutableStateOf(0) }
    var rewardedAd by remember { mutableStateOf<RewardedAd?>(null) }
    val activity = context as? android.app.Activity

    // (Navigation logic is now handled immediately in onConfirm)

    // ── Focus ring state ────────────────────────────────────────
    // null = hidden, non-null Offset = show ring at these native pixel coords
    var focusRingOffset by remember { mutableStateOf<Offset?>(null) }
    val focusRingAlpha by animateFloatAsState(
        targetValue = if (focusRingOffset != null) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "focus_ring_alpha"
    )

    // Auto-hide after 900ms whenever a new tap position is set
    LaunchedEffect(focusRingOffset) {
        if (focusRingOffset != null) {
            kotlinx.coroutines.delay(900L)
            focusRingOffset = null
        }
    }

    // Load rewarded ad when screen opens
    LaunchedEffect(Unit) {
        loadRewardedAd(context) { ad ->
            rewardedAd = ad
        }
    }

    // ── Confirmation state ──────────────────────────────────────
    // (capturedUri moved up to coordination block)

    // ── Gallery picker ──────────────────────────────────────────
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            capturedUri = uri
        }
    }

    // ── CameraX setup ───────────────────────────────────────────
    DisposableEffect(Unit) {
        CameraUtils.createCameraProvider(
            context = context,
            onCameraProviderReady = { cameraProvider ->
                val preview = Preview.Builder().build()
                val newImageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                preview.setSurfaceProvider(previewView.surfaceProvider)

                try {
                    cameraProvider.unbindAll()
                    val boundCamera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        newImageCapture
                    )
                    imageCapture = newImageCapture
                    camera = boundCamera
                } catch (e: Exception) {
                    onError("Camera binding failed: ${e.message}")
                }
            },
            onError = { exception ->
                onError("Failed to initialize camera: ${exception.message}")
            }
        )

        onDispose {
            camera = null
            imageCapture = null
        }
    }

    // ── UI ──────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {
        if (capturedUri != null) {
            // ═══════════════════════════════════════════════════════
            // IMAGE CONFIRMATION STATE
            // ═══════════════════════════════════════════════════════
            ConfirmationContent(
                uri = capturedUri!!,
                onRetake = { capturedUri = null },
                onConfirm = {
                    if (!isConfirming) {
                        isConfirming = true
                        scope.launch {
                            try {
                                val currentQuota = diagnosisViewModel.checkQuota()
                                currentQuotaCount = currentQuota.dailyCount
                                val maxLimit = if (isPremium) 50 else 3

                                when {
                                    currentQuotaCount < maxLimit -> {
                                        // Within limit — proceed with capture/increment will happen in VM
                                        onImageCaptured(capturedUri!!)
                                    }
                                    !isPremium && currentQuotaCount < 6 -> {
                                        // Tier 2: Free users can unlock 3 more via ads (Fair Use doesn't apply yet)
                                        // Wait... the user said 3 is the limit for Free. 
                                        // If they want to keep the ad unlock, we can keep it up to 6.
                                        showLimitDialog = true
                                    }
                                    else -> {
                                        // Hard limit reached (3/6 for Free, 50 for Pro)
                                        showHardLimitDialog = true
                                    }
                                }
                            } catch (e: Exception) {
                                // If quota check fails, allow the scan to proceed
                                onImageCaptured(capturedUri!!)
                            } finally {
                                isConfirming = false
                            }
                        }
                    }
                },
                isConfirmLoading = isConfirming
            )
        } else {
            // ═══════════════════════════════════════════════════════
            // LIVE CAMERA STATE
            // ═══════════════════════════════════════════════════════

            // Camera preview with tap-to-focus via native touch listener.
            // We attach the listener inside `update` so `camera` is always captured
            // from the latest recomposition closure, and PreviewView.meteringPointFactory
            // guarantees correct coordinate mapping to the camera surface.
            AndroidView(
                factory = { ctx ->
                    previewView.apply {
                        // Accessibility: let the system know performClick() will be called
                        isClickable = true
                    }
                },
                update = { view ->
                    val currentCamera = camera
                    view.setOnTouchListener { v, event ->
                        if (event.action == MotionEvent.ACTION_UP && currentCamera != null) {
                            val factory = (v as PreviewView).meteringPointFactory
                            val point = factory.createPoint(event.x, event.y)
                            val action = FocusMeteringAction.Builder(
                                point,
                                FocusMeteringAction.FLAG_AF
                            )
                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                .build()
                            currentCamera.cameraControl.startFocusAndMetering(action)
                            // Update Compose state to drive the focus ring overlay
                            focusRingOffset = Offset(event.x, event.y)
                            v.performClick()
                        }
                        true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Focus ring overlay — clean, anti-aliased, layered glow ring
            // Guard: skip composition entirely when fully faded out
            if (focusRingAlpha > 0f) {
                val focusPx = focusRingOffset
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (focusPx != null) {
                        val ringRadius = 36.dp.toPx()

                        // Layer 1: soft glow — wider, semi-transparent ring underneath
                        drawCircle(
                            color = Color.White,
                            radius = ringRadius,
                            center = focusPx,
                            style = Stroke(width = 8.dp.toPx()),
                            alpha = focusRingAlpha * 0.18f
                        )

                        // Layer 2: crisp 1.5dp main ring on top — fully anti-aliased
                        drawCircle(
                            color = Color.White,
                            radius = ringRadius,
                            center = focusPx,
                            style = Stroke(width = 1.5.dp.toPx()),
                            alpha = focusRingAlpha
                        )
                    }
                }
            }

            // Close button — safe area padding
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp)
                    .statusBarsPadding()
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }

            // Bottom bar: gallery + shutter
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery button
                IconButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.15f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Gallery",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Shutter button
                CaptureButton(
                    isCapturing = isCapturing,
                    onClick = {
                        val currentImageCapture = imageCapture
                        if (currentImageCapture != null && !isCapturing) {
                            isCapturing = true
                            CameraUtils.captureImage(
                                imageCapture = currentImageCapture,
                                context = context,
                                executor = CameraUtils.getCameraExecutor(context),
                                onImageCaptured = { uri ->
                                    isCapturing = false
                                    capturedUri = uri
                                },
                                onError = { exception ->
                                    isCapturing = false
                                    onError("Failed to capture image: ${exception.message}")
                                }
                            )
                        }
                    }
                )

                // Invisible spacer to keep shutter centered
                Spacer(modifier = Modifier.size(52.dp))
            }
        }

        // ── Rewarded Ad Dialog (Tier 2) ───────────────────────
        if (showLimitDialog) {
            val remaining = (6 - currentQuotaCount).coerceAtLeast(0)
            AlertDialog(
                onDismissRequest = { showLimitDialog = false },
                title = {
                    Text(
                        text = "Free Scans Exhausted",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                text = {
                    Text(
                        text = "Watch a short video ad to unlock an extra scan! ($remaining remaining today)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (activity != null) {
                            showLimitDialog = false
                            if (rewardedAd != null) {
                                var didEarnReward = false
                                showRewardedAd(
                                    activity = activity,
                                    ad = rewardedAd,
                                    onRewardEarned = {
                                        didEarnReward = true
                                    },
                                    onAdDismissed = {
                                        rewardedAd = null
                                        if (didEarnReward) {
                                            scope.launch {
                                                onImageCaptured(capturedUri!!)
                                            }
                                        }
                                        loadRewardedAd(context) { newAd ->
                                            rewardedAd = newAd
                                        }
                                    }
                                )
                            } else {
                                diagnosisViewModel.showSnackbar(
                                    "Ad is still loading. Please check your connection and try again.",
                                    com.pixeleye.plantdoctor.ui.components.SnackbarType.WARNING
                                )
                                loadRewardedAd(context) { newAd ->
                                    rewardedAd = newAd
                                }
                            }
                        }
                    }) {
                        Text("Watch Ad")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLimitDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // ── Hard Limit Dialog (Tier 3) ───────────────────────
        if (showHardLimitDialog) {
            AlertDialog(
                onDismissRequest = { showHardLimitDialog = false },
                title = {
                    Text(
                        text = "Daily Limit Reached",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                text = {
                    Text(
                        text = "You have used all your free and ad-supported scans for today. Upgrade to PRO for unlimited access!",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showHardLimitDialog = false
                        onOpenPaywall()
                    }) {
                        Text("Upgrade to PRO")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showHardLimitDialog = false }) {
                        Text("Maybe Later")
                    }
                }
            )
        }
    }
}

// ── Image confirmation overlay ──────────────────────────────────
@Composable
private fun ConfirmationContent(
    uri: Uri,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
    isConfirmLoading: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Full-screen image
        AsyncImage(
            model = uri,
            contentDescription = "Selected image",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
        )

        // Close button on confirmation view
        IconButton(
            onClick = onRetake,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp)
                .statusBarsPadding()
                .background(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White
            )
        }

        // Bottom action bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Retake
            ActionButton(
                icon = Icons.Default.Refresh,
                label = "Retake",
                containerColor = Color.White.copy(alpha = 0.2f),
                contentColor = Color.White,
                onClick = onRetake
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Confirm
            ActionButton(
                icon = Icons.Default.Check,
                label = "Confirm",
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                onClick = onConfirm,
                isLoading = isConfirmLoading
            )
        }
    }
}

// ── Confirmation action button ──────────────────────────────────
@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    isLoading: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = if (isLoading) ({}) else onClick,
            modifier = Modifier
                .size(60.dp)
                .background(color = containerColor, shape = CircleShape)
                .border(width = 2.dp, color = contentColor.copy(alpha = 0.3f), shape = CircleShape)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = contentColor,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
        )
    }
}

// ── Shutter button ──────────────────────────────────────────────
@Composable
private fun CaptureButton(
    isCapturing: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .background(Color.White, CircleShape)
            .padding(4.dp)
            .background(Color.White.copy(alpha = 0.9f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (isCapturing) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .size(68.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .background(Color.White, CircleShape)
                )
            }
        }
    }
}

// ── Permission denied state ─────────────────────────────────────
@Composable
private fun PermissionDeniedContent(
    onRequestPermission: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Camera Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "To capture plant images for disease detection, please grant camera permission.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Button(
            onClick = onRequestPermission,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text("Grant Camera Permission")
        }

        Button(onClick = onCancel) {
            Text("Cancel")
        }
    }
}
