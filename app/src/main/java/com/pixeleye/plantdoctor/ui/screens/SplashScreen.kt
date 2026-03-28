package com.pixeleye.plantdoctor.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixeleye.plantdoctor.data.UserPreferencesRepository
import com.pixeleye.plantdoctor.viewmodel.AuthState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Animated splash screen that acts as the routing hub.
 *
 * Flow:
 * 1. Logo scales up and text fades in (1000ms animation + 500ms hold).
 * 2. During the animation, reads `onboardingCompleted` from DataStore (non-reactive, read once).
 * 3. After the animation + delay, routes to the appropriate screen:
 *    - Unauthenticated / Error → LoginScreen
 *    - Authenticated + onboardingCompleted → HomeScreen
 *    - Authenticated + !onboardingCompleted → OnboardingScreen
 * 4. Navigates with `popUpTo("splash") { inclusive = true }` so the user
 *    cannot press back to return to the splash screen.
 */
@Composable
fun SplashScreen(
    authState: AuthState,
    userPreferencesRepository: UserPreferencesRepository,
    onNavigate: (route: String) -> Unit
) {
    val logoScale = remember { Animatable(0.3f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Read onboardingCompleted once (non-reactive) while the animation plays
        val onboardingCompleted = try {
            userPreferencesRepository.userPreferences.first().onboardingCompleted
        } catch (e: Exception) {
            false
        }

        // Play animation
        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000)
        )
        textAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800)
        )

        // Hold on splash
        delay(500)

        // Route based on auth state
        when (authState) {
            is AuthState.Authenticated -> {
                val destination = if (onboardingCompleted) "home" else "onboarding"
                onNavigate(destination)
            }

            is AuthState.Unauthenticated, is AuthState.Error -> {
                onNavigate("login")
            }

            is AuthState.Loading -> {
                // Auth is still resolving — stay on splash and retry after a beat
                delay(500)
                onNavigate("login")
            }
        }
    }

    // ── UI ──────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo — scale-up animation
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(logoScale.value)
                    .background(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Eco,
                    contentDescription = "Plant Doctor Logo",
                    modifier = Modifier.size(72.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App name — fade-in animation
            Text(
                text = "Plant Doctor",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.alpha(textAlpha.value)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "AI Plant Health Scanner",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.alpha(textAlpha.value)
            )
        }
    }
}
