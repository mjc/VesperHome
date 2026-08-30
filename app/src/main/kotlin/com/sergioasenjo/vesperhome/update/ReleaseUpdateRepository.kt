package com.sergioasenjo.vesperhome.update

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class ReleaseUpdate(
    val currentVersion: String,
    val latestVersion: String,
    val title: String,
    val notes: String,
    val publishedAt: Long,
    val updateAvailable: Boolean
)

class ReleaseUpdateRepository(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val currentVersion: String
) {
    suspend fun latest(): ReleaseUpdate = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Vesper-Home")
            .build()
        httpClient.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "GitHub returned ${response.code}" }
            val release = json.decodeFromString<GitHubRelease>(requireNotNull(response.body).string())
            val latestVersion = release.tagName.removePrefix("v")
            ReleaseUpdate(
                currentVersion = currentVersion.substringBefore('-'),
                latestVersion = latestVersion,
                title = release.name.ifBlank { release.tagName },
                notes = release.body,
                publishedAt = parsePublishedAt(release.publishedAt),
                updateAvailable = compareVersions(latestVersion, currentVersion.substringBefore('-')) > 0
            )
        }
    }

    private fun parsePublishedAt(value: String): Long {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }
        return requireNotNull(formatter.parse(value)).time
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = semanticVersion(left)
        val rightParts = semanticVersion(right)
        return leftParts.zip(rightParts)
            .firstOrNull { (leftPart, rightPart) -> leftPart != rightPart }
            ?.let { (leftPart, rightPart) -> leftPart.compareTo(rightPart) }
            ?: 0
    }

    private fun semanticVersion(value: String): List<Int> {
        val parts = value.removePrefix("v").split('.')
        require(parts.size == 3)
        return parts.map(String::toInt)
    }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        val name: String = "",
        val body: String = "",
        @SerialName("published_at") val publishedAt: String
    )

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/sergio-asenjo/VesperHome/releases/latest"
    }
}
