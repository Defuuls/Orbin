package com.orbin.ios

import com.orbin.core.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Proves the device's owner is here: Face ID, Touch ID or the passcode on iOS. */
fun interface Authenticator {
    suspend fun authenticate(): AuthResult
}

sealed interface AuthResult {
    data object Success : AuthResult

    /** Not proven; [message] says why, for the lock screen. */
    data class Failed(
        val message: String,
    ) : AuthResult
}

/**
 * What the lock screen shows. [locked] needs the owner to unlock; [obscured] only hides the app,
 * as it does in the app switcher and until the settings have been read at launch.
 */
data class LockState(
    val locked: Boolean = false,
    val obscured: Boolean = true,
    val unlocking: Boolean = false,
    val message: String? = null,
)

/**
 * Android's app lock on iOS. With the setting on, the app is locked at launch and again whenever
 * it goes to the background, and asks to be unlocked when it comes back, as Android's does; while
 * it is inactive (the app switcher, Control Centre) it is hidden. Turning the lock on first proves
 * the owner can unlock, so no one locks themselves out of a device with no passcode.
 */
class AppLock(
    private val settings: Flow<AppSettings>,
    private val setEnabled: suspend (Boolean) -> Unit,
    private val authenticator: Authenticator,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(LockState())
    val state: StateFlow<LockState> = _state.asStateFlow()

    private var enabled = false
    private var attempt: Job? = null

    // Only a return from the background asks to unlock by itself. The Face ID sheet also takes the
    // app off screen and back, and a failed attempt must not ask again at once.
    private var askOnReturn = false

    init {
        scope.launch {
            enabled = settings.first().biometricLockEnabled
            _state.update { it.copy(locked = enabled, obscured = false) }
            if (enabled) unlock()
        }
        scope.launch {
            settings.map { it.biometricLockEnabled }.drop(1).collect { on ->
                enabled = on
                // Turning it off lets the reader straight in; turning it on waits for the background.
                if (!on) _state.update { LockState(obscured = false) }
            }
        }
    }

    /** Asks the owner to unlock, unless an attempt is already showing. */
    fun unlock(): Job? {
        if (attempt?.isActive == true || !_state.value.locked) return null
        _state.update { it.copy(unlocking = true, message = null) }
        return scope
            .launch {
                val result = authenticator.authenticate()
                _state.update {
                    when (result) {
                        AuthResult.Success -> it.copy(locked = false, unlocking = false)
                        is AuthResult.Failed -> it.copy(unlocking = false, message = result.message)
                    }
                }
            }.also { attempt = it }
    }

    /** Turns the lock on once the owner has proven they can unlock, or turns it off. */
    fun setLockEnabled(on: Boolean): Job =
        scope.launch {
            if (!on || authenticator.authenticate() == AuthResult.Success) setEnabled(on)
        }

    /** The app is leaving the screen for a moment: hide it, as Android's secure window does. */
    fun onResignActive() {
        if (enabled && attempt?.isActive != true) _state.update { it.copy(obscured = true) }
    }

    /** The app went to the background: it is locked when it comes back. */
    fun onBackground() {
        if (!enabled) return
        askOnReturn = true
        _state.update { it.copy(locked = true, obscured = true, message = null) }
    }

    /** The app is back on screen: show it, and ask to unlock it if it was in the background. */
    fun onForeground() {
        _state.update { it.copy(obscured = false) }
        if (askOnReturn) {
            askOnReturn = false
            unlock()
        }
    }
}
