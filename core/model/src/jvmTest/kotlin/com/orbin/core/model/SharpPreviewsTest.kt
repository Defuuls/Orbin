package com.orbin.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SharpPreviewsTest {
    @Test
    fun listModeNeverLoadsTheOriginal() {
        assertThat(
            decide(modeWantsSharp = false, setting = SharpPreviewsMode.ALWAYS, isMetered = false),
        ).isFalse()
    }

    @Test
    fun neverSettingKeepsTheThumbnailOnUnmeteredWifi() {
        assertThat(
            decide(setting = SharpPreviewsMode.NEVER, isMetered = false, isDataSaver = false),
        ).isFalse()
    }

    @Test
    fun alwaysLoadsAnInBudgetStillImageOnAMeteredConnection() {
        assertThat(
            decide(setting = SharpPreviewsMode.ALWAYS, isMetered = true, isDataSaver = true),
        ).isTrue()
    }

    @Test
    fun wifiOnlyLoadsOnUnmeteredWifiWithoutDataSaver() {
        assertThat(
            decide(setting = SharpPreviewsMode.WIFI_ONLY, isMetered = false, isDataSaver = false),
        ).isTrue()
    }

    @Test
    fun wifiOnlyKeepsTheThumbnailOnAMeteredConnection() {
        assertThat(
            decide(setting = SharpPreviewsMode.WIFI_ONLY, isMetered = true, isDataSaver = false),
        ).isFalse()
    }

    @Test
    fun wifiOnlyTreatsAnUndeterminedConnectionAsMetered() {
        assertThat(
            decide(setting = SharpPreviewsMode.WIFI_ONLY, isMetered = true),
        ).isFalse()
    }

    @Test
    fun wifiOnlyTreatsDataSaverAsNever() {
        assertThat(
            decide(setting = SharpPreviewsMode.WIFI_ONLY, isMetered = false, isDataSaver = true),
        ).isFalse()
    }

    @Test
    fun wifiOnlyTreatsLowDataModeAsNever() {
        assertThat(
            decide(setting = SharpPreviewsMode.WIFI_ONLY, isMetered = false, isDataSaver = true),
        ).isFalse()
    }

    @Test
    fun anImageAtTheCapMayLoad() {
        assertThat(decide(sizeBytes = SHARP_PREVIEW_MAX_BYTES)).isTrue()
    }

    @Test
    fun anImageOverTheCapKeepsTheThumbnail() {
        assertThat(decide(sizeBytes = SHARP_PREVIEW_MAX_BYTES + 1)).isFalse()
    }

    @Test
    fun anUnknownSizeKeepsTheThumbnail() {
        assertThat(decide(sizeBytes = 0L)).isFalse()
        assertThat(decide(sizeBytes = -1L)).isFalse()
    }

    @Test
    fun videosKeepTheThumbnail() {
        assertThat(
            decide(setting = SharpPreviewsMode.ALWAYS, mediaType = MediaType.VIDEO),
        ).isFalse()
    }

    @Test
    fun gifsKeepTheThumbnail() {
        assertThat(
            decide(setting = SharpPreviewsMode.ALWAYS, mediaType = MediaType.ANIMATED_IMAGE),
        ).isFalse()
    }

    @Test
    fun audioAndUnknownTypesKeepTheThumbnail() {
        assertThat(decide(setting = SharpPreviewsMode.ALWAYS, mediaType = MediaType.AUDIO)).isFalse()
        assertThat(decide(setting = SharpPreviewsMode.ALWAYS, mediaType = MediaType.UNKNOWN)).isFalse()
    }

    @Test
    fun aCustomCapIsRespected() {
        assertThat(decide(sizeBytes = 1_000L, maxSizeBytes = 999L)).isFalse()
        assertThat(decide(sizeBytes = 999L, maxSizeBytes = 999L)).isTrue()
    }

    @Test
    fun defaultCapIsFourMebibytes() {
        assertThat(SHARP_PREVIEW_MAX_BYTES).isEqualTo(4L * 1_024 * 1_024)
    }

    @Test
    fun defaultSettingIsWifiOnly() {
        assertThat(AppSettings.Default.sharpPreviews).isEqualTo(SharpPreviewsMode.WIFI_ONLY)
    }

    private fun decide(
        modeWantsSharp: Boolean = true,
        setting: SharpPreviewsMode = SharpPreviewsMode.WIFI_ONLY,
        isMetered: Boolean = false,
        isDataSaver: Boolean = false,
        mediaType: MediaType = MediaType.IMAGE,
        sizeBytes: Long = 1_024L,
        maxSizeBytes: Long = SHARP_PREVIEW_MAX_BYTES,
    ): Boolean =
        shouldLoadSharpPreview(
            modeWantsSharp = modeWantsSharp,
            setting = setting,
            isMetered = isMetered,
            isDataSaver = isDataSaver,
            mediaType = mediaType,
            sizeBytes = sizeBytes,
            maxSizeBytes = maxSizeBytes,
        )
}
