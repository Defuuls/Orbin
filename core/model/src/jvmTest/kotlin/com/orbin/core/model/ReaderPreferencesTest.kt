package com.orbin.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPreferencesTest {
    @Test
    fun oldSettingsKeepOrbinDefaultsAndSessionConsentIsNeverSerialized() {
        val codec = Json { encodeDefaults = true }
        val old = codec.decodeFromString<AppSettings>("{\"themeMode\":\"DARK\",\"amoled\":true}")
        assertEquals(AccentTheme.ORBIN, old.accentTheme)
        assertEquals(ThreadLayout.NESTED, old.threadLayout)
        assertTrue(old.amoled)
        val revealed = old.copy(nsfwEachSession = true, nsfwSessionRevealed = true)
        assertFalse(revealed.nsfwContentHidden)
        val encoded = codec.encodeToString(revealed)
        assertFalse(encoded.contains("nsfwSessionRevealed"))
        assertTrue(codec.decodeFromString<AppSettings>(encoded).nsfwContentHidden)
    }

    @Test
    fun memoryRoundTripRetainsFullSourceIdentityAndIndependentState() {
        val key = ThreadKey(ProviderId("fourchan"), BoardId("g"), ThreadId(42))
        val memory =
            ThreadMemory(
                key,
                anchorPostId = 7,
                rowPath = "1/7",
                offsetPx = 19,
                foldedPaths = setOf("1/8"),
                hiddenPostIds = setOf(8),
                hiddenPosterIds = setOf("same-id"),
                seenPostIds = setOf(1, 7),
                knownPostIds = setOf(1, 7, 8),
            )
        assertEquals(memory, Json.decodeFromString<ThreadMemory>(Json.encodeToString(memory)))
        assertFalse(memory.key == key.copy(provider = ProviderId("other-site")))
    }
}
