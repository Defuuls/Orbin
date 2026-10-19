package com.orbin.ios

import com.orbin.data.sync.SyncException
import com.orbin.data.sync.SyncPasswordStore
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/** A device-only Keychain secret, available to background sync after the first device unlock. */
@OptIn(ExperimentalForeignApi::class)
internal class KeychainSyncPasswordStore(
    private val serviceName: String = "io.github.defuuls.orbin.sync",
) : SyncPasswordStore {
    override suspend fun read(): String? =
        withQuery { query ->
            CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
            memScoped {
                val result = alloc<CFTypeRefVar>()
                result.value = null
                when (val status = SecItemCopyMatching(query, result.ptr)) {
                    errSecItemNotFound -> null
                    errSecSuccess -> {
                        val data = result.value ?: throw SyncException("The sync password couldn't be read")
                        try {
                            val length = CFDataGetLength(data.reinterpret()).toInt()
                            val bytes =
                                if (length == 0) {
                                    byteArrayOf()
                                } else {
                                    CFDataGetBytePtr(data.reinterpret())?.readBytes(length)
                                        ?: throw SyncException("The sync password couldn't be read")
                                }
                            try {
                                runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }
                                    .getOrElse { throw SyncException("The sync password couldn't be decoded", it) }
                            } finally {
                                bytes.fill(0)
                            }
                        } finally {
                            CFRelease(data)
                        }
                    }
                    else -> throw storageFailure(status)
                }
            }
        }

    override suspend fun write(password: String) {
        val bytes = password.encodeToByteArray()
        val data =
            if (bytes.isEmpty()) {
                CFDataCreate(null, null, 0)
            } else {
                bytes.usePinned { CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.convert()) }
            } ?: throw SyncException("The sync password couldn't be stored")
        try {
            withQuery { query ->
                val changes =
                    CFDictionaryCreateMutable(null, 0, null, null)
                        ?: throw SyncException("The sync password couldn't be stored")
                try {
                    CFDictionaryAddValue(changes, kSecValueData, data)
                    CFDictionaryAddValue(changes, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
                    val updated = SecItemUpdate(query, changes)
                    val status =
                        if (updated == errSecItemNotFound) {
                            CFDictionaryAddValue(query, kSecValueData, data)
                            CFDictionaryAddValue(
                                query,
                                kSecAttrAccessible,
                                kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                            )
                            SecItemAdd(query, null)
                        } else {
                            updated
                        }
                    if (status != errSecSuccess) throw storageFailure(status)
                } finally {
                    CFRelease(changes)
                }
            }
        } finally {
            CFRelease(data)
            bytes.fill(0)
        }
    }

    private fun <T> withQuery(block: (platform.CoreFoundation.CFMutableDictionaryRef) -> T): T =
        // With null callbacks the dictionary borrows its values; keep the strings alive through
        // the Security call and release all owned Core Foundation objects afterwards.
        withString(serviceName) { service ->
            withString("webdav-password") { account ->
                val query =
                    CFDictionaryCreateMutable(null, 0, null, null)
                        ?: throw SyncException("The sync password store is unavailable")
                try {
                    CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
                    CFDictionaryAddValue(query, kSecAttrService, service)
                    CFDictionaryAddValue(query, kSecAttrAccount, account)
                    block(query)
                } finally {
                    CFRelease(query)
                }
            }
        }

    private fun <T> withString(
        value: String,
        block: (platform.CoreFoundation.CFStringRef) -> T,
    ): T {
        val string =
            CFStringCreateWithCString(null, value, kCFStringEncodingUTF8)
                ?: throw SyncException("The sync password store is unavailable")
        return try {
            block(string)
        } finally {
            CFRelease(string)
        }
    }

    private fun storageFailure(status: Int): SyncException =
        SyncException("The sync password store is unavailable ($status)")
}
