package com.orbin.data.repository

import com.orbin.core.model.UpdateStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateRepositoryParsingTest {
    @Test
    fun higherReleaseNumberIsOfferedAsAnUpdate() {
        val status = parseLatestRelease(release("v62-Canopus", "v62 — Canopus"), "61-Achernar")

        assertIs<UpdateStatus.Available>(status)
        assertEquals("v62-Canopus", status.tag)
        assertEquals("v62 — Canopus", status.name)
        assertEquals("https://example.invalid/release", status.url)
    }

    @Test
    fun theSameReleaseIsUpToDate() {
        assertEquals(UpdateStatus.UpToDate, parseLatestRelease(release("v61-Achernar"), "61-Achernar"))
    }

    @Test
    fun olderPublishedReleaseIsUpToDate() {
        assertEquals(UpdateStatus.UpToDate, parseLatestRelease(release("v60-Vega"), "61-Achernar"))
    }

    @Test
    fun releasesAreComparedNumericallyRatherThanLexicographically() {
        assertEquals(UpdateStatus.UpToDate, parseLatestRelease(release("v9-Sirius"), "61-Achernar"))
        assertIs<UpdateStatus.Available>(parseLatestRelease(release("v100-Rigel"), "61-Achernar"))
    }

    @Test
    fun unparseableTagIsTreatedAsUpToDate() {
        assertEquals(UpdateStatus.UpToDate, parseLatestRelease(release("nightly"), "61-Achernar"))
    }

    @Test
    fun untitledReleaseFallsBackToItsTag() {
        val status = parseLatestRelease(release("v62-Canopus", name = ""), "61-Achernar")
        assertIs<UpdateStatus.Available>(status)
        assertEquals("v62-Canopus", status.name)
    }

    @Test
    fun releaseApkAndItsChecksumAreFound() {
        val status =
            parseLatestRelease(
                release(
                    "v149-Orange",
                    assets =
                        listOf(
                            "orbin-v149-Orange-mapping.txt",
                            "orbin-v149-Orange-mapping.txt.sha256",
                            "orbin-v149-Orange.apk",
                            "orbin-v149-Orange.apk.sha256",
                        ),
                ),
                "148-Nectarine",
            )
        assertIs<UpdateStatus.Available>(status)
        assertEquals("$DOWNLOADS/v149-Orange/orbin-v149-Orange.apk", status.apkUrl)
        assertEquals("$DOWNLOADS/v149-Orange/orbin-v149-Orange.apk.sha256", status.checksumUrl)
        assertTrue(status.installable)
    }

    @Test
    fun apkWithoutChecksumIsNotInstallable() {
        val status =
            parseLatestRelease(release("v149-Orange", assets = listOf("orbin-v149-Orange.apk")), "148-Nectarine")
        assertIs<UpdateStatus.Available>(status)
        assertNotNull(status.apkUrl)
        assertFalse(status.installable)
    }

    @Test
    fun downloadsFromElsewhereAreIgnored() {
        val status =
            parseLatestRelease(
                release(
                    "v149-Orange",
                    assets = listOf("orbin-v149-Orange.apk", "orbin-v149-Orange.apk.sha256"),
                    downloads = "https://example.invalid/releases/download",
                ),
                "148-Nectarine",
            )
        assertIs<UpdateStatus.Available>(status)
        assertNull(status.apkUrl)
        assertFalse(status.installable)
    }

    @Test
    fun realWorldGitHubPayloadWithExtraFieldsDeserializesCorrectly() {
        val realPayload =
            """
            {
              "url": "https://api.github.com/repos/Defuuls/Orbin/releases/12345",
              "assets_url": "https://api.github.com/repos/Defuuls/Orbin/releases/12345/assets",
              "upload_url": "https://uploads.github.com/repos/Defuuls/Orbin/releases/12345/assets{?name,label}",
              "html_url": "https://github.com/Defuuls/Orbin/releases/tag/v150-Peach",
              "id": 12345,
              "author": {
                "login": "octocat",
                "id": 1,
                "node_id": "MDQ6VXNlcjE=",
                "avatar_url": "https://github.com/images/error/octocat_happy.gif",
                "gravatar_id": "",
                "url": "https://api.github.com/users/octocat",
                "html_url": "https://github.com/octocat",
                "type": "User",
                "site_admin": false
              },
              "node_id": "MDc6UmVsZWFzZTE=",
              "tag_name": "v150-Peach",
              "target_commitish": "main",
              "name": null,
              "draft": false,
              "prerelease": false,
              "created_at": "2026-10-01T12:00:00Z",
              "published_at": "2026-10-01T12:30:00Z",
              "assets": [
                {
                  "url": "https://api.github.com/repos/Defuuls/Orbin/releases/assets/1",
                  "id": 1,
                  "node_id": "MDEyOlJlbGVhc2VBc3NldDE=",
                  "name": "orbin-v150-Peach.apk",
                  "label": "",
                  "uploader": { "login": "octocat" },
                  "content_type": "application/vnd.android.package-archive",
                  "state": "uploaded",
                  "size": 1024,
                  "download_count": 5,
                  "created_at": "2026-10-01T12:00:00Z",
                  "updated_at": "2026-10-01T12:00:00Z",
                  "browser_download_url": "https://github.com/Defuuls/Orbin/releases/download/v150-Peach/orbin-v150-Peach.apk"
                },
                {
                  "url": "https://api.github.com/repos/Defuuls/Orbin/releases/assets/2",
                  "id": 2,
                  "node_id": "MDEyOlJlbGVhc2VBc3NldDI=",
                  "name": "orbin-v150-Peach.apk.sha256",
                  "label": null,
                  "uploader": { "login": "octocat" },
                  "content_type": "text/plain",
                  "state": "uploaded",
                  "size": 64,
                  "download_count": 2,
                  "created_at": "2026-10-01T12:00:00Z",
                  "updated_at": "2026-10-01T12:00:00Z",
                  "browser_download_url": "https://github.com/Defuuls/Orbin/releases/download/v150-Peach/orbin-v150-Peach.apk.sha256"
                }
              ],
              "tarball_url": "https://api.github.com/repos/Defuuls/Orbin/tarball/v150-Peach",
              "zipball_url": "https://api.github.com/repos/Defuuls/Orbin/zipball/v150-Peach",
              "body": "## Changelog\n* Fix stuff"
            }
            """.trimIndent()

        val status = parseLatestRelease(realPayload, "149-Orange")
        assertIs<UpdateStatus.Available>(status)
        assertEquals("v150-Peach", status.tag)
        assertEquals("v150-Peach", status.name)
        assertEquals("https://github.com/Defuuls/Orbin/releases/tag/v150-Peach", status.url)
        assertEquals(
            "https://github.com/Defuuls/Orbin/releases/download/v150-Peach/orbin-v150-Peach.apk",
            status.apkUrl,
        )
        assertEquals(
            "https://github.com/Defuuls/Orbin/releases/download/v150-Peach/orbin-v150-Peach.apk.sha256",
            status.checksumUrl,
        )
        assertTrue(status.installable)
    }

    private fun release(
        tag: String,
        name: String = "release",
        assets: List<String> = emptyList(),
        downloads: String = DOWNLOADS,
    ): String {
        val assetJson =
            assets.joinToString { "{\"name\": \"$it\", \"browser_download_url\": \"$downloads/$tag/$it\"}" }
        return """
            {
              "tag_name": "$tag",
              "name": "$name",
              "html_url": "https://example.invalid/release",
              "assets": [$assetJson]
            }
            """.trimIndent()
    }

    private companion object {
        const val DOWNLOADS = "https://github.com/Defuuls/Orbin/releases/download"
    }
}
