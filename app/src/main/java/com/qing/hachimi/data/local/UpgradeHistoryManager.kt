package com.qing.hachimi.data.local

import android.content.Context
import androidx.core.content.edit
import com.qing.hachimi.data.model.UpgradeMode
import com.qing.hachimi.data.model.UpgradeStatus
import com.qing.hachimi.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class UpgradeHistoryManager(private val context: Context) {

    data class UpgradeRecord(
        val originalFile: File,
        val upgradedFile: File,
        val originalQuality: String,
        val upgradedQuality: String,
        val timestamp: Long,
        val status: UpgradeStatus,
        /** 升级方式；老记录没有这个字段，按自动替换处理 */
        val mode: UpgradeMode = UpgradeMode.AUTO_REPLACE
    )

    private val prefs = context.getSharedPreferences("upgrade_history", Context.MODE_PRIVATE)

    fun addRecord(record: UpgradeRecord) {
        val records = getRecords().toMutableList()
        records.add(record)
        saveRecords(records)
    }

    fun getRecords(): List<UpgradeRecord> {
        val json = prefs.getString("records", "[]") ?: "[]"
        val jsonArray = try {
            JSONArray(json)
        } catch (e: Exception) {
            // 整份 JSON 坏了就当没有记录：不能让一条坏数据把扫描和升级页永久锁死
            AppLogger.error("升级历史解析失败，本次按空记录处理", e)
            return emptyList()
        }
        val records = mutableListOf<UpgradeRecord>()

        for (i in 0 until jsonArray.length()) {
            val obj = runCatching { jsonArray.getJSONObject(i) }.getOrNull() ?: continue
            val record = runCatching {
                UpgradeRecord(
                    originalFile = File(obj.getString("originalFile")),
                    upgradedFile = File(obj.getString("upgradedFile")),
                    originalQuality = obj.optString("originalQuality"),
                    upgradedQuality = obj.optString("upgradedQuality"),
                    timestamp = obj.optLong("timestamp"),
                    status = UpgradeStatus.valueOf(obj.getString("status")),
                    mode = runCatching { UpgradeMode.valueOf(obj.optString("mode")) }
                        .getOrDefault(UpgradeMode.AUTO_REPLACE)
                )
            }.getOrElse { error ->
                AppLogger.warn("跳过损坏的升级记录 #$i: ${error.message}")
                null
            }
            record?.let(records::add)
        }

        return records
    }

    fun getRecordsByFolder(folder: File): List<UpgradeRecord> {
        return getRecords().filter {
            it.originalFile.parentFile?.absolutePath == folder.absolutePath
        }
    }

    fun clearRecords() {
        prefs.edit { remove("records") }
    }

    /**
     * 已经升级过的文件路径。
     * 替换模式下原文件被改名成 .backup，实际在用的是新文件；
     * 保留模式下原文件还在，它才是「被升级过」的那一首。
     */
    fun upgradedFilePaths(): Set<String> = getRecords()
        .filter { it.status == UpgradeStatus.COMPLETED }
        .map {
            when (it.mode) {
                UpgradeMode.AUTO_REPLACE -> it.upgradedFile.absolutePath
                else -> it.originalFile.absolutePath
            }
        }
        .toSet()

    fun isUpgraded(file: File): Boolean = file.absolutePath in upgradedFilePaths()

    private fun saveRecords(records: List<UpgradeRecord>) {
        val jsonArray = JSONArray()
        records.forEach { record ->
            val obj = JSONObject().apply {
                put("originalFile", record.originalFile.absolutePath)
                put("upgradedFile", record.upgradedFile.absolutePath)
                put("originalQuality", record.originalQuality)
                put("upgradedQuality", record.upgradedQuality)
                put("timestamp", record.timestamp)
                put("status", record.status.name)
                put("mode", record.mode.name)
            }
            jsonArray.put(obj)
        }

        prefs.edit { putString("records", jsonArray.toString()) }
    }
}
