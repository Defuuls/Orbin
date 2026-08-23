package com.orbin.minimal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orbin.core.designsystem.theme.OrbinTheme
import com.orbin.domain.repository.VersionGuardRepository
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
            OrbinTheme {
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
     */
    @Composable
    private fun DowngradeBlocked() {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.minimal_downgrade_title),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text =
                        stringResource(
                            R.string.minimal_downgrade_body,
                            versionGuardRepository.highestVersionCodeSeen(),
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { finish() }) { Text("Close") }
            }
        }
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
