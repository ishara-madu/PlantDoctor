package com.pixeleye.plantdoctor.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revenuecat.purchases.Package
import kotlin.math.roundToInt

// ── Paywall Screen ────────────────────────────────────────────
@Composable
fun PaywallScreen(
    monthlyPrice: String = "",
    yearlyPrice: String = "",
    monthlyPackage: Package? = null,
    yearlyPackage: Package? = null,
    isProcessing: Boolean = false,
    onTriggerSnackbar: (String, com.pixeleye.plantdoctor.ui.components.SnackbarType) -> Unit = { _, _ -> },
    onClose: () -> Unit = {},
    onSubscribe: (plan: String) -> Unit = {},
    onRestorePurchases: () -> Unit = {},
    onTermsClick: () -> Unit = {}
) {
    var selectedPlan by remember { mutableStateOf("yearly") }

    // Helper to calculate monthly equivalent if yearly price is available
    val yearlyNum = yearlyPrice.filter { it.isDigit() || it == '.' || it == ',' }.toDoubleOrNull()
    val monthlyEquivalent = if (yearlyNum != null) {
        val symbol = yearlyPrice.takeWhile { !it.isDigit() }
        val perMonth = (yearlyNum / 12)
        val formatted = "%.2f".format(perMonth)
        "Just $symbol$formatted / month"
    } else {
        null
    }

    val discountPercentage = remember(monthlyPackage, yearlyPackage) {
        calculateSavingsPercentage(monthlyPackage, yearlyPackage)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // ... (Header and Features logic remains the same)
            // ── Header Section ──────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1B5E20),
                                Color(0xFF2E7D32),
                                Color(0xFF43A047),
                                Color(0xFF66BB6A).copy(alpha = 0.6f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            ) {
                // Decorative floating leaves
                FloatingLeaves(
                    modifier = Modifier
                        .matchParentSize()
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Close button — top right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = Color.White.copy(alpha = 0.9f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Crown / PRO badge illustration
                    ProBadgeIllustration(
                        modifier = Modifier.size(80.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Unlock Plant Doctor PRO",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Get unlimited scans, expert AI treatments,\nand an ad-free experience.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            // ── Features List ───────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                ProFeatureRow(
                    icon = Icons.Default.AllInclusive,
                    title = "Unlimited Plant Scans",
                    subtitle = "No daily limits — scan as many plants as you need"
                )

                Spacer(modifier = Modifier.height(14.dp))

                ProFeatureRow(
                    icon = Icons.Default.Science,
                    title = "Expert Chemical & Organic Treatments",
                    subtitle = "AI-powered solutions with local availability"
                )

                Spacer(modifier = Modifier.height(14.dp))

                ProFeatureRow(
                    icon = Icons.Default.CloudQueue,
                    title = "Unlimited Cloud History",
                    subtitle = "Every scan saved — never lose a diagnosis"
                )

                Spacer(modifier = Modifier.height(14.dp))

                ProFeatureRow(
                    icon = Icons.Default.Block,
                    title = "Zero Advertisements",
                    subtitle = "A clean, focused experience every time"
                )

                Spacer(modifier = Modifier.height(28.dp))
            }

            // ── Subscription Cards ──────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                // Yearly — Best Value
                SubscriptionCard(
                    title = "Yearly",
                    price = yearlyPrice.ifBlank { "..." },
                    period = " / year",
                    monthlyEquivalent = monthlyEquivalent,
                    isSelected = selectedPlan == "yearly",
                    isBestValue = true,
                    discountPercentage = discountPercentage,
                    onClick = { selectedPlan = "yearly" }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Monthly
                SubscriptionCard(
                    title = "Monthly",
                    price = monthlyPrice.ifBlank { "..." },
                    period = " / month",
                    monthlyEquivalent = null,
                    isSelected = selectedPlan == "monthly",
                    isBestValue = false,
                    discountPercentage = null,
                    onClick = { selectedPlan = "monthly" }
                )

                Spacer(modifier = Modifier.height(28.dp))
            }

            // ── CTA Button ──────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = { onSubscribe(selectedPlan) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp
                    )
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text(
                        text = "Subscribe Now",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Restore & Terms row
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onRestorePurchases, enabled = !isProcessing) {
                        Text(
                            text = "Restore Purchases",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "  \u2022  ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )

                    TextButton(onClick = onTermsClick) {
                        Text(
                            text = "Terms & Privacy Policy",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

// ── Subscription Card ─────────────────────────────────────────
@Composable
private fun SubscriptionCard(
    title: String,
    price: String,
    period: String,
    monthlyEquivalent: String?,
    isSelected: Boolean,
    isBestValue: Boolean,
    discountPercentage: Int? = null,
    onClick: () -> Unit
) {
    val borderColor = when {
        isSelected && isBestValue -> MaterialTheme.colorScheme.primary
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    val borderWidth = if (isSelected && isBestValue) 2.5.dp else 1.2.dp

    val containerColor = when {
        isSelected && isBestValue -> MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 3.dp else 0.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Selection indicator
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = CircleShape
                        )
                        .border(
                            width = 2.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (monthlyEquivalent != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = monthlyEquivalent,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Price
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = price,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = period,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }

            // Dynamic savings badge — only on yearly best value card
            if (isBestValue) {
                SavingsBadge(
                    discountPercentage = discountPercentage,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 12.dp, y = (-10).dp)
                )
            }
        }
    }
}

// ── Pro Feature Row ───────────────────────────────────────────
@Composable
private fun ProFeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

// ── PRO Badge Illustration ────────────────────────────────────
@Composable
private fun ProBadgeIllustration(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "badge_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) * 0.42f

        // Outer glow ring
        drawCircle(
            color = Color.White.copy(alpha = glowAlpha * 0.3f),
            radius = r + 6.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(width = 3.dp.toPx())
        )

        // Main circle
        drawCircle(
            color = Color.White.copy(alpha = 0.2f),
            radius = r,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.8f),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 2.dp.toPx())
        )

        // Crown shape
        val crownW = r * 1.2f
        val crownH = r * 0.7f
        val crownTop = cy - crownH * 0.15f
        val crownBottom = cy + crownH * 0.45f

        val crownPath = Path().apply {
            // Left base
            moveTo(cx - crownW / 2, crownBottom)
            // Left up to point
            lineTo(cx - crownW * 0.4f, crownTop)
            // Left inner valley
            lineTo(cx - crownW * 0.15f, crownTop + crownH * 0.35f)
            // Center peak
            lineTo(cx, crownTop - crownH * 0.05f)
            // Right inner valley
            lineTo(cx + crownW * 0.15f, crownTop + crownH * 0.35f)
            // Right point
            lineTo(cx + crownW * 0.4f, crownTop)
            // Right base
            lineTo(cx + crownW / 2, crownBottom)
            // Close bottom
            close()
        }

        drawPath(
            path = crownPath,
            color = Color.White,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // Crown fill
        drawPath(
            path = crownPath,
            color = Color.White.copy(alpha = 0.15f)
        )

        // Crown base bar
        drawRoundRect(
            color = Color.White.copy(alpha = 0.6f),
            topLeft = Offset(cx - crownW * 0.55f, crownBottom - 3.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(crownW * 1.1f, 6.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
        )

        // Sparkle dots
        val sparklePositions = listOf(
            Offset(cx - r * 0.9f, cy - r * 0.5f),
            Offset(cx + r * 0.85f, cy - r * 0.3f),
            Offset(cx + r * 0.2f, cy - r * 1.0f)
        )
        sparklePositions.forEach { pos ->
            drawCircle(
                color = Color.White.copy(alpha = 0.6f),
                radius = 2.dp.toPx(),
                center = pos
            )
        }
    }
}

// ── Floating Leaves Background ────────────────────────────────
@Composable
private fun FloatingLeaves(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "leaves")
    val leafFloat1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "leaf1"
    )
    val leafFloat2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "leaf2"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Leaf 1 — top left
        val l1x = w * 0.12f
        val l1y = h * 0.15f + leafFloat1
        drawLeaf(
            color = Color.White.copy(alpha = 0.08f),
            cx = l1x,
            cy = l1y,
            width = 30.dp.toPx(),
            height = 18.dp.toPx(),
            rotation = -25f
        )

        // Leaf 2 — top right
        val l2x = w * 0.85f
        val l2y = h * 0.22f + leafFloat2
        drawLeaf(
            color = Color.White.copy(alpha = 0.06f),
            cx = l2x,
            cy = l2y,
            width = 24.dp.toPx(),
            height = 14.dp.toPx(),
            rotation = 15f
        )

        // Leaf 3 — center left
        val l3x = w * 0.05f
        val l3y = h * 0.55f + leafFloat1
        drawLeaf(
            color = Color.White.copy(alpha = 0.05f),
            cx = l3x,
            cy = l3y,
            width = 20.dp.toPx(),
            height = 12.dp.toPx(),
            rotation = -40f
        )

        // Leaf 4 — bottom right
        val l4x = w * 0.9f
        val l4y = h * 0.7f + leafFloat2
        drawLeaf(
            color = Color.White.copy(alpha = 0.07f),
            cx = l4x,
            cy = l4y,
            width = 26.dp.toPx(),
            height = 16.dp.toPx(),
            rotation = 30f
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLeaf(
    color: Color,
    cx: Float,
    cy: Float,
    width: Float,
    height: Float,
    rotation: Float = 0f
) {
    val rad = Math.toRadians(rotation.toDouble())
    val cosR = Math.cos(rad).toFloat()
    val sinR = Math.sin(rad).toFloat()

    fun rotate(x: Float, y: Float): Offset {
        val rx = x * cosR - y * sinR
        val ry = x * sinR + y * cosR
        return Offset(cx + rx, cy + ry)
    }

    val path = Path().apply {
        val start = rotate(0f, 0f)
        moveTo(start.x, start.y)

        // Right curve
        val cp1 = rotate(width * 0.5f, -height * 0.6f)
        val end1 = rotate(width * 0.3f, -height)
        cubicTo(cp1.x, cp1.y, cp1.x, cp1.y, end1.x, end1.y)

        // Top to left curve
        val cp2 = rotate(-width * 0.1f, -height * 0.8f)
        val end2 = rotate(-width * 0.3f, -height * 0.5f)
        cubicTo(cp2.x, cp2.y, cp2.x, cp2.y, end2.x, end2.y)

        // Back to start
        val cp3 = rotate(-width * 0.4f, -height * 0.1f)
        val end3 = rotate(0f, 0f)
        cubicTo(cp3.x, cp3.y, cp3.x, cp3.y, end3.x, end3.y)

        close()
    }

    drawPath(path = path, color = color)
}

// ── Helpers ───────────────────────────────────────────────────

fun calculateSavingsPercentage(monthlyPackage: Package?, yearlyPackage: Package?): Int? {
    if (monthlyPackage == null || yearlyPackage == null) return null

    val monthlyPriceMicros = monthlyPackage.product.price.amountMicros
    val yearlyPriceMicros = yearlyPackage.product.price.amountMicros

    // Prevent division by zero or negative prices
    if (monthlyPriceMicros <= 0L || yearlyPriceMicros <= 0L) return null

    // Calculate how much 12 months of the monthly package would cost
    val twelveMonthCost = monthlyPriceMicros * 12

    // If the yearly price is somehow more expensive, there's no savings
    if (yearlyPriceMicros >= twelveMonthCost) return 0

    // Calculate percentage savings
    val savings = twelveMonthCost - yearlyPriceMicros
    val percentage = (savings.toDouble() / twelveMonthCost.toDouble()) * 100

    return percentage.roundToInt()
}

@Composable
fun SavingsBadge(
    discountPercentage: Int?,
    modifier: Modifier = Modifier
) {
    if (discountPercentage != null && discountPercentage > 0) {
        Box(
            modifier = modifier
                .background(
                    color = MaterialTheme.colorScheme.tertiary,
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 8.dp)
                )
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "SAVE $discountPercentage%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1A1C18),
                letterSpacing = 0.8.sp,
                fontSize = 10.sp
            )
        }
    }
}
