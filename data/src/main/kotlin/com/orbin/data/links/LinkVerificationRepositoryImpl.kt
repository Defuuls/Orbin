package com.orbin.data.links

import com.orbin.core.common.dispatchers.Dispatcher
import com.orbin.core.common.dispatchers.OrbinDispatcher
import com.orbin.core.model.LinkStatus
import com.orbin.domain.repository.LinkVerificationRepository
import com.orbin.domain.repository.SettingsRepository
import com.orbin.network.di.BaseOkHttp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks file-host links against their hosts' public lookup endpoints:
 * - **gofile.io** — the contents API, authenticated with a cached guest-account token.
 * - **mega.nz** — the `cs` API, which answers handle lookups without needing the decryption key.
 * - **fast-file.ru** — a plain page fetch, since the host has no API.
 *
 * Verdicts are cached for the process lifetime; [LinkStatus.UNKNOWN] results are not cached so a
 * transient network failure can be retried the next time the link scrolls into view.
 */
@Singleton
class LinkVerificationRepositoryImpl
    @Inject
    constructor(
        @BaseOkHttp okHttpClient: OkHttpClient,
        private val settingsRepository: SettingsRepository,
        @Dispatcher(OrbinDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
    ) : LinkVerificationRepository {
        private val client =
            okHttpClient
                .newBuilder()
                .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()

        private val cache = ConcurrentHashMap<String, LinkStatus>()
        private val inFlight = Mutex()
        private var gofileGuestToken: String? = null

        override suspend fun verify(url: String): LinkStatus {
            if (!settingsRepository.settings.first().verifyFileHostLinks) return LinkStatus.UNKNOWN
            val link = FileHostLink.parse(url) ?: return LinkStatus.UNKNOWN
            cache[url]?.let { return it }
            return withContext(ioDispatcher) {
                // One probe at a time: link checks are a background nicety and must never
                // compete with catalog/media traffic or hammer the file hosts from a busy thread.
                inFlight.withLock {
                    cache[url] ?: probe(link).also { status ->
                        if (status != LinkStatus.UNKNOWN) cache[url] = status
                    }
                }
            }
        }

        private fun probe(link: FileHostLink): LinkStatus =
            runCatching {
                when (link) {
                    is FileHostLink.GoFile -> probeGoFile(link)
                    is FileHostLink.Mega -> probeMega(link)
                    is FileHostLink.FastFile -> probeFastFile(link)
                }
            }.getOrDefault(LinkStatus.UNKNOWN)

        private fun probeMega(link: FileHostLink.Mega): LinkStatus {
            val payload =
                if (link.isFolder) {
                    """[{"a":"f","c":1,"r":0}]"""
                } else {
                    """[{"a":"g","p":"${link.handle}"}]"""
                }
            val url =
                if (link.isFolder) {
                    "$MEGA_API_URL?id=0&n=${link.handle}"
                } else {
                    "$MEGA_API_URL?id=0"
                }
            val request =
                Request
                    .Builder()
                    .url(url)
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            return execute(request) { _, body -> LinkProbeResponses.megaStatus(body) }
        }

        private fun probeGoFile(link: FileHostLink.GoFile): LinkStatus {
            val token = gofileGuestToken ?: fetchGofileGuestToken() ?: return LinkStatus.UNKNOWN
            val request =
                Request
                    .Builder()
                    .url("$GOFILE_API_URL/contents/${link.contentId}?wt=$GOFILE_WEBSITE_TOKEN")
                    .header("Authorization", "Bearer $token")
                    .build()
            return execute(request) { _, body -> LinkProbeResponses.gofileStatus(body) }
        }

        /** gofile requires an account token even for public lookups; a guest account suffices. */
        private fun fetchGofileGuestToken(): String? {
            val request =
                Request
                    .Builder()
                    .url("$GOFILE_API_URL/accounts")
                    .post(ByteArray(0).toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            val token =
                runCatching {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@runCatching null
                        val root = Json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                        (root["data"] as? JsonObject)?.get("token")?.jsonPrimitive?.content
                    }
                }.getOrNull()
            gofileGuestToken = token
            return token
        }

        private fun probeFastFile(link: FileHostLink.FastFile): LinkStatus {
            val request = Request.Builder().url(link.url).build()
            return execute(request) { code, body -> LinkProbeResponses.fastFileStatus(code, body) }
        }

        private inline fun execute(
            request: Request,
            interpret: (code: Int, body: String) -> LinkStatus,
        ): LinkStatus =
            try {
                client.newCall(request).execute().use { response ->
                    interpret(response.code, response.peekBody(MAX_BODY_BYTES).string())
                }
            } catch (_: IOException) {
                LinkStatus.UNKNOWN
            }

        private companion object {
            const val MEGA_API_URL = "https://g.api.mega.co.nz/cs"
            const val GOFILE_API_URL = "https://api.gofile.io"

            /** Static token gofile's own web app sends with content lookups. */
            const val GOFILE_WEBSITE_TOKEN = "4fd6sg89d7s6"

            const val CALL_TIMEOUT_SECONDS = 10L
            const val MAX_BODY_BYTES = 64L * 1024
            val JSON_MEDIA_TYPE = "application/json".toMediaType()
        }
    }
