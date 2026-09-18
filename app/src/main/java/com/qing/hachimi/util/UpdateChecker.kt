package com.qing.hachimi.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class UpdateInfo(
    val latestVersion: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val hasUpdate: Boolean,
)

object UpdateChecker {

    private const val API_URL = "https://api.github.com/repos/qingyueyin/Hachimi/releases/latest"
    private val json = Json { ignoreUnknownKeys = true }
    private val coreVersionRegex = Regex("""^\d+(?:\.\d+){0,2}""")

    suspend fun check(client: OkHttpClient, currentVersion: String): UpdateInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(API_URL)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "Hachimi/$currentVersion")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            when (response.code) {
                404 -> throw IllegalStateException("暂未发布正式版本")
                !in 200..299 -> throw IllegalStateException("请求失败: ${response.code}")
            }

            val release = json.decodeFromString<GitHubRelease>(body)
            val latestTag = release.tagName.removePrefix("v").removePrefix("V")
            val hasUpdate = compareVersions(latestTag, currentVersion) > 0
            val apkUrl = release.assets
                .firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?.browserDownloadUrl

            UpdateInfo(
                latestVersion = latestTag,
                releaseNotes = release.body.orEmpty().trim(),
                downloadUrl = apkUrl ?: release.htmlUrl,
                hasUpdate = hasUpdate,
            )
        }
    }

    internal fun coreVersion(raw: String): String {
        val normalized = raw.trim().removePrefix("v").removePrefix("V")
        return coreVersionRegex.find(normalized)?.value ?: normalized.substringBefore("-")
    }

    internal fun compareVersions(a: String, b: String): Int {
        val partsA = coreVersion(a).split(".").map { it.toIntOrNull() ?: 0 }
        val partsB = coreVersion(b).split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val va = partsA.getOrElse(i) { 0 }
            val vb = partsB.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }
}

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
private data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)
