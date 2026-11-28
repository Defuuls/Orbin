package com.orbin.app.pulse

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.orbin.app.R
import com.orbin.core.model.stream.StreamLens
import com.orbin.uipulse.PulseControls
import com.orbin.uipulse.PulseControlsLabels
import com.orbin.uipulse.PulseLensUi
import com.orbin.uipulse.PulseLineUi
import com.orbin.uipulse.PulseStream

/** What a screen hands the shell: its own title and lines, and what its actions do. */
internal class PulseScreen(
    val title: String,
    val summary: String,
    val lines: List<PulseLineUi>,
    val selectedLens: StreamLens?,
    val showBack: Boolean,
    val onLineClick: (String) -> Unit,
    val onRefresh: () -> Unit,
    val onBack: () -> Unit,
    val onOpenLens: (StreamLens) -> Unit,
    val onVisibleLines: (List<String>) -> Unit = {},
)

/**
 * The stream and its controls for any Pulse screen. The feed and a thread differ only in the
 * lines they supply and in what Back and Refresh do, so the controls live here once.
 */
@Composable
internal fun PulseShell(screen: PulseScreen) {
    var controlsOpen by rememberSaveable { mutableStateOf(false) }
    var command by rememberSaveable { mutableStateOf("") }
    val words = pulseWords()
    val commands = remember(screen.showBack, words) { pulseCommands(screen.showBack, words) }
    val view = pulseView(screen.lines, command, commands)

    fun close() {
        controlsOpen = false
        command = ""
    }

    fun perform(action: PulseAction?) {
        close()
        when (action) {
            PulseAction.Refresh -> screen.onRefresh()
            PulseAction.Back -> screen.onBack()
            is PulseAction.OpenLens -> if (action.lens != screen.selectedLens) screen.onOpenLens(action.lens)
            null -> Unit
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        PulseStream(
            lensName = screen.title,
            summary = screen.summary,
            controlsLabel = stringResource(R.string.pulse_controls_hold),
            lines = view.lines,
            onLineClick = screen.onLineClick,
            onOpenControls = { controlsOpen = true },
            modifier = Modifier.safeDrawingPadding(),
            unreadLabel = stringResource(R.string.pulse_unread),
            onVisibleLines = screen.onVisibleLines,
        )
    }
    PulseControls(
        visible = controlsOpen,
        labels =
            PulseControlsLabels(
                title = stringResource(R.string.pulse_controls_title),
                refresh = stringResource(R.string.pulse_refresh),
                back = stringResource(R.string.pulse_back),
                commandHint = stringResource(R.string.pulse_command_hint),
                close = stringResource(R.string.pulse_close),
            ),
        lenses =
            PulseLenses.map {
                PulseLensUi(id = it.id, label = words.lensLabel(it), selected = it == screen.selectedLens)
            },
        showBack = screen.showBack,
        command = command,
        suggestions = view.suggestions,
        onCommandChange = { command = it },
        onRunCommand = { perform(resolveAction(it, screen.showBack)) },
        onSubmit = { perform(submitAction(command, commands, screen.showBack)) },
        onLens = { perform(resolveAction(it, screen.showBack)) },
        onRefresh = { perform(PulseAction.Refresh) },
        onBack = { perform(PulseAction.Back) },
        onDismiss = ::close,
    )
}

@Composable
private fun pulseWords(): PulseCommandWords {
    val refresh = stringResource(R.string.pulse_refresh)
    val back = stringResource(R.string.pulse_back)
    val home = stringResource(R.string.pulse_lens_home)
    val watched = stringResource(R.string.pulse_lens_watched)
    val saved = stringResource(R.string.pulse_lens_saved)
    val settings = stringResource(R.string.pulse_lens_settings)
    return remember(refresh, back, home, watched, saved, settings) {
        PulseCommandWords(refresh, back) { lens ->
            when (lens) {
                StreamLens.Home -> home
                StreamLens.Watched -> watched
                StreamLens.Saved -> saved
                StreamLens.Settings -> settings
                else -> lens.label
            }
        }
    }
}
