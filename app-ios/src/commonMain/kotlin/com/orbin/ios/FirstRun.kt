package com.orbin.ios

import com.orbin.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Whether to show first run's board picker, as Android does before its feed: until the reader
 * finishes it once.
 *
 * Earlier iOS builds had no picker and never marked setup done, so a reader who already follows a
 * board is taken to have finished it, and is marked so, rather than being shown it on update.
 * [needed] stays false until both the settings and the followed boards have been read, so it never
 * flashes up for a reader who should not see it.
 */
class FirstRun(
    private val settings: SettingsRepository,
    followsAnyBoard: Flow<Boolean>,
    private val scope: CoroutineScope,
) {
    val needed: StateFlow<Boolean> =
        combine(settings.settings, followsAnyBoard) { current, follows -> current.onboardingCompleted to follows }
            .onEach { (done, follows) -> if (!done && follows) settings.setOnboardingCompleted(true) }
            .map { (done, follows) -> !done && !follows }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, false)

    /** The picker's "Start browsing": it is not shown again. */
    fun complete(): Job = scope.launch { settings.setOnboardingCompleted(true) }
}
