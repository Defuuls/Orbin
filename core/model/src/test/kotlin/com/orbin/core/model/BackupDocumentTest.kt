package com.orbin.core.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * A backup is only worth writing if it restores faithfully, and it has to survive the app version
 * moving underneath it — the whole point is importing into a *different* install than exported it.
 */
class BackupDocumentTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val populated =
        BackupDocument(
            exportedAt = "2026-08-04T12:00:00Z",
            exportedByAppVersion = "59-Betelgeuse",
            settings =
                AppSettings(
                    personalizedHomeFeed = false,
                    hiddenTags = "spoilers, wip",
                    themeMode = AppThemeMode.DARK,
                    colorTheme = ColorTheme.TOMORROW_NIGHT,
                    appIconVariant = AppIconVariant.DUAL_GRADIENT,
                    thumbnailSize = ThumbnailSize.FILL,
                    fontScale = 1.2f,
                    feedThreadLimit = FeedThreadLimit.ALL,
                    dohProvider = DohProvider.NEXTDNS,
                    preloadThrottleMode = PreloadThrottleMode.UNLIMITED,
                    quietHoursStart = "23:00",
                    activeProviderId = "vichan",
                ),
            subscribedBoards = listOf(BackupBoardRef("vichan", "g"), BackupBoardRef("lynxchan", "tech")),
            favoriteBoards = listOf(BackupBoardRef("vichan", "a")),
        )

    @Test
    fun roundTripsWithoutLosingAnything() {
        val restored = json.decodeFromString(BackupDocument.serializer(), encode(populated))

        assertThat(restored).isEqualTo(populated)
    }

    @Test
    fun everySettingSurvivesTheRoundTrip() {
        val restored = json.decodeFromString(BackupDocument.serializer(), encode(populated))

        // Guards against a field being added to AppSettings but dropped from the backup format.
        assertThat(restored.settings).isEqualTo(populated.settings)
    }

    /** A backup from a newer build must still import, minus whatever this build cannot model. */
    @Test
    fun unknownFieldsFromANewerBuildAreIgnored() {
        val withExtras =
            """
            {
              "formatVersion": 1,
              "exportedAt": "2026-08-04T12:00:00Z",
              "settings": { "amoled": true, "somethingAddedLater": "surprise" },
              "subscribedBoards": [ { "providerId": "vichan", "boardId": "g" } ],
              "aWholeNewSection": [ 1, 2, 3 ]
            }
            """.trimIndent()

        val restored = json.decodeFromString(BackupDocument.serializer(), withExtras)

        assertThat(restored.settings.amoled).isTrue()
        assertThat(restored.subscribedBoards).containsExactly(BackupBoardRef("vichan", "g"))
    }

    /** A backup from an older build must import too, defaulting anything it predates. */
    @Test
    fun missingFieldsFallBackToDefaults() {
        val minimal = """{ "exportedAt": "2026-08-04T12:00:00Z" }"""

        val restored = json.decodeFromString(BackupDocument.serializer(), minimal)

        assertThat(restored.settings).isEqualTo(AppSettings.Default)
        assertThat(restored.subscribedBoards).isEmpty()
        assertThat(restored.formatVersion).isEqualTo(BackupDocument.CURRENT_FORMAT_VERSION)
    }

    @Test
    fun boardRefsKeepProviderScopeSoIdenticalBoardIdsDoNotCollide() {
        val restored = json.decodeFromString(BackupDocument.serializer(), encode(populated))

        assertThat(restored.subscribedBoards.map { it.providerId }).containsExactly("vichan", "lynxchan")
    }

    private fun encode(document: BackupDocument): String = json.encodeToString(BackupDocument.serializer(), document)
}
