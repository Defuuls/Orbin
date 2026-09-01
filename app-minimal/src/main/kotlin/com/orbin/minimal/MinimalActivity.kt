package com.orbin.minimal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.orbin.domain.repository.VersionGuardRepository
import com.orbin.uinext.MessageScreen
import com.orbin.uinext.NextTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MinimalActivity : ComponentActivity() {
    @Inject
    lateinit var versionGuardRepository: VersionGuardRepository

    @Inject
    lateinit var experiencePolicy: MinimalExperiencePolicy

    private var downgradeBlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        downgradeBlocked = versionGuardRepository.isDowngrade()
        experiencePolicy.start()

        setContent {
            val profileReady by experiencePolicy.ready.collectAsStateWithLifecycle()
            NextTheme {
                when {
                    downgradeBlocked -> DowngradeBlocked()
                    !profileReady ->
                        MessageScreen(
                            title = stringResource(R.string.minimal_app_name),
                            subtitle = stringResource(R.string.minimal_preparing),
                        )
                    else -> {
                        MarkLaunchSucceededAfterForegroundDelay()
                        MinimalNavHost()
                    }
                }
            }
        }
    }

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

    /** Records success only after five seconds actually spent resumed in the foreground. */
    @Composable
    private fun MarkLaunchSucceededAfterForegroundDelay() {
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    delay(LAUNCH_SUCCESS_DELAY_MILLIS)
                    versionGuardRepository.recordSuccessfulLaunch()
                }
            }
        }
    }

    private companion object {
        const val LAUNCH_SUCCESS_DELAY_MILLIS = 5_000L
    }
}
