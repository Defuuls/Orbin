package com.orbin.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator

/**
 * A settings-specific marker outside the writable app files. The database's encryption key is
 * unsuitable: opening the database can create it before a fresh install reads any settings.
 * The old app-lock alias also identifies an existing locked install during the first upgrade.
 */
internal object SettingsInitializationMarker {
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "orbin_settings_initialized"
    private const val LEGACY_LOCK_ALIAS = "orbin_app_lock_key"

    fun exists(): Boolean = keyStore().let { it.containsAlias(ALIAS) || it.containsAlias(LEGACY_LOCK_ALIAS) }

    @Synchronized
    fun create() {
        if (keyStore().containsAlias(ALIAS)) return
        KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply {
                init(
                    KeyGenParameterSpec
                        .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build(),
                )
            }.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
}
