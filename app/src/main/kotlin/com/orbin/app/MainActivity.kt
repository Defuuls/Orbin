package com.orbin.app

import android.Manifest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.orbin.core.designsystem.theme.ThemeMode
import com.orbin.core.model.AppSettings
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
    private var activeBiometricPrompt: BiometricPrompt? = null
    private var authenticationSession = 0
    private val mainHandler = Handler(Looper.getMainLooper())

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

            RequestNotificationPermissionWhenUnlocked(
                ready = ready,
                shouldLock = shouldLock,
                unlocked = unlocked,
            )
            SideEffect {
                biometricLockActive = shouldLock
            }

            // BiometricPrompt silently fails to appear (no callback, no exception - just no
            // dialog) if authenticate() is called before the activity is genuinely RESUMED, which
            // a plain LaunchedEffect can't guarantee since Compose's first composition can run
            // before onResume finishes. Track the real lifecycle state so the prompt is only ever
            // requested once the activity has actually reached RESUMED.
            val lifecycleOwner = LocalLifecycleOwner.current
            val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()

            LaunchedEffect(ready, shouldLock, relockOnResume, lifecycleState) {
                if (!ready) return@LaunchedEffect

                if (shouldLock) {
                    setSecureContent(enabled = true)
                    if (relockOnResume) {
                        relockOnResume = false
                        unlocked = false
                        unlockMessage = null
                        allowContinueWithoutLock = false
                    }
                    if (!unlocked && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
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

            AppContent(
                settings = settings,
                ready = ready,
                shouldLock = shouldLock,
                unlocked = unlocked,
                unlockMessage = unlockMessage,
                allowContinueWithoutLock = allowContinueWithoutLock,
                authenticationInProgress = authenticationInProgress,
                onRetryUnlock = { requestUnlock() },
                onContinueWithoutLock = {
                    unlockMessage = null
                    allowContinueWithoutLock = false
                    unlocked = true
                },
            )
        }
    }

    override fun onStop() {
        cancelActiveAuthentication()
        super.onStop()
        if (biometricLockActive) {
            // Cancel defensively rather than relying on the system to always deliver a
            // cancellation callback before the activity fully stops — that race can leave
            // authenticationInProgress stuck true, which would silently block every future
            // unlock attempt (automatic and manual).
            relockOnResume = true
        }
    }

    override fun onDestroy() {
        cancelActiveAuthentication()
        super.onDestroy()
    }

    private fun authenticateToUnlock(
        onUnlocked: () -> Unit,
        onAuthenticationUnavailable: (String) -> Unit,
        onAuthenticationError: (String) -> Unit,
        onAuthenticationFailed: () -> Unit,
    ) {
        if (authenticationInProgress) return
        authenticationInProgress = true
        val session = ++authenticationSession

        // Biometric-only: a CryptoObject-gated unlock (see below) can't be combined with
        // DEVICE_CREDENTIAL, since a PIN/pattern unlock doesn't correspond to a fresh execution
        // of the underlying Keystore crypto operation the way a biometric match does.
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
        val canAuthenticate = BiometricManager.from(this).canAuthenticate(authenticators)
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            finishAuthentication(session)
            onAuthenticationUnavailable(AUTHENTICATION_UNAVAILABLE_MESSAGE)
            return
        }

        val cipher =
            runCatching { AppLockCrypto.createUnlockCipher() }
                .getOrElse {
                    // Enrolled biometrics changed since the key was created; the old key is
                    // permanently invalidated. Drop it so a fresh one is generated (and gated by
                    // the current enrollment) on the next attempt.
                    AppLockCrypto.invalidate()
                    finishAuthentication(session)
                    onAuthenticationError(AUTHENTICATION_ERROR_MESSAGE)
                    return
                }

        val timeout =
            Runnable {
                val promptToCancel = activeBiometricPrompt
                if (finishAuthentication(session)) {
                    promptToCancel?.cancelAuthentication()
                    onAuthenticationError(AUTHENTICATION_TIMEOUT_MESSAGE)
                }
            }
        mainHandler.postDelayed(timeout, AUTHENTICATION_TIMEOUT_MS)

        val prompt =
            BiometricPrompt(
                this,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        mainHandler.removeCallbacks(timeout)
                        if (finishAuthentication(session)) {
                            // The callback firing isn't proof enough on its own; only treat this
                            // as unlocked once the Keystore-backed cipher actually completes,
                            // since that is what the TEE/StrongBox gates on a genuine biometric
                            // match.
                            val resultCipher = result.cryptoObject?.cipher
                            val verified =
                                resultCipher != null &&
                                    runCatching { AppLockCrypto.verify(resultCipher) }.isSuccess
                            if (verified) {
                                onUnlocked()
                            } else {
                                onAuthenticationError(AUTHENTICATION_ERROR_MESSAGE)
                            }
                        }
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        mainHandler.removeCallbacks(timeout)
                        if (finishAuthentication(session)) {
                            onAuthenticationError(errString.toString().ifBlank { AUTHENTICATION_ERROR_MESSAGE })
                        }
                    }

                    override fun onAuthenticationFailed() {
                        onAuthenticationFailed()
                    }
                },
            )
        activeBiometricPrompt = prompt
        val promptInfo =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle("Unlock Orbin")
                .setSubtitle("Use fingerprint or face unlock")
                .setAllowedAuthenticators(authenticators)
                .setNegativeButtonText("Cancel")
                .build()

        runCatching { prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher)) }
            .onFailure {
                mainHandler.removeCallbacks(timeout)
                if (finishAuthentication(session)) {
                    onAuthenticationError(AUTHENTICATION_ERROR_MESSAGE)
                }
            }
    }

    private fun cancelActiveAuthentication() {
        activeBiometricPrompt?.cancelAuthentication()
        activeBiometricPrompt = null
        authenticationInProgress = false
        authenticationSession++
    }

    private fun finishAuthentication(session: Int): Boolean {
        if (session != authenticationSession) return false
        activeBiometricPrompt = null
        authenticationInProgress = false
        return true
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
        private const val AUTHENTICATION_TIMEOUT_MESSAGE = "Unlock timed out. Try again."
        private const val AUTHENTICATION_UNAVAILABLE_MESSAGE =
            "Biometric unlock is unavailable. Enroll a fingerprint or face unlock in Android " +
                "Settings, or continue without app lock."
        private const val AUTHENTICATION_TIMEOUT_MS = 30_000L
    }
}

@Composable
private fun RequestNotificationPermissionWhenUnlocked(
    ready: Boolean,
    shouldLock: Boolean,
    unlocked: Boolean,
) {
    var notificationPermissionRequested by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { }

    LaunchedEffect(ready, shouldLock, unlocked) {
        val shouldRequest =
            shouldRequestNotificationPermission(
                ready = ready,
                shouldLock = shouldLock,
                unlocked = unlocked,
                alreadyRequested = notificationPermissionRequested,
            )
        if (shouldRequest) {
            notificationPermissionRequested = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun AppContent(
    settings: AppSettings,
    ready: Boolean,
    shouldLock: Boolean,
    unlocked: Boolean,
    unlockMessage: String?,
    allowContinueWithoutLock: Boolean,
    authenticationInProgress: Boolean,
    onRetryUnlock: () -> Unit,
    onContinueWithoutLock: () -> Unit,
) {
    com.orbin.core.designsystem.theme.OrbinTheme(
        themeMode = settings.themeMode.toDesignSystem(),
        dynamicColor = settings.dynamicColor,
        amoled = settings.amoled,
        fontScale = settings.fontScale,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
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
                            onRetry = onRetryUnlock,
                            onContinueWithoutLock = onContinueWithoutLock,
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

private fun shouldRequestNotificationPermission(
    ready: Boolean,
    shouldLock: Boolean,
    unlocked: Boolean,
    alreadyRequested: Boolean,
): Boolean = ready && (unlocked || !shouldLock) && !alreadyRequested
