package com.qing.hachimi.data.local

import android.content.Context
import com.qing.hachimi.data.model.UpgradeStatus
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
        val status: UpgradeStatus
    )

    private val prefs = context.getSharedPreferences("upgrade_history", Context.MODE_PRIVATE)

    fun addRecord(record: UpgradeRecord) {
        val records = getRecords().toMutableList()
        records.add(record)
        saveRecords(records)
    }

    fun getRecords(): List<UpgradeRecord> {
        val json = prefs.getString("records", "[]") ?: "[]"
        val jsonArray = JSONArray(json)
        val records = mutableListOf<UpgradeRecord>()

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            records.add(
                UpgradeRecord(
                    originalFile = File(obj.getString("originalFile")),
                    upgradedFile = File(obj.getString("upgradedFile")),
                    originalQuality = obj.getString("originalQuality"),
                    upgradedQuality = obj.getString("upgradedQuality"),
                    timestamp = obj.getLong("timestamp"),
                    status = UpgradeStatus.valueOf(obj.getString("status"))
                )
            )
        }

        return records
    }

    fun getRecordsByFolder(folder: File): List<UpgradeRecord> {
        return getRecords().filter {
            it.originalFile.parentFile?.absolutePath == folder.absolutePath
        }
    }

    fun clearRecords() {
        prefs.edit().remove("records").apply()
    }

    fun isUpgraded(file: File): Boolean {
        return getRecords().any {
            it.originalFile.absolutePath == file.absolutePath &&
                it.status == UpgradeStatus.COMPLETED
        }
    }

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
            }
            jsonArray.put(obj)
        }

        prefs.edit().putString("records", jsonArray.toString()).apply()
    }
}
