package com.orbin.data.notification

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * A random per-install secret that thread notifications put on their launch intent.
 *
 * MainActivity is exported (it is the launcher), so any app on the device can start it with the
 * thread extras. Only an intent carrying this value, which lives in app-private prefs that other
 * apps cannot read, is treated as a notification tap; anything else just opens the app.
 */
object NotificationLaunchToken {
    const val EXTRA_TOKEN = "com.orbin.extra.NOTIFICATION_TOKEN"

    private const val PREFS_NAME = "orbin_notification_token"
    private const val KEY_TOKEN = "token"
    private const val TOKEN_BYTES = 32
    private val lock = Any()

    fun get(context: Context): String =
        synchronized(lock) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_TOKEN, null) ?: run {
                val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
                Base64
                    .encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
                    .also { prefs.edit().putString(KEY_TOKEN, it).commit() }
            }
        }

    fun matches(
        context: Context,
        candidate: String?,
    ): Boolean =
        candidate != null &&
            MessageDigest.isEqual(candidate.encodeToByteArray(), get(context).encodeToByteArray())
}
