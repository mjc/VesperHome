package com.sergioasenjo.vesperhome.profiles

import android.content.Context
import android.util.AtomicFile
import com.sergioasenjo.vesperhome.R
import com.sergioasenjo.vesperhome.backup.BackupRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class LayoutProfile(val id: String, val name: String, val createdAt: Long, val updatedAt: Long)

data class ProfileState(
    val profiles: List<LayoutProfile> = emptyList(),
    val activeProfileId: String? = null,
    val loading: Boolean = true
) {
    val activeProfile: LayoutProfile?
        get() = profiles.firstOrNull { it.id == activeProfileId }
}

class ProfileRepository(context: Context, private val backupRepository: BackupRepository, private val json: Json) {
    private val profileDirectory = File(context.applicationContext.filesDir, PROFILE_DIRECTORY)
    private val defaultProfileName = context.getString(R.string.default_profile)
    private val registryFile = AtomicFile(File(profileDirectory, REGISTRY_FILE))
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = mutableState

    suspend fun ensureInitialized() = withContext(Dispatchers.IO) {
        mutex.withLock { mutableState.value = loadOrCreateRegistry().toState() }
    }

    suspend fun create(name: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val registry = loadOrCreateRegistry()
            val cleanName = validateName(name, registry.profiles)
            saveActiveState(registry)
            val now = System.currentTimeMillis()
            val profile = StoredProfile(UUID.randomUUID().toString(), cleanName, now, now)
            backupRepository.saveProfileState(profileFile(profile.id))
            val updated = registry.copy(activeProfileId = profile.id, profiles = registry.profiles + profile)
            writeRegistry(updated)
            mutableState.value = updated.toState()
        }
    }

    suspend fun switchTo(profileId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val registry = loadOrCreateRegistry()
            if (registry.activeProfileId == profileId) return@withLock
            val target = requireNotNull(registry.profiles.firstOrNull { it.id == profileId })
            val previous = requireNotNull(registry.profiles.firstOrNull { it.id == registry.activeProfileId })
            saveActiveState(registry)
            backupRepository.restoreProfileState(profileFile(target.id))
            val now = System.currentTimeMillis()
            val updated = registry.copy(
                activeProfileId = target.id,
                profiles = registry.profiles.map { profile ->
                    if (profile.id == target.id) profile.copy(updatedAt = now) else profile
                }
            )
            try {
                writeRegistry(updated)
            } catch (error: Exception) {
                backupRepository.restoreProfileState(profileFile(previous.id))
                throw error
            }
            mutableState.value = updated.toState()
        }
    }

    suspend fun rename(profileId: String, name: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val registry = loadOrCreateRegistry()
            require(registry.profiles.any { it.id == profileId })
            val cleanName = validateName(name, registry.profiles.filterNot { it.id == profileId })
            val updated = registry.copy(
                profiles = registry.profiles.map { profile ->
                    if (profile.id == profileId) profile.copy(name = cleanName) else profile
                }
            )
            writeRegistry(updated)
            mutableState.value = updated.toState()
        }
    }

    suspend fun delete(profileId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val registry = loadOrCreateRegistry()
            require(profileId != registry.activeProfileId && registry.profiles.size > 1)
            require(registry.profiles.any { it.id == profileId })
            val updated = registry.copy(profiles = registry.profiles.filterNot { it.id == profileId })
            writeRegistry(updated)
            profileFile(profileId).delete()
            mutableState.value = updated.toState()
        }
    }

    suspend fun reload() = withContext(Dispatchers.IO) {
        mutex.withLock { mutableState.value = loadOrCreateRegistry().toState() }
    }

    private suspend fun saveActiveState(registry: ProfileRegistry) {
        val active = requireNotNull(registry.profiles.firstOrNull { it.id == registry.activeProfileId })
        backupRepository.saveProfileState(profileFile(active.id))
    }

    private suspend fun loadOrCreateRegistry(): ProfileRegistry {
        readRegistry()?.let { return it }
        profileDirectory.mkdirs()
        val now = System.currentTimeMillis()
        val profile = StoredProfile(UUID.randomUUID().toString(), defaultProfileName, now, now)
        backupRepository.saveProfileState(profileFile(profile.id))
        return ProfileRegistry(activeProfileId = profile.id, profiles = listOf(profile)).also(::writeRegistry)
    }

    private fun readRegistry(): ProfileRegistry? = runCatching {
        if (!registryFile.baseFile.isFile) return null
        val registry = registryFile.openRead().bufferedReader().use { reader ->
            json.decodeFromString<ProfileRegistry>(reader.readText())
        }
        require(registry.version == PROFILE_FORMAT_VERSION)
        require(registry.profiles.isNotEmpty() && registry.profiles.size <= MAX_PROFILES)
        require(registry.profiles.map(StoredProfile::id).distinct().size == registry.profiles.size)
        require(registry.profiles.map { it.name.lowercase() }.distinct().size == registry.profiles.size)
        require(registry.activeProfileId in registry.profiles.map(StoredProfile::id))
        registry.profiles.forEach { profile ->
            require(profile.id.matches(PROFILE_ID_PATTERN))
            require(profile.name.isNotBlank() && profile.name.length <= MAX_PROFILE_NAME_LENGTH)
            require(profileFile(profile.id).isFile)
        }
        registry
    }.getOrNull()

    private fun writeRegistry(registry: ProfileRegistry) {
        profileDirectory.mkdirs()
        val output = registryFile.startWrite()
        try {
            output.write(json.encodeToString(registry).toByteArray(Charsets.UTF_8))
            registryFile.finishWrite(output)
        } catch (error: Exception) {
            registryFile.failWrite(output)
            throw error
        }
    }

    private fun validateName(name: String, profiles: List<StoredProfile>): String {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty() && cleanName.length <= MAX_PROFILE_NAME_LENGTH)
        require(profiles.none { it.name.equals(cleanName, ignoreCase = true) })
        require(profiles.size < MAX_PROFILES)
        return cleanName
    }

    private fun profileFile(profileId: String): File = File(profileDirectory, "$profileId.vesperprofile")

    private fun ProfileRegistry.toState(): ProfileState = ProfileState(
        profiles = profiles.map { LayoutProfile(it.id, it.name, it.createdAt, it.updatedAt) },
        activeProfileId = activeProfileId,
        loading = false
    )

    @Serializable
    private data class ProfileRegistry(
        val version: Int = PROFILE_FORMAT_VERSION,
        val activeProfileId: String,
        val profiles: List<StoredProfile>
    )

    @Serializable
    private data class StoredProfile(val id: String, val name: String, val createdAt: Long, val updatedAt: Long)

    private companion object {
        const val PROFILE_DIRECTORY = "profiles"
        const val REGISTRY_FILE = "registry.json"
        const val PROFILE_FORMAT_VERSION = 1
        const val MAX_PROFILE_NAME_LENGTH = 40
        const val MAX_PROFILES = 12
        val PROFILE_ID_PATTERN = Regex("[a-f0-9-]+")
    }
}
