package com.orbin.ios

import com.orbin.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Setup waits for local preferences, then stays open until a followed board has been saved. */
data class FirstRunState(
    val initialized: Boolean = false,
    val needed: Boolean = true,
    val hasFollows: Boolean = false,
    val pendingWrites: Int = 0,
    val completing: Boolean = false,
    val errorMessage: String? = null,
) {
    val isSaving: Boolean get() = pendingWrites > 0 || completing
    val canStart: Boolean get() = initialized && needed && hasFollows && !isSaving
}

/**
 * Initializes Home's followed boards before the reader can leave first run. The initial local
 * preferences decide whether setup is needed; later follow changes never dismiss the picker.
 * Older installs which skipped setup with no boards get the picker on their next launch, while
 * existing readers with a populated feed retain their boards and bypass setup.
 */
class FirstRun(
    private val settings: SettingsRepository,
    private val followsAnyBoard: Flow<Boolean>,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(FirstRunState())
    val state: StateFlow<FirstRunState> = _state.asStateFlow()
    private val writes = Mutex()

    init {
        scope.launch {
            var initialized = false
            combine(settings.settings, followsAnyBoard) { current, follows ->
                current.onboardingCompleted to follows
            }.collect { (done, follows) ->
                if (initialized) {
                    _state.update { it.copy(hasFollows = follows) }
                } else {
                    // Repair skipped setup once at startup. Do not remand a reader who deliberately
                    // unfollows their last board later in the same session.
                    val failure =
                        try {
                            if (done != follows) settings.setOnboardingCompleted(follows)
                            null
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (
                            @Suppress("TooGenericExceptionCaught") error: Exception,
                        ) {
                            error.readable()
                        }
                    initialized = true
                    _state.update {
                        it.copy(initialized = true, needed = !follows, hasFollows = follows, errorMessage = failure)
                    }
                }
            }
        }
    }

    /** Serializes board edits and keeps Start disabled until every edit has actually persisted. */
    fun updateFollow(write: suspend () -> Unit): Job {
        if (_state.value.completing) return scope.launch {}
        _state.update { it.copy(pendingWrites = it.pendingWrites + 1, errorMessage = null) }
        return scope.launch {
            try {
                writes.withLock { write() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Exception,
            ) {
                _state.update { it.copy(errorMessage = error.readable()) }
            } finally {
                _state.update { it.copy(pendingWrites = it.pendingWrites - 1) }
            }
        }
    }

    /** Only a successful persisted follow and an explicit Start can finish a new reader's setup. */
    fun complete(onCompleted: suspend () -> Unit = {}): Job {
        if (!_state.value.canStart) return scope.launch {}
        _state.update { it.copy(completing = true, errorMessage = null) }
        return scope.launch {
            try {
                writes.withLock {
                    if (!followsAnyBoard.first()) {
                        _state.update { it.copy(hasFollows = false) }
                        return@withLock
                    }
                    settings.setOnboardingCompleted(true)
                    onCompleted()
                    _state.update { it.copy(needed = false) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Exception,
            ) {
                _state.update { it.copy(errorMessage = error.readable()) }
            } finally {
                _state.update { it.copy(completing = false) }
            }
        }
    }
}
