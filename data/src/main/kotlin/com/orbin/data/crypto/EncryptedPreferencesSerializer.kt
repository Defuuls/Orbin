package com.orbin.data.crypto

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.InputStream
import java.io.OutputStream

/**
 * A [Serializer] that stores DataStore [Preferences] as a Keystore-encrypted JSON document (see
 * [LocalDataCipher]). Each entry is tagged with its value type so it round-trips through the same
 * typed key it was written with, and plaintext never touches disk.
 *
 * Only the value types the app actually persists are supported; an unexpected type fails fast
 * rather than silently dropping data.
 */
internal class EncryptedPreferencesSerializer(
    private val encrypt: (ByteArray) -> ByteArray = LocalDataCipher::encrypt,
    private val decrypt: (ByteArray) -> ByteArray = LocalDataCipher::decrypt,
    private val wasInitialized: () -> Boolean = SettingsInitializationMarker::exists,
    private val markInitialized: () -> Unit = SettingsInitializationMarker::create,
) : Serializer<Preferences> {
    // DataStore uses defaultValue for a missing file too. A Keystore marker distinguishes a
    // deleted settings file from a new install; a lookup failure must never turn the lock off.
    override val defaultValue: Preferences
        get() = if (runCatching(wasInitialized).getOrDefault(true)) lockedRecoveryPreferences() else emptyPreferences()

    override suspend fun readFrom(input: InputStream): Preferences {
        val blob = input.readBytes()
        return runCatching {
            markInitialized()
            require(blob.isNotEmpty()) { "Empty encrypted settings" }
            val json = decrypt(blob).decodeToString()
            val root = Json.parseToJsonElement(json).jsonObject
            val prefs = mutablePreferencesOf()
            root.forEach { (name, element) -> prefs.putEntry(name, element.jsonObject) }
            prefs
        }.getOrElse {
            if (it is CancellationException) throw it
            // Undecryptable or malformed settings blob (lost/rotated Keystore key, a write
            // interrupted mid-flight, corrupt JSON). DataStore's corruption handler only engages
            // for CorruptionException, so translate any failure here into one instead of letting
            // it crash every collector of dataStore.data.
            throw CorruptionException("Unable to decrypt or parse settings", it)
        }
    }

    override suspend fun writeTo(
        t: Preferences,
        output: OutputStream,
    ) {
        val bytes = Json.encodeToString(JsonObject.serializer(), encode(t)).encodeToByteArray()
        // Record initialization before writing so deleting or truncating the file cannot make
        // the next launch look like a new install. This marker contains no settings or secrets.
        markInitialized()
        output.write(encrypt(bytes))
    }

    private fun MutablePreferences.putEntry(
        name: String,
        entry: JsonObject,
    ) {
        val raw = entry.getValue(VALUE)
        when (val type = entry.getValue(TYPE).jsonPrimitive.content) {
            TYPE_BOOLEAN -> set(booleanPreferencesKey(name), raw.jsonPrimitive.content.toBoolean())
            TYPE_INT -> set(intPreferencesKey(name), raw.jsonPrimitive.content.toInt())
            TYPE_LONG -> set(longPreferencesKey(name), raw.jsonPrimitive.content.toLong())
            TYPE_FLOAT -> set(floatPreferencesKey(name), raw.jsonPrimitive.content.toFloat())
            TYPE_DOUBLE -> set(doublePreferencesKey(name), raw.jsonPrimitive.content.toDouble())
            TYPE_STRING -> set(stringPreferencesKey(name), raw.jsonPrimitive.content)
            TYPE_STRING_SET -> {
                val values = raw.jsonArray.map { it.jsonPrimitive.content }.toSet()
                set(stringSetPreferencesKey(name), values)
            }
            else -> error("Unsupported preference type '$type' for '$name'")
        }
    }

    private fun encode(preferences: Preferences): JsonObject =
        buildJsonObject {
            preferences.asMap().forEach { (key, value) ->
                putJsonObject(key.name) { encodeValue(key.name, value) }
            }
        }

    private fun JsonObjectBuilder.encodeValue(
        name: String,
        value: Any,
    ) {
        when (value) {
            is Boolean -> {
                put(TYPE, TYPE_BOOLEAN)
                put(VALUE, value)
            }
            is Int -> {
                put(TYPE, TYPE_INT)
                put(VALUE, value)
            }
            is Long -> {
                put(TYPE, TYPE_LONG)
                put(VALUE, value)
            }
            is Float -> {
                put(TYPE, TYPE_FLOAT)
                put(VALUE, value)
            }
            is Double -> {
                put(TYPE, TYPE_DOUBLE)
                put(VALUE, value)
            }
            is String -> {
                put(TYPE, TYPE_STRING)
                put(VALUE, value)
            }
            is Set<*> -> {
                put(TYPE, TYPE_STRING_SET)
                putJsonArray(VALUE) { value.forEach { add(it.toString()) } }
            }
            else -> error("Unsupported preference type for '$name'")
        }
    }

    private companion object {
        const val TYPE = "t"
        const val VALUE = "v"
        const val TYPE_BOOLEAN = "b"
        const val TYPE_INT = "i"
        const val TYPE_LONG = "l"
        const val TYPE_FLOAT = "f"
        const val TYPE_DOUBLE = "d"
        const val TYPE_STRING = "s"
        const val TYPE_STRING_SET = "ss"
    }
}

/** Unknown lock policy is recovered locked, including the onboarding condition in MainActivity. */
internal fun lockedRecoveryPreferences(): Preferences =
    mutablePreferencesOf(
        booleanPreferencesKey("biometric_lock") to true,
        booleanPreferencesKey("onboarding_completed") to true,
    )
