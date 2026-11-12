package com.orbin.ios

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.Security.SecAccessControlCreateWithFlags
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecAuthFailed
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAccessControlUserPresence
import platform.Security.kSecAttrAccessControl
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecUseAuthenticationContext
import platform.Security.kSecValueData
import kotlin.coroutines.resume

/**
 * Face ID or Touch ID, falling back to the device passcode, through LocalAuthentication, then a
 * Keychain item whose access control requires the same user presence. Android gates a Keystore
 * key the same way. The item is created on the first successful unlock after this version, so
 * existing users are not locked out, and uses [kSecAccessControlUserPresence] (not biometry-only)
 * so a passcode still unlocks the app on a device without Face ID or Touch ID.
 */
class DeviceOwnerAuthenticator : Authenticator {
    override suspend fun authenticate(): AuthResult {
        val evaluated = evaluateOwner()
        if (evaluated !is OwnerAuth.Passed) {
            return AuthResult.Failed(evaluated.message)
        }
        when (AppLockGate.confirm(evaluated.context)) {
            AppLockGate.Status.Verified -> Unit
            AppLockGate.Status.Missing, AppLockGate.Status.Invalidated, AppLockGate.Status.Failed ->
                AppLockGate.install()
        }
        // The owner already passed Face ID, Touch ID or the passcode. A Keychain failure must not
        // lock them out of their own app; the next unlock will try to install the gate again.
        return AuthResult.Success
    }

    private suspend fun evaluateOwner(): OwnerAuth =
        suspendCancellableCoroutine { continuation ->
            val context = LAContext()
            continuation.invokeOnCancellation { context.invalidate() }
            context.evaluatePolicy(
                LAPolicyDeviceOwnerAuthentication,
                localizedReason = UNLOCK_REASON,
            ) { success, error ->
                continuation.resume(
                    if (success) {
                        OwnerAuth.Passed(context)
                    } else {
                        OwnerAuth.Denied(error?.localizedDescription ?: FALLBACK_FAILURE)
                    },
                )
            }
        }
}

private sealed interface OwnerAuth {
    val message: String

    class Passed(
        val context: LAContext,
    ) : OwnerAuth {
        override val message: String = ""
    }

    class Denied(
        override val message: String,
    ) : OwnerAuth
}

/**
 * A this-device-only generic password that can be read only after Face ID, Touch ID or the
 * passcode. [confirm] is called with the already-authenticated [LAContext] so the Keychain does
 * not prompt a second time.
 */
@OptIn(ExperimentalForeignApi::class)
private object AppLockGate {
    enum class Status { Verified, Missing, Invalidated, Failed }

    fun confirm(context: LAContext): Status {
        val held = CFBridgingRetain(context)
        return try {
            withQuery(
                kSecUseAuthenticationContext to held,
                kSecMatchLimit to kSecMatchLimitOne,
            ) { query ->
                when (SecItemCopyMatching(query, null)) {
                    errSecSuccess -> Status.Verified
                    errSecItemNotFound -> Status.Missing
                    errSecAuthFailed -> Status.Invalidated
                    else -> Status.Failed
                }
            } ?: Status.Failed
        } finally {
            held?.let { CFBridgingRelease(it) }
        }
    }

    fun install() {
        withQuery { query -> SecItemDelete(query) }
        add()
    }

    private fun add(): Boolean {
        val access =
            SecAccessControlCreateWithFlags(
                null,
                kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                kSecAccessControlUserPresence,
                null,
            ) ?: return false
        val bytes = GATE_VALUE.encodeToByteArray()
        val data =
            bytes.usePinned { pinned ->
                CFDataCreate(null, pinned.addressOf(0).reinterpret(), bytes.size.toLong())
            }
        return try {
            withQuery(
                kSecAttrAccessControl to access,
                kSecValueData to data,
            ) { attrs ->
                SecItemAdd(attrs, null) == errSecSuccess
            } ?: false
        } finally {
            data?.let { CFRelease(it) }
            CFRelease(access)
        }
    }

    private inline fun <T> withQuery(
        vararg extra: Pair<COpaquePointer?, COpaquePointer?>,
        block: (CFMutableDictionaryRef) -> T,
    ): T? {
        val service = CFStringCreateWithCString(null, SERVICE, kCFStringEncodingUTF8) ?: return null
        val account = CFStringCreateWithCString(null, ACCOUNT, kCFStringEncodingUTF8) ?: return null
        val query = CFDictionaryCreateMutable(null, 0, null, null)
        if (query == null) {
            CFRelease(service)
            CFRelease(account)
            return null
        }
        return try {
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, service)
            CFDictionaryAddValue(query, kSecAttrAccount, account)
            extra.forEach { (key, value) ->
                if (key != null && value != null) CFDictionaryAddValue(query, key, value)
            }
            block(query)
        } finally {
            CFRelease(query)
            CFRelease(service)
            CFRelease(account)
        }
    }

    private const val SERVICE = "io.github.defuuls.orbin.app-lock"
    private const val ACCOUNT = "gate"
    private const val GATE_VALUE = "orbin-app-lock-gate"
}

/** What the Touch ID and passcode prompts say; Face ID shows the app's NSFaceIDUsageDescription. */
private const val UNLOCK_REASON = "Unlock Orbin"

private const val FALLBACK_FAILURE = "Unlock was canceled. Try again."
