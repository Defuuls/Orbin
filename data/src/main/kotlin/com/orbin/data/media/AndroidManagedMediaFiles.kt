package com.orbin.data.media

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.orbin.domain.repository.ManagedMediaFiles
import com.orbin.domain.repository.validManagedMediaKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Saved originals are private, authenticated encrypted files; sharing decrypts only into caller staging. */
@Singleton
class AndroidManagedMediaFiles internal constructor(
    private val directory: File,
    private val encryptionKey: () -> SecretKey,
) : ManagedMediaFiles {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(File(context.filesDir, "managed-media"), ::managedMediaKey)

    override suspend fun contains(storageKey: String): Boolean = withContext(Dispatchers.IO) { file(storageKey).isFile }

    override suspend fun size(storageKey: String): Long? =
        withContext(Dispatchers.IO) {
            val source = file(storageKey)
            if (!source.isFile) return@withContext null
            DataInputStream(source.inputStream().buffered()).use { input ->
                check(input.readInt() == MAGIC) { "Invalid saved media" }
                input.readLong().takeIf { it >= 0 }
            }
        }

    override suspend fun importFile(
        storageKey: String,
        sourcePath: String,
    ) = withContext(Dispatchers.IO) {
        val target = file(storageKey)
        val source = File(sourcePath)
        require(source.isFile) { "Saved media source is not a file" }
        directory.mkdirs()
        val pending = File(directory, ".${UUID.randomUUID()}.part")
        try {
            val cipher =
                Cipher
                    .getInstance(
                        "AES/GCM/NoPadding",
                    ).apply { init(Cipher.ENCRYPT_MODE, encryptionKey()) }
            DataOutputStream(pending.outputStream().buffered()).use { output ->
                output.writeInt(MAGIC)
                output.writeLong(source.length())
                output.writeInt(cipher.iv.size)
                output.write(cipher.iv)
                CipherOutputStream(output, cipher).use { encrypted ->
                    source.inputStream().use { it.copyTo(encrypted) }
                }
            }
            // Same-directory rename is atomic, including replacing an imported record's previous bytes.
            java.nio.file.Files.move(
                pending.toPath(),
                target.toPath(),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            pending.delete()
        }
        Unit
    }

    override suspend fun exportFile(
        storageKey: String,
        destinationPath: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val source = file(storageKey)
            if (!source.isFile) return@withContext false
            val target = File(destinationPath)
            require(target.canonicalPath != source.canonicalPath) { "An export cannot replace its encrypted original" }
            target.parentFile?.mkdirs()
            val pending = File(checkNotNull(target.parentFile), ".${UUID.randomUUID()}.part")
            try {
                DataInputStream(source.inputStream().buffered()).use { input ->
                    check(input.readInt() == MAGIC) { "Invalid saved media" }
                    val expectedSize = input.readLong()
                    val ivSize = input.readInt()
                    check(expectedSize >= 0 && ivSize == GCM_IV_BYTES) { "Invalid saved media header" }
                    val iv = ByteArray(ivSize).also(input::readFully)
                    val cipher =
                        Cipher.getInstance("AES/GCM/NoPadding").apply {
                            init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
                        }
                    val written =
                        CipherInputStream(input, cipher).use { decrypted ->
                            pending.outputStream().use { decrypted.copyTo(it) }
                        }
                    check(written == expectedSize) { "Saved media length changed" }
                }
                java.nio.file.Files.move(
                    pending.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                )
                true
            } finally {
                pending.delete()
            }
        }

    override suspend fun remove(storageKey: String) {
        withContext(Dispatchers.IO) {
            check(!file(storageKey).exists() || file(storageKey).delete()) { "Could not remove saved media" }
        }
    }

    private fun file(key: String): File {
        require(validManagedMediaKey(key)) { "Invalid media storage key" }
        return File(directory, key)
    }
}

private fun managedMediaKey(): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
    return synchronized(AndroidManagedMediaFiles::class.java) {
        (store.getKey(KEY_ALIAS, null) as? SecretKey)
            ?: KeyGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply {
                    init(
                        KeyGenParameterSpec
                            .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(MEDIA_KEY_BITS)
                            .build(),
                    )
                }.generateKey()
    }
}

private const val MAGIC = 0x4f524d31
private const val KEY_ALIAS = "orbin-managed-media-v1"

private const val GCM_IV_BYTES = 12
private const val GCM_TAG_BITS = 128
private const val MEDIA_KEY_BITS = 256
