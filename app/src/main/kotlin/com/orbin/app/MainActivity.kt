package com.orbin.app

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    private var relockOnResume by mutableStateOf(false)
    private var biometricLockActive = false
    private var authenticationInProgress by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val ready by viewModel.ready.collectAsStateWithLifecycle()
            val shouldLock = ready && settings.biometricLockEnabled && settings.onboardingCompleted
            var unlocked by remember { mutableStateOf(false) }
            var unlockMessage by remember { mutableStateOf<String?>(null) }
            var allowContinueWithoutLock by remember { mutableStateOf(false) }

            fun requestUnlock() {
                if (!shouldLock || authenticationInProgress) return
                allowContinueWithoutLock = false
                authenticateToUnlock(
                    onUnlocked = {
                        unlockMessage = null
                        allowContinueWithoutLock = false
                        unlocked = true
                    },
                    onAuthenticationUnavailable = { message ->
                        unlockMessage = message
                        allowContinueWithoutLock = true
                    },
                    onAuthenticationError = { message ->
                        unlockMessage = message
                    },
                    onAuthenticationFailed = {
                        unlockMessage = AUTHENTICATION_FAILED_MESSAGE
                    },
                )
            }

            // Ask for notification permission once so watched-thread updates can be delivered.
            val permissionLauncher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { }
            LaunchedEffect(Unit) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            SideEffect {
                biometricLockActive = shouldLock
            }

            LaunchedEffect(ready, shouldLock, relockOnResume) {
                if (!ready) return@LaunchedEffect

                if (shouldLock) {
                    setSecureContent(enabled = true)
                    if (relockOnResume) {
                        relockOnResume = false
                        unlocked = false
                        unlockMessage = null
                        allowContinueWithoutLock = false
                    }
                    if (!unlocked) {
                        requestUnlock()
                    }
                } else {
                    setSecureContent(enabled = false)
                    relockOnResume = false
                    unlocked = false
                    unlockMessage = null
                    allowContinueWithoutLock = false
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
                        !ready -> Unit
                        shouldLock && !unlocked ->
                            LockedScreen(
                                message = unlockMessage,
                                unlocking = authenticationInProgress,
                                allowContinueWithoutLock = allowContinueWithoutLock,
                                onRetry = { requestUnlock() },
                                onContinueWithoutLock = {
                                    unlockMessage = null
                                    allowContinueWithoutLock = false
                                    unlocked = true
                                },
                            )
                        // Wait for the first persisted snapshot so onboarding gating is correct.
                        else ->
                            OrbinAppProviders {
                                OrbinApp(startWithOnboarding = !settings.onboardingCompleted)
                            }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (biometricLockActive && !authenticationInProgress) {
            relockOnResume = true
        }
    }

    private fun authenticateToUnlock(
        onUnlocked: () -> Unit,
        onAuthenticationUnavailable: (String) -> Unit,
        onAuthenticationError: (String) -> Unit,
        onAuthenticationFailed: () -> Unit,
    ) {
        if (authenticationInProgress) return
        authenticationInProgress = true

        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val canAuthenticate = BiometricManager.from(this).canAuthenticate(authenticators)
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            authenticationInProgress = false
            onAuthenticationUnavailable(AUTHENTICATION_UNAVAILABLE_MESSAGE)
            return
        }

        val prompt =
            BiometricPrompt(
                this,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        authenticationInProgress = false
                        onUnlocked()
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        authenticationInProgress = false
                        onAuthenticationError(errString.toString().ifBlank { AUTHENTICATION_ERROR_MESSAGE })
                    }

                    override fun onAuthenticationFailed() {
                        onAuthenticationFailed()
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

        runCatching { prompt.authenticate(promptInfo) }
            .onFailure {
                authenticationInProgress = false
                onAuthenticationError(AUTHENTICATION_ERROR_MESSAGE)
            }
    }

    private fun setSecureContent(enabled: Boolean) {
        val secureFlag = WindowManager.LayoutParams.FLAG_SECURE
        if (enabled) {
            window.setFlags(secureFlag, secureFlag)
        } else {
            window.clearFlags(secureFlag)
        }
    }

    private companion object {
        private const val AUTHENTICATION_ERROR_MESSAGE = "Unlock was canceled. Try again."
        private const val AUTHENTICATION_FAILED_MESSAGE = "Authentication was not recognized. Try again."
        private val AUTHENTICATION_UNAVAILABLE_MESSAGE =
            "Device unlock is unavailable. Set up a biometric or screen lock in Android Settings, " +
                "or continue without app lock."
    }
}

@Composable
private fun LockedScreen(
    message: String?,
    unlocking: Boolean,
    allowContinueWithoutLock: Boolean,
    onRetry: () -> Unit,
    onContinueWithoutLock: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Orbin is locked", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = message ?: "Authenticate to continue.",
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onRetry,
            enabled = !unlocking,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(if (unlocking) "Unlocking..." else "Unlock")
        }
        if (allowContinueWithoutLock) {
            TextButton(onClick = onContinueWithoutLock) {
                Text("Continue without app lock")
            }
        }
    }
}

private fun AppThemeMode.toDesignSystem(): ThemeMode =
    when (this) {
        AppThemeMode.SYSTEM -> ThemeMode.SYSTEM
        AppThemeMode.LIGHT -> ThemeMode.LIGHT
        AppThemeMode.DARK -> ThemeMode.DARK
    }
