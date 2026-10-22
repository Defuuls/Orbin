package com.orbin.data.sync

import com.orbin.domain.repository.SYNC_SETUP_PREFIX
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

private const val MAX_SETUP_CODE_LENGTH = 4096

private val SetupJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class SyncSetup(
    val folder: String,
    val username: String,
    val password: String,
)

/**
 * The WebDAV sync login as one string, for the "share setup" QR code: the way two devices that are
 * not near each other (on mobile data, say) end up syncing through the reader's own server. It
 * holds the password, so it is only ever shown on screen for the other device's camera.
 */
internal object SyncSetupCode {
    fun encode(account: SyncAccount): String {
        require(account.isUsable) { "Set up WebDAV sync first" }
        val setup = SyncSetup(account.folderUrl, account.username, account.password)
        return SYNC_SETUP_PREFIX + Base64.UrlSafe.encode(SetupJson.encodeToString(setup).encodeToByteArray())
    }

    fun parse(code: String): SyncAccount {
        val trimmed = code.trim()
        require(trimmed.length <= MAX_SETUP_CODE_LENGTH && trimmed.startsWith(SYNC_SETUP_PREFIX)) {
            "Use an Orbin sync setup code"
        }
        val setup =
            SetupJson.decodeFromString<SyncSetup>(
                Base64.UrlSafe.decode(trimmed.removePrefix(SYNC_SETUP_PREFIX)).decodeToString(),
            )
        val account = SyncAccount(setup.folder.trim(), setup.username.trim(), setup.password)
        require(account.isUsable) { "The sync setup code needs an https folder and a login" }
        return account
    }
}
