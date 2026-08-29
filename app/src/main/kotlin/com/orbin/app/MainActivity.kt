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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.lifecycleScope
import com.orbin.core.common.lock.AppLockController
import com.orbin.core.designsystem.theme.ColorSchemeVariant
import com.orbin.core.designsystem.theme.ThemeMode
import com.orbin.core.model.AppSettings
import com.orbin.core.model.AppThemeMode
import com.orbin.core.model.ColorTheme
import com.orbin.domain.repository.DiagnosticsRepository
import com.orbin.domain.repository.VersionGuardRepository
import com.orbin.uinext.NextTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Single-activity host. Sets up the splash screen, edge-to-edge layout, and the Compose content,
 * theming the whole tree from persisted settings.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject
    lateinit var appIconManager: AppIconManager

    @Inject
    lateinit var appLockController: AppLockController

    @Inject
    lateinit var diagnosticsRepository: DiagnosticsRepository

    @Inject
    lateinit var versionGuardRepository: VersionGuardRepository

    /**
     * Registered eagerly: the safe-mode screen may be the very first thing composed, and a
     * launcher registered after the activity is started throws.
     */
    private val exportDiagnosticsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            uri ?: return@registerForActivityResult
            lifecycleScope.launch {
                val report = diagnosticsRepository.exportReport() ?: return@launch
                withContext(Dispatchers.IO) {
                    runCatching {
                        contentResolver.openOutputStream(uri)?.use { it.write(report.toByteArray()) }
                    }
                }
            }
        }

    private var safeMode by mutableStateOf(false)

    private var downgradeBlocked by mutableStateOf(false)

    private var relockOnResume by mutableStateOf(false)
    private var biometricLockActive = false
    private var authenticationInProgress by mutableStateOf(false)
    private var activeBiometricPrompt: BiometricPrompt? = null
    private var authenticationSession = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    /** The recovery UI shown when startup has been crash-looping. */
    @Composable
    private fun SafeMode() {
        SafeModeScreen(
            onExportDiagnostics = { exportDiagnostics() },
            onResetLocalData = {
                lifecycleScope.launch {
                    diagnosticsRepository.resetLocalData()
                    safeMode = false
                }
            },
            onContinueAnyway = { safeMode = false },
        )
    }

    /** The dead end shown when this build is older than one that has already run here. */
    @Composable
    private fun DowngradeBlocked() {
        DowngradeBlockedScreen(
            currentVersionCode = versionGuardRepository.currentVersionCode(),
            highestVersionCode = versionGuardRepository.highestVersionCodeSeen(),
            onClose = { finish() },
        )
    }

    /**
     * A launch that stays up this long counts as working, clearing the crash-loop counter. Long
     * enough that a startup crash would already have happened, short enough that a user who waits
     * out one bad launch isn't held in safe mode afterwards.
     *
     * The downgrade high-water mark is raised by the same signal, and deliberately not at process
     * start: a build that crash-loops on startup would otherwise raise the mark on its way down,
     * blocking the working build the user came from and turning a bad release into an
     * unrecoverable one.
     */
    @Composable
    private fun MarkLaunchSucceededAfterDelay() {
        LaunchedEffect(Unit) {
            delay(LAUNCH_SUCCESS_DELAY_MILLIS)
            diagnosticsRepository.markLaunchSucceeded()
            versionGuardRepository.recordSuccessfulLaunch()
        }
    }

    private fun exportDiagnostics() {
        exportDiagnosticsLauncher.launch(DIAGNOSTICS_FILE_NAME)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // After super.onCreate: Hilt field injection for an @AndroidEntryPoint activity happens
        // there, so reading an injected dependency any earlier throws. Still before setContent,
        // which is all safe mode needs.
        safeMode = diagnosticsRepository.isCrashLooping()
        downgradeBlocked = versionGuardRepository.isDowngrade()

        setContent {
            // Ahead of safe mode: a build that must not run must not run, and safe mode's recovery
            // actions (resetting local data) would be the wrong thing to offer for a problem that
            // is not about local state at all.
            if (downgradeBlocked) {
                DowngradeBlocked()
                return@setContent
            }

            // Before the view model, the theme or anything else that reads local state: a crash
            // loop is most likely caused by that state, so safe mode must not depend on it.
            if (safeMode) {
                SafeMode()
                return@setContent
            }

            MarkLaunchSucceededAfterDelay()

            val viewModel: MainViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val ready by viewModel.ready.collectAsStateWithLifecycle()
            val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
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
                    onAuthenticationError = { message ->
                        unlockMessage = message
                    },
                    onAuthenticationFailed = {
                        unlockMessage = getString(R.string.lock_error_not_recognized)
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

            LaunchedEffect(settings.appIconVariant) {
                appIconManager.setIconVariant(settings.appIconVariant)
            }

            // A "lock now" request from anywhere in the UI (e.g. the feed's failsafe button)
            // reuses the exact same re-lock path a background/foreground cycle takes: requesting
            // it when biometric lock isn't enabled is a no-op, same as it would be on resume.
            LaunchedEffect(Unit) {
                appLockController.lockRequests.collect { relockOnResume = true }
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
                isOnline = isOnline,
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
        onAuthenticationError: (String) -> Unit,
        onAuthenticationFailed: () -> Unit,
    ) {
        if (authenticationInProgress) return
        authenticationInProgress = true
        val session = ++authenticationSession

        // Require biometric authentication with KeyStore-backed cryptography.
        // Device credential fallback is not used to ensure all authentication paths
        // use cryptographic verification to prevent bypass via hooking tools.
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
        val canAuthenticate = BiometricManager.from(this).canAuthenticate(authenticators)
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            finishAuthentication(session)
            onAuthenticationError(getString(R.string.lock_biometric_unavailable))
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
                    onAuthenticationError(getString(R.string.lock_error_canceled))
                    return
                }

        val timeout =
            Runnable {
                val promptToCancel = activeBiometricPrompt
                if (finishAuthentication(session)) {
                    promptToCancel?.cancelAuthentication()
                    onAuthenticationError(getString(R.string.lock_error_timeout))
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
                                onAuthenticationError(getString(R.string.lock_error_canceled))
                            }
                        }
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        mainHandler.removeCallbacks(timeout)
                        if (finishAuthentication(session)) {
                            onAuthenticationError(
                                errString.toString().ifBlank { getString(R.string.lock_error_canceled) },
                            )
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
                .setTitle(getString(R.string.lock_prompt_title))
                .setSubtitle(getString(R.string.lock_prompt_subtitle))
                .setAllowedAuthenticators(authenticators)
                .setNegativeButtonText(getString(R.string.lock_prompt_cancel))
                .build()

        runCatching { prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher)) }
            .onFailure {
                mainHandler.removeCallbacks(timeout)
                if (finishAuthentication(session)) {
                    onAuthenticationError(getString(R.string.lock_error_canceled))
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
        private const val AUTHENTICATION_TIMEOUT_MS = 30_000L
        private const val DIAGNOSTICS_FILE_NAME = "orbin-diagnostics.txt"

        /** How long a launch must stay up before it counts as working. */
        private const val LAUNCH_SUCCESS_DELAY_MILLIS = 15_000L
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
    isOnline: Boolean,
    shouldLock: Boolean,
    unlocked: Boolean,
    unlockMessage: String?,
    allowContinueWithoutLock: Boolean,
    authenticationInProgress: Boolean,
    onRetryUnlock: () -> Unit,
    onContinueWithoutLock: () -> Unit,
) {
    // Two themes, because the app draws from two layers. OrbinTheme covers the Material surfaces
    // — the gallery, the onboarding wizard, dialogs and snackbars. NextTheme covers the interface
    // itself, whose screens each wrap themselves in one and inherit this outer choice rather than
    // overwriting it; stating it here is how a theme setting reaches them at all.
    com.orbin.core.designsystem.theme.OrbinTheme(
        themeMode = settings.themeMode.toDesignSystem(),
        colorSchemeVariant = settings.colorTheme.toDesignSystem(),
        dynamicColor = settings.dynamicColor,
        amoled = settings.amoled,
        fontScale = settings.fontScale,
    ) {
        NextTheme(
            darkTheme = settings.themeMode.isDark(),
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
                    if (ready) {
                        OrbinAppProviders {
                            OrbinApp(
                                startWithOnboarding = !settings.onboardingCompleted,
                                fullScreenFeedChrome = settings.fullScreenFeedChrome,
                                threadPresentation = settings.threadPresentation,
                                isOnline = isOnline,
                            )
                        }
                    }
                }

                if (ready && shouldLock && !unlocked) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        LockedScreen(
                            message = unlockMessage,
                            unlocking = authenticationInProgress,
                            allowContinueWithoutLock = allowContinueWithoutLock,
                            onRetry = onRetryUnlock,
                            onContinueWithoutLock = onContinueWithoutLock,
                        )
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
        Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = message ?: stringResource(R.string.lock_authenticate_hint),
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onRetry,
            enabled = !unlocking,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(if (unlocking) stringResource(R.string.lock_unlocking) else stringResource(R.string.lock_unlock))
        }
        if (allowContinueWithoutLock) {
            TextButton(onClick = onContinueWithoutLock) {
                Text(stringResource(R.string.lock_continue_without))
            }
        }
    }
}

/**
 * The same choice as [toDesignSystem], resolved to a plain boolean for `:ui-next`, which has one
 * dark palette and one light one rather than a mode. SYSTEM is answered here rather than left to
 * NextTheme's own default so both themes read the same setting in the same composition.
 */
@Composable
private fun AppThemeMode.isDark(): Boolean =
    when (this) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

private fun AppThemeMode.toDesignSystem(): ThemeMode =
    when (this) {
        AppThemeMode.SYSTEM -> ThemeMode.SYSTEM
        AppThemeMode.LIGHT -> ThemeMode.LIGHT
        AppThemeMode.DARK -> ThemeMode.DARK
    }

// The two enums are kept name-for-name in sync (core:model persists the setting; the design
// system owns the palettes), so map by name and fall back to Orbin if they ever diverge.
private fun ColorTheme.toDesignSystem(): ColorSchemeVariant =
    runCatching { ColorSchemeVariant.valueOf(name) }.getOrDefault(ColorSchemeVariant.ORBIN)

private fun shouldRequestNotificationPermission(
    ready: Boolean,
    shouldLock: Boolean,
    unlocked: Boolean,
    alreadyRequested: Boolean,
): Boolean = ready && (unlocked || !shouldLock) && !alreadyRequested
