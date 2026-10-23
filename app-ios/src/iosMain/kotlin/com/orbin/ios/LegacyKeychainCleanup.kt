package com.orbin.ios

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Security.SecItemDelete
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword

/**
 * Deletes the Keychain items the removed sync features stored: the WebDAV password and the nearby
 * pairing identity. Deleting an item that isn't there is harmless, so this runs on every launch.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun deleteLegacySyncKeychainItems() {
    LEGACY_SERVICES.forEach { name ->
        val service = CFStringCreateWithCString(null, name, kCFStringEncodingUTF8) ?: return@forEach
        val query = CFDictionaryCreateMutable(null, 0, null, null)
        if (query == null) {
            CFRelease(service)
            return@forEach
        }
        try {
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, service)
            SecItemDelete(query)
        } finally {
            CFRelease(query)
            CFRelease(service)
        }
    }
}

private val LEGACY_SERVICES = listOf("io.github.defuuls.orbin.sync", "io.github.defuuls.orbin.nearby")
