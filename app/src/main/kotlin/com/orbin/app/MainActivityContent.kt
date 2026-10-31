package com.orbin.app

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbin.app.navigation.Route
import com.orbin.app.update.UpdateDialog
import com.orbin.app.update.UpdateDialogActions
import com.orbin.core.model.AppSettings
import com.orbin.uinext.LockScreen
import com.orbin.uinext.NextColorTheme
import com.orbin.uinext.NextPlatform
import com.orbin.uinext.NextTheme
import com.orbin.uinext.colorThemePalette
import com.orbin.uinext.next
import kotlinx.coroutines.launch

// What MainActivity draws once it is past safe mode and the version guard: the app itself, its
// update prompt, and the notification-permission request.

@Composable
internal fun RequestNotificationPermissionWhenUnlocked(
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

/** Runs the launch update check once the app is [visible], and shows the update dialog. */
@Composable
internal fun AppUpdateDialog(
    viewModel: MainViewModel,
    visible: Boolean,
) {
    val state by viewModel.updateState.collectAsStateWithLifecycle()
    LaunchedEffect(visible) {
        if (visible) viewModel.checkForUpdateOnLaunch(BuildConfig.VERSION_NAME)
    }
    val actions =
        remember(viewModel) {
            UpdateDialogActions(
                onUpdate = viewModel::update,
                onDismiss = viewModel::dismissUpdate,
                onCancel = viewModel::cancelUpdate,
                onOpenSettings = viewModel::openInstallPermissionSettings,
                onInstall = viewModel::installUpdate,
            )
        }
    if (visible) UpdateDialog(state, actions)
}

@Composable
internal fun AppContent(
    settings: AppSettings,
    ready: Boolean,
    isOnline: Boolean,
    shouldLock: Boolean,
    unlocked: Boolean,
    unlockMessage: String?,
    authenticationInProgress: Boolean,
    onRetryUnlock: () -> Unit,
    pendingRoute: Route?,
    onPendingRouteHandled: () -> Unit,
    updateDialog: @Composable () -> Unit,
) {
    // Root installs NextTheme once. Nested no-arg NextTheme calls short-circuit, so Next screens
    // do not pay a second MaterialTheme. Reachable destinations draw through NextTheme; nested
    // Material sliders / snackbars / pull-to-refresh were replaced with Next controls, so the
    // MaterialOrbinTheme adapter is gone.
    val nextPalette =
        colorThemePalette(
            NextColorTheme.valueOf(settings.colorTheme.name),
            settings.themeMode.isDark(),
            settings.amoled,
        )
    NextTheme(
        darkTheme = nextPalette.dark,
        amoled = nextPalette.amoled,
        palette = nextPalette,
        platform = NextPlatform.ANDROID,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = next.background,
        ) {
            if (ready && (!shouldLock || unlocked)) {
                OrbinAppProviders {
                    OrbinApp(
                        startWithOnboarding = !settings.onboardingCompleted,
                        isOnline = isOnline,
                        pendingRoute = pendingRoute,
                        onPendingRouteHandled = onPendingRouteHandled,
                    )
                }
            }

            if (ready && shouldLock && !unlocked) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = next.background,
                ) {
                    LockScreen(
                        message = unlockMessage,
                        unlocking = authenticationInProgress,
                        onUnlock = onRetryUnlock,
                    )
                }
            }

            if (ready) updateDialog()
        }
    }
}

private fun shouldRequestNotificationPermission(
    ready: Boolean,
    shouldLock: Boolean,
    unlocked: Boolean,
    alreadyRequested: Boolean,
): Boolean = ready && (unlocked || !shouldLock) && !alreadyRequested
