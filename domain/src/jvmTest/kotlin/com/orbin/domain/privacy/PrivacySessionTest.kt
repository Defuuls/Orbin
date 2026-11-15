package com.orbin.domain.privacy

import com.orbin.core.model.AppSettings
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacySessionTest {
    @After
    fun clearSession() = PrivacySession.forgetSession()

    @Test
    fun revealIsTemporaryAndCannotBypassPermanentHiding() {
        PrivacySession.forgetSession()
        val preferences = AppSettings(hideNsfwBoards = false, nsfwEachSession = true)
        assertTrue(preferences.copy(nsfwSessionRevealed = PrivacySession.nsfwRevealed.value).nsfwContentHidden)
        PrivacySession.revealNsfw()
        assertFalse(preferences.copy(nsfwSessionRevealed = PrivacySession.nsfwRevealed.value).nsfwContentHidden)
        assertTrue(preferences.copy(hideNsfwBoards = true, nsfwSessionRevealed = true).nsfwContentHidden)
        PrivacySession.forgetSession()
        assertTrue(preferences.copy(nsfwSessionRevealed = PrivacySession.nsfwRevealed.value).nsfwContentHidden)
    }
}
