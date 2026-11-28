package com.orbin.app.pulse

import com.orbin.core.model.stream.CommandRanking
import com.orbin.core.model.stream.PaletteCommand
import com.orbin.core.model.stream.PaletteInput
import com.orbin.core.model.stream.StreamFilter
import com.orbin.core.model.stream.StreamLens
import com.orbin.uipulse.PulseCommandUi
import com.orbin.uipulse.PulseLineUi

/**
 * The lenses Pulse offers on Android today. Each opens the existing screen for that place, so
 * nothing is unreachable while the rest of Pulse is still being built.
 */
internal val PulseLenses: List<StreamLens> =
    listOf(StreamLens.Home, StreamLens.Watched, StreamLens.Saved, StreamLens.Settings)

internal const val COMMAND_REFRESH = "refresh"
internal const val COMMAND_BACK = "back"

/** What a command or a lens button does. */
internal sealed interface PulseAction {
    data object Refresh : PulseAction

    data object Back : PulseAction

    data class OpenLens(
        val lens: StreamLens,
    ) : PulseAction
}

/** The words the command list shows, supplied by the host so this file needs no resources. */
internal class PulseCommandWords(
    val refresh: String,
    val back: String,
    val lensLabel: (StreamLens) -> String,
)

/** What the stream shows for what is typed in the controls. */
internal data class PulseView(
    val lines: List<PulseLineUi>,
    val suggestions: List<PulseCommandUi>,
)

internal fun pulseCommands(
    showBack: Boolean,
    words: PulseCommandWords,
): List<PaletteCommand> =
    buildList {
        add(PaletteCommand(COMMAND_REFRESH, words.refresh, listOf("reload", "update")))
        if (showBack) add(PaletteCommand(COMMAND_BACK, words.back, listOf("return")))
        PulseLenses.forEach { add(PaletteCommand(it.id, words.lensLabel(it))) }
    }

/** `null` for an id that is not an action here, so an unknown value is ignored rather than guessed at. */
internal fun resolveAction(
    id: String?,
    showBack: Boolean,
): PulseAction? =
    when {
        id == COMMAND_REFRESH -> PulseAction.Refresh
        id == COMMAND_BACK -> PulseAction.Back.takeIf { showBack }
        else -> StreamLens.fromId(id)?.takeIf { it in PulseLenses }?.let { PulseAction.OpenLens(it) }
    }

/**
 * `/` starts a command search and leaves the stream alone; anything else filters the stream and
 * shows no suggestions.
 */
internal fun pulseView(
    lines: List<PulseLineUi>,
    command: String,
    commands: List<PaletteCommand>,
): PulseView =
    when (val input = CommandRanking.parseInput(command)) {
        is PaletteInput.Command ->
            PulseView(
                lines = lines,
                suggestions = CommandRanking.rank(commands, input.query).map { PulseCommandUi(it.id, it.label) },
            )
        is PaletteInput.Filter ->
            PulseView(
                lines = StreamFilter.apply(lines, input.query) { "${it.title} ${it.sub}" },
                suggestions = emptyList(),
            )
    }

/** What pressing the keyboard's action key does: run the best suggestion, or nothing. */
internal fun submitAction(
    command: String,
    commands: List<PaletteCommand>,
    showBack: Boolean,
): PulseAction? {
    val input = CommandRanking.parseInput(command) as? PaletteInput.Command ?: return null
    return CommandRanking.rank(commands, input.query).firstNotNullOfOrNull { resolveAction(it.id, showBack) }
}
