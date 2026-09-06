package com.qing.hachimi.data.local

import android.content.Context
import android.content.SharedPreferences
import com.qing.hachimi.util.AppLogger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class DownloadRecord(
    val songId: Long,
    val songName: String,
    val artists: String = "",
    val album: String = "",
    val coverUrl: String = "",
    val qualityLabel: String = "",
    val filePath: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

class DownloadHistoryManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("download_history", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun save(record: DownloadRecord) {
        val list = loadAll().toMutableList()
        val existingIndex = list.indexOfFirst { it.songId == record.songId }
        if (existingIndex >= 0) {
            list[existingIndex] = record
        } else {
            list.add(0, record)
        }
        val encoded = json.encodeToString(list)
        prefs.edit().putString(KEY_HISTORY, encoded).apply()
        AppLogger.debug("DownloadHistory: saved record for songId=${record.songId}, total=${list.size}")
    }

    fun remove(songId: Long) {
        val list = loadAll().toMutableList()
        val removed = list.removeAll { it.songId == songId }
        if (removed) {
            val encoded = json.encodeToString(list)
            prefs.edit().putString(KEY_HISTORY, encoded).apply()
            AppLogger.debug("DownloadHistory: removed songId=$songId, remaining=${list.size}")
        }
    }

    fun loadAll(): List<DownloadRecord> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<DownloadRecord>>(raw)
        } catch (e: Exception) {
            AppLogger.error("DownloadHistory: failed to load", e)
            emptyList()
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_HISTORY).apply()
        AppLogger.debug("DownloadHistory: cleared")
    }

    companion object {
        private const val KEY_HISTORY = "downloads"
    }
}
