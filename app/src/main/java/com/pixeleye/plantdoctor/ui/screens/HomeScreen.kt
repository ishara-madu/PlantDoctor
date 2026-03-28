package com.pixeleye.plantdoctor.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.pixeleye.plantdoctor.data.api.PlantScanDto
import com.pixeleye.plantdoctor.viewmodel.HomeUiState
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

// ── Date Formatting ────────────────────────────────────────────
fun formatScanDate(isoDateString: String?): String {
    if (isoDateString.isNullOrBlank()) return "Unknown date"
    return try {
        val zonedDateTime = ZonedDateTime.parse(isoDateString)
        val outputFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault())
        zonedDateTime.format(outputFormatter)
    } catch (e: Exception) {
        try {
            // Fallback: try parsing as epoch millis
            val millis = isoDateString.toLongOrNull()
            if (millis != null) {
                SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(millis))
            } else {
                isoDateString.take(10)
            }
        } catch (_: Exception) {
            isoDateString.take(10)
        }
    }
}

// ── Main Screen ────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    selectedAiLanguage: String = "English",
    onScanPlantClick: () -> Unit,
    onViewResult: (PlantScanDto) -> Unit = {},
    onDeleteScan: (PlantScanDto) -> Unit = {},
    onRetry: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onResume: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Auto-reload history every time this screen resumes (e.g. navigating back from ResultScreen)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(12.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.tertiary
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Eco,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Plant Doctor",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "AI Plant Health Scanner",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenSettings,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            val context = LocalContext.current
            ScanFAB(onClick = {
                val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                
                val safeAiLanguage = if (selectedAiLanguage.isBlank()) "English" else selectedAiLanguage

                if (!hasLocationPermission) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Location access is required to scan. Please enable it in Settings.",
                            duration = SnackbarDuration.Short
                        )
                    }
                } else {
                    onScanPlantClick()
                }
            })
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        when (val state = uiState) {
            is HomeUiState.Loading -> {
                LoadingContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            is HomeUiState.Empty -> {
                EmptyHistoryState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    onScanClick = onScanPlantClick
                )
            }

            is HomeUiState.Success -> {
                ScanHistoryContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    scans = state.scans,
                    onViewResult = onViewResult,
                    onDeleteScan = { scan ->
                        onDeleteScan(scan)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Scan deleted",
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    onRefresh = onRetry
                )
            }

            is HomeUiState.Error -> {
                ErrorContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    message = state.message,
                    onRetry = onRetry
                )
            }
        }
    }
}

// ── Loading State ──────────────────────────────────────────────
@Composable
private fun LoadingContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Loading history...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Error State ────────────────────────────────────────────────
@Composable
private fun ErrorContent(
    modifier: Modifier = Modifier,
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.WarningAmber,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Could not load history",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Retry",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ── Scan History Content ───────────────────────────────────────
@Composable
private fun ScanHistoryContent(
    modifier: Modifier = Modifier,
    scans: List<PlantScanDto>,
    onViewResult: (PlantScanDto) -> Unit,
    onDeleteScan: (PlantScanDto) -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 8.dp,
            bottom = 100.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            RecentScansHeader(count = scans.size, onRefresh = onRefresh)
        }
        items(
            items = scans,
            key = { it.id ?: it.hashCode() }
        ) { scan ->
            ScanCard(
                scan = scan,
                onClick = { onViewResult(scan) },
                onDelete = { onDeleteScan(scan) }
            )
        }
    }
}

@Composable
private fun RecentScansHeader(
    count: Int,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Recent Scans",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$count scan${if (count != 1) "s" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Scan Card ──────────────────────────────────────────────────
@Composable
private fun ScanCard(
    scan: PlantScanDto,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "card_press_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp,
            pressedElevation = 6.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true),
                    onClick = onClick
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlantThumbnail(
                imageUrl = scan.imageUrl,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scan.diseaseTitle.ifBlank { "Unknown diagnosis" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = scan.treatmentPlan
                        .lines()
                        .firstOrNull { it.isNotBlank() }
                        ?.take(80)
                        .orEmpty()
                        .ifBlank { "View details..." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = getDiseaseIcon(scan.diseaseTitle)
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatScanDate(scan.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete scan",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Subtle progress bar accent at bottom
        LinearProgressIndicator(
            progress = { 1f },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            trackColor = Color.Transparent,
        )
    }
}

@Composable
private fun PlantThumbnail(
    imageUrl: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (!imageUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = "Plant scan",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    // Strictly center and size the loader to prevent "ghost" expansion
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            strokeWidth = 2.dp
                        )
                    }
                },
                error = {
                    Icon(
                        imageVector = Icons.Default.LocalFlorist,
                        contentDescription = "No image",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            )
        } else {
            Icon(
                imageVector = Icons.Default.LocalFlorist,
                contentDescription = "No image available",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun getDiseaseIcon(diagnosis: String): ImageVector {
    val lower = diagnosis.lowercase()
    return when {
        lower.contains("healthy") -> Icons.Default.Spa
        lower.contains("nutrient") -> Icons.Default.Grass
        lower.contains("fung") || lower.contains("blight") || lower.contains("mildew") -> Icons.Default.Eco
        lower.contains("pest") || lower.contains("bug") || lower.contains("insect") -> Icons.Default.CameraAlt
        else -> Icons.Default.Eco
    }
}

// ── Empty State ────────────────────────────────────────────────
@Composable
private fun EmptyHistoryState(
    modifier: Modifier = Modifier,
    onScanClick: () -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        EmptyPlantIllustration(
            modifier = Modifier.size(220.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "No scans yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Scan your first plant to get started!\nPoint your camera at any leaf to detect diseases.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onScanClick,
            modifier = Modifier
                .height(52.dp)
                .width(200.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoCamera,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Scan a Plant",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EmptyPlantIllustration(
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.5f

        // Ground / Soil
        drawOval(
            color = secondaryColor.copy(alpha = 0.25f),
            topLeft = Offset(w * 0.2f, h * 0.82f),
            size = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.08f)
        )

        // Pot
        val potPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx - w * 0.1f, h * 0.72f)
            lineTo(cx - w * 0.13f, h * 0.9f)
            lineTo(cx + w * 0.13f, h * 0.9f)
            lineTo(cx + w * 0.1f, h * 0.72f)
            close()
        }
        drawPath(potPath, color = secondaryColor.copy(alpha = 0.6f))

        // Pot rim
        drawRoundRect(
            color = secondaryColor.copy(alpha = 0.7f),
            topLeft = Offset(cx - w * 0.12f, h * 0.70f),
            size = androidx.compose.ui.geometry.Size(w * 0.24f, h * 0.04f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
        )

        // Stem
        drawLine(
            color = primaryColor,
            start = Offset(cx, h * 0.70f),
            end = Offset(cx, h * 0.35f),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )

        val leafColor = primaryColor
        val leafColorLight = primaryContainer

        // Large left leaf
        val leafPath1 = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx, h * 0.55f)
            cubicTo(cx - w * 0.25f, h * 0.50f, cx - w * 0.3f, h * 0.35f, cx - w * 0.12f, h * 0.30f)
            cubicTo(cx - w * 0.08f, h * 0.38f, cx - w * 0.05f, h * 0.48f, cx, h * 0.55f)
            close()
        }
        drawPath(leafPath1, color = leafColor)

        // Large right leaf
        val leafPath2 = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx, h * 0.48f)
            cubicTo(cx + w * 0.22f, h * 0.43f, cx + w * 0.28f, h * 0.28f, cx + w * 0.10f, h * 0.24f)
            cubicTo(cx + w * 0.06f, h * 0.32f, cx + w * 0.03f, h * 0.42f, cx, h * 0.48f)
            close()
        }
        drawPath(leafPath2, color = leafColorLight)

        // Small top leaf
        val leafPath3 = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx, h * 0.35f)
            cubicTo(cx + w * 0.06f, h * 0.30f, cx + w * 0.12f, h * 0.18f, cx + w * 0.03f, h * 0.14f)
            cubicTo(cx - w * 0.02f, h * 0.22f, cx - w * 0.02f, h * 0.30f, cx, h * 0.35f)
            close()
        }
        drawPath(leafPath3, color = leafColor)

        // Leaf veins
        drawLine(color = primaryColor.copy(alpha = 0.3f), start = Offset(cx, h * 0.55f), end = Offset(cx - w * 0.16f, h * 0.38f), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color = primaryColor.copy(alpha = 0.3f), start = Offset(cx, h * 0.48f), end = Offset(cx + w * 0.14f, h * 0.34f), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)

        // Magnifying glass
        val mgCx = cx + w * 0.22f
        val mgCy = cy - h * 0.05f
        val mgRadius = w * 0.12f

        drawLine(color = outlineVariant, start = Offset(mgCx + mgRadius * 0.7f, mgCy + mgRadius * 0.7f), end = Offset(mgCx + mgRadius * 1.6f, mgCy + mgRadius * 1.6f), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(color = tertiaryColor, radius = mgRadius, center = Offset(mgCx, mgCy), style = Stroke(width = 4.dp.toPx()))
        drawCircle(color = tertiaryColor.copy(alpha = 0.1f), radius = mgRadius - 2.dp.toPx(), center = Offset(mgCx, mgCy))

        // Sparkle
        val sparkleSize = 3.dp.toPx()
        drawLine(color = tertiaryColor, start = Offset(mgCx + mgRadius * 1.1f, mgCy - mgRadius * 0.5f), end = Offset(mgCx + mgRadius * 1.1f, mgCy - mgRadius * 0.5f - sparkleSize * 3), strokeWidth = sparkleSize, cap = StrokeCap.Round)
        drawLine(color = tertiaryColor, start = Offset(mgCx + mgRadius * 1.1f - sparkleSize * 1.5f, mgCy - mgRadius * 0.5f - sparkleSize * 1.5f), end = Offset(mgCx + mgRadius * 1.1f + sparkleSize * 1.5f, mgCy - mgRadius * 0.5f - sparkleSize * 1.5f), strokeWidth = sparkleSize, cap = StrokeCap.Round)
    }
}

// ── Scan FAB with Pulse Animation ──────────────────────────────
@Composable
private fun ScanFAB(
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fab_pulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val ring1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1_alpha"
    )
    val ring1Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1_scale"
    )

    val ring2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, delayMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring2_alpha"
    )
    val ring2Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, delayMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring2_scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.offset(y = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .scale(ring1Scale)
                .background(
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = ring1Alpha),
                    shape = CircleShape
                )
        )

        Box(
            modifier = Modifier
                .size(76.dp)
                .scale(ring2Scale)
                .background(
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = ring2Alpha),
                    shape = CircleShape
                )
        )

        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier
                .size(68.dp)
                .scale(pulseScale),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp,
                pressedElevation = 12.dp
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(68.dp)) {
                    val strokeWidth = 2.dp.toPx()
                    val radius = (size.minDimension / 2f) - strokeWidth - 4.dp.toPx()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.25f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = strokeWidth)
                    )

                    val markLen = 6.dp.toPx()
                    val markOffset = radius - 2.dp.toPx()
                    val corners = listOf(0.0, 90.0, 180.0, 270.0)
                    for (deg in corners) {
                        val rad = Math.toRadians(deg)
                        val startX = center.x + (markOffset * cos(rad)).toFloat()
                        val startY = center.y + (markOffset * sin(rad)).toFloat()
                        val endX = center.x + ((markOffset + markLen) * cos(rad)).toFloat()
                        val endY = center.y + ((markOffset + markLen) * sin(rad)).toFloat()
                        drawLine(
                            color = Color.White.copy(alpha = 0.3f),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Scan Plant",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
