package com.sergioasenjo.vesperhome.security

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

private val Context.pinSecurityDataStore by preferencesDataStore(name = "pin_security")

enum class PinProtectedArea {
    MEDIA_INTEGRATIONS,
    APP_MANAGEMENT,
    BACKUPS_AND_PROFILES
}

data class PinSecurityState(
    val hasPin: Boolean = false,
    val lockedPackages: Set<String> = emptySet(),
    val protectMediaIntegrations: Boolean = false,
    val protectAppManagement: Boolean = false,
    val protectBackupsAndProfiles: Boolean = false
) {
    fun protects(area: PinProtectedArea): Boolean = hasPin && when (area) {
        PinProtectedArea.MEDIA_INTEGRATIONS -> protectMediaIntegrations
        PinProtectedArea.APP_MANAGEMENT -> protectAppManagement
        PinProtectedArea.BACKUPS_AND_PROFILES -> protectBackupsAndProfiles
    }
}

class PinRepository(context: Context) {
    private val applicationContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val securityState = applicationContext.pinSecurityDataStore.data.map(::toState)
    val state: StateFlow<PinSecurityState> = securityState.stateIn(
        scope,
        SharingStarted.Eagerly,
        PinSecurityState()
    )

    suspend fun current(): PinSecurityState = securityState.first()

    suspend fun setPin(pin: CharArray) = withContext(Dispatchers.Default) {
        try {
            require(pin.size in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all(Char::isDigit))
            val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
            val hash = derive(pin, salt, HASH_ITERATIONS)
            applicationContext.pinSecurityDataStore.edit { preferences ->
                preferences[PIN_SALT] = Base64.encodeToString(salt, Base64.NO_WRAP)
                preferences[PIN_HASH] = Base64.encodeToString(hash, Base64.NO_WRAP)
                preferences[PIN_ITERATIONS] = HASH_ITERATIONS
            }
            hash.fill(0)
        } finally {
            pin.fill('\u0000')
        }
    }

    suspend fun verify(pin: CharArray): Boolean = withContext(Dispatchers.Default) {
        try {
            val preferences = applicationContext.pinSecurityDataStore.data.first()
            val storedHash = preferences[PIN_HASH]?.let(::decode) ?: return@withContext false
            val salt = preferences[PIN_SALT]?.let(::decode) ?: return@withContext false
            val iterations = preferences[PIN_ITERATIONS] ?: HASH_ITERATIONS
            MessageDigest.isEqual(storedHash, derive(pin, salt, iterations))
        } finally {
            pin.fill('\u0000')
        }
    }

    suspend fun clearPin() {
        applicationContext.pinSecurityDataStore.edit { preferences -> preferences.clear() }
    }

    suspend fun toggleAppLock(packageName: String) {
        require(current().hasPin)
        applicationContext.pinSecurityDataStore.edit { preferences ->
            val locked = preferences[LOCKED_PACKAGES].orEmpty().toMutableSet()
            if (!locked.add(packageName)) locked.remove(packageName)
            preferences[LOCKED_PACKAGES] = locked
        }
    }

    suspend fun setProtection(area: PinProtectedArea, enabled: Boolean) {
        require(current().hasPin)
        val key = when (area) {
            PinProtectedArea.MEDIA_INTEGRATIONS -> PROTECT_MEDIA_INTEGRATIONS
            PinProtectedArea.APP_MANAGEMENT -> PROTECT_APP_MANAGEMENT
            PinProtectedArea.BACKUPS_AND_PROFILES -> PROTECT_BACKUPS_AND_PROFILES
        }
        applicationContext.pinSecurityDataStore.edit { preferences -> preferences[key] = enabled }
    }

    private fun toState(preferences: androidx.datastore.preferences.core.Preferences): PinSecurityState =
        PinSecurityState(
            hasPin = preferences[PIN_HASH] != null && preferences[PIN_SALT] != null,
            lockedPackages = preferences[LOCKED_PACKAGES].orEmpty(),
            protectMediaIntegrations = preferences[PROTECT_MEDIA_INTEGRATIONS] ?: false,
            protectAppManagement = preferences[PROTECT_APP_MANAGEMENT] ?: false,
            protectBackupsAndProfiles = preferences[PROTECT_BACKUPS_AND_PROFILES] ?: false
        )

    private fun derive(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        require(iterations in MIN_HASH_ITERATIONS..MAX_HASH_ITERATIONS)
        val keyBytes = String(pin).toByteArray(StandardCharsets.UTF_8)
        val mac = Mac.getInstance(HASH_ALGORITHM).apply {
            init(SecretKeySpec(keyBytes, HASH_ALGORITHM))
        }
        keyBytes.fill(0)
        val firstBlock = ByteBuffer.allocate(salt.size + Int.SIZE_BYTES)
            .put(salt)
            .putInt(1)
            .array()
        var round = mac.doFinal(firstBlock)
        val result = round.copyOf()
        repeat(iterations - 1) {
            round = mac.doFinal(round)
            result.indices.forEach { index ->
                result[index] = (result[index].toInt() xor round[index].toInt()).toByte()
            }
        }
        round.fill(0)
        return result
    }

    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_ITERATIONS = intPreferencesKey("pin_iterations")
        val LOCKED_PACKAGES = stringSetPreferencesKey("locked_packages")
        val PROTECT_MEDIA_INTEGRATIONS = booleanPreferencesKey("protect_media_integrations")
        val PROTECT_APP_MANAGEMENT = booleanPreferencesKey("protect_app_management")
        val PROTECT_BACKUPS_AND_PROFILES = booleanPreferencesKey("protect_backups_and_profiles")
        const val HASH_ALGORITHM = "HmacSHA256"
        const val HASH_ITERATIONS = 120_000
        const val MIN_HASH_ITERATIONS = 100_000
        const val MAX_HASH_ITERATIONS = 1_000_000
        const val SALT_BYTES = 16
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 8
    }
}
