package com.orbin.app

import android.Manifest
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.core.designsystem.theme.ThemeMode
import com.orbin.core.model.AppThemeMode
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. Sets up the splash screen, edge-to-edge layout, and the Compose content,
 * theming the whole tree from persisted settings.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val ready by viewModel.ready.collectAsStateWithLifecycle()
            var unlocked by remember { mutableStateOf(!settings.biometricLockEnabled) }

            // Ask for notification permission once so watched-thread updates can be delivered.
            val permissionLauncher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { }
            LaunchedEffect(Unit) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            LaunchedEffect(settings.biometricLockEnabled) {
                if (settings.biometricLockEnabled) {
                    unlocked = false
                    authenticateToUnlock { unlocked = true }
                } else {
                    unlocked = true
                }
            }

            com.orbin.core.designsystem.theme.OrbinTheme(
                themeMode = settings.themeMode.toDesignSystem(),
                dynamicColor = settings.dynamicColor,
                amoled = settings.amoled,
                fontScale = settings.fontScale,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    when {
                        !unlocked -> Text("Orbin is locked")
                        // Wait for the first persisted snapshot so onboarding gating is correct.
                        ready ->
                            OrbinAppProviders {
                                OrbinApp(startWithOnboarding = !settings.onboardingCompleted)
                            }
                    }
                }
            }
        }
    }

    private fun authenticateToUnlock(onUnlocked: () -> Unit) {
        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val canAuthenticate = BiometricManager.from(this).canAuthenticate(authenticators)
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            onUnlocked()
            return
        }

        val prompt =
            BiometricPrompt(
                this,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onUnlocked()
                    }
                },
            )
        val promptInfo =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle("Unlock Orbin")
                .setSubtitle("Use fingerprint or device credentials")
                .setAllowedAuthenticators(authenticators)
                .build()

        prompt.authenticate(promptInfo)
    }
}

private fun AppThemeMode.toDesignSystem(): ThemeMode =
    when (this) {
        AppThemeMode.SYSTEM -> ThemeMode.SYSTEM
        AppThemeMode.LIGHT -> ThemeMode.LIGHT
        AppThemeMode.DARK -> ThemeMode.DARK
    }
