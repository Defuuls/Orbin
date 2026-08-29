package com.orbin.minimal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.orbin.domain.repository.VersionGuardRepository
import com.orbin.uinext.MessageScreen
import com.orbin.uinext.NextTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * The whole app: a subscription feed, a board picker, and a thread reader.
 *
 * Notably absent next to the full client's activity — and absent on purpose — are the settings
 * hub, biometric app-lock, safe mode, the app-icon switcher, notification permissions, and the
 * theme controls. This build takes the system's light/dark setting and stops there.
 */
@AndroidEntryPoint
class MinimalActivity : ComponentActivity() {
    @Inject
    lateinit var versionGuardRepository: VersionGuardRepository

    private var downgradeBlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // After super.onCreate, where Hilt field injection happens for an @AndroidEntryPoint
        // activity, and before setContent, which is all the check needs.
        downgradeBlocked = versionGuardRepository.isDowngrade()

        setContent {
            // Stated once here, exactly as the full client states its settings, so every screen
            // below inherits one choice rather than each resolving its own. This app has no
            // settings screen, so the system is the whole of the choice.
            NextTheme {
                if (downgradeBlocked) {
                    DowngradeBlocked()
                } else {
                    MarkLaunchSucceededAfterDelay()
                    MinimalNavHost()
                }
            }
        }
    }

    /**
     * The full client refuses to run a build older than one that has already run on this install,
     * and so does this one. Its own applicationId means its own high-water mark: the two APKs
     * guard themselves independently, and neither can block the other.
     *
     * Drawn as every other dead end in the app is drawn — a title, what happened, and the one
     * thing left to do — rather than as a Material dialog, which is the only screen this app had
     * that still looked like the interface the rest of it replaced.
     */
    @Composable
    private fun DowngradeBlocked() {
        MessageScreen(
            title = stringResource(R.string.minimal_downgrade_title),
            subtitle =
                stringResource(
                    R.string.minimal_downgrade_body,
                    versionGuardRepository.highestVersionCodeSeen(),
                ),
            actionLabel = stringResource(R.string.minimal_close),
            onAction = ::finish,
        )
    }

    /**
     * Raises the downgrade high-water mark only once a launch has actually got somewhere.
     * Recording at process start would let a build that crash-loops on startup raise the mark on
     * its way down, locking the user out of the working build they came from.
     */
    @Composable
    private fun MarkLaunchSucceededAfterDelay() {
        LaunchedEffect(Unit) {
            delay(LAUNCH_SUCCESS_DELAY_MILLIS)
            versionGuardRepository.recordSuccessfulLaunch()
        }
    }

    private companion object {
        const val LAUNCH_SUCCESS_DELAY_MILLIS = 5_000L
    }
}
