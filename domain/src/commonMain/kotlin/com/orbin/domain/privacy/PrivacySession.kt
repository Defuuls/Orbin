package com.orbin.domain.privacy

import com.orbin.domain.repository.HistorySession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Explicit NSFW consent is kept in memory and never becomes a saved preference or backup field. */
object PrivacySession {
    private val revealed = MutableStateFlow(false)
    val nsfwRevealed: StateFlow<Boolean> = revealed.asStateFlow()

    fun revealNsfw() {
        revealed.value = true
    }

    /** Called when the app is left or locked; a subsequent session needs another explicit reveal. */
    fun forgetSession() {
        revealed.value = false
        HistorySession.forgetSession()
    }
}
