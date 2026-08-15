package com.orbin.data.repository

import com.google.common.truth.Truth.assertThat
import com.orbin.core.model.UpdateStatus
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Newest SDK Robolectric 4.16 ships an image for; the app's target SDK is ahead of it. */
private const val ROBOLECTRIC_SDK = 35

/**
 * Covers the release comparison, which is the part of the update check that can be quietly wrong.
 *
 * Orbin's tags are `v<number>-<Codename>` where the codename is a themed label — stars up to v90,
 * pasta from v91 — and never a version component, so neither string ordering nor semver parsing
 * applies. Robolectric is here only to supply a real `org.json` — the JVM stub throws on every call.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class UpdateRepositoryParsingTest {
    @Test
    fun aHigherReleaseNumberIsOfferedAsAnUpdate() {
        val status = parseLatestRelease(release("v62-Canopus", "v62 — Canopus"), "61-Achernar")

        assertThat(status).isInstanceOf(UpdateStatus.Available::class.java)
        val available = status as UpdateStatus.Available
        assertThat(available.tag).isEqualTo("v62-Canopus")
        assertThat(available.name).isEqualTo("v62 — Canopus")
        assertThat(available.url).isEqualTo("https://example.invalid/release")
    }

    @Test
    fun theSameReleaseIsUpToDate() {
        assertThat(parseLatestRelease(release("v61-Achernar"), "61-Achernar")).isEqualTo(UpdateStatus.UpToDate)
    }

    /** A user on a newer build than the published release is not told to downgrade. */
    @Test
    fun anOlderPublishedReleaseIsUpToDate() {
        assertThat(parseLatestRelease(release("v60-Vega"), "61-Achernar")).isEqualTo(UpdateStatus.UpToDate)
    }

    /**
     * String ordering puts "v9" after "v61". Comparing the leading number is the whole point of
     * [parseLatestRelease]'s tag handling, so pin it.
     */
    @Test
    fun releasesAreComparedNumericallyRatherThanLexicographically() {
        assertThat(parseLatestRelease(release("v9-Sirius"), "61-Achernar")).isEqualTo(UpdateStatus.UpToDate)
        assertThat(parseLatestRelease(release("v100-Rigel"), "61-Achernar"))
            .isInstanceOf(UpdateStatus.Available::class.java)
    }

    /** A codename-only or otherwise unreadable tag must not be reported as a newer release. */
    @Test
    fun anUnparseableTagIsTreatedAsUpToDate() {
        assertThat(parseLatestRelease(release("nightly"), "61-Achernar")).isEqualTo(UpdateStatus.UpToDate)
    }

    /** GitHub allows an untitled release; the tag is the only name left to show. */
    @Test
    fun anUntitledReleaseFallsBackToItsTag() {
        val status = parseLatestRelease(release("v62-Canopus", name = ""), "61-Achernar")

        assertThat((status as UpdateStatus.Available).name).isEqualTo("v62-Canopus")
    }

    private fun release(
        tag: String,
        name: String = "release",
    ) = """
        {
          "tag_name": "$tag",
          "name": "$name",
          "html_url": "https://example.invalid/release"
        }
        """.trimIndent()
}
