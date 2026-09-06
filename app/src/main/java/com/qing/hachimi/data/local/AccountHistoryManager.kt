@file:Suppress("DEPRECATION")

package com.qing.hachimi.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.qing.hachimi.util.AppLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * 历史账号信息
 */
@Serializable
data class AccountHistory(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String = "",
    val lastLoginTime: Long = System.currentTimeMillis(),
    val cookieData: Map<String, String> = emptyMap()
)

/**
 * 管理浏览器登录的历史账号列表
 */
class AccountHistoryManager(context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
    private val storage = openStorage(context)
    private val prefs = storage.prefs

    init {
        migrateLegacyHistory()
    }

    companion object {
        private const val KEY_ACCOUNTS = "accounts"
        private const val MAX_ACCOUNTS = 5 // 最多保存5个历史账号
        private const val LEGACY_PREFS_NAME = "account_history"
        private const val SECURE_PREFS_NAME = "account_history_secure"
        private const val METADATA_PREFS_NAME = "account_history_metadata"
    }

    private data class Storage(
        val prefs: SharedPreferences,
        val storesCredentials: Boolean
    )

    /**
     * 获取所有历史账号（按最后登录时间倒序）
     */
    fun getAccounts(): List<AccountHistory> {
        val jsonStr = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<AccountHistory>>(jsonStr)
                .sortedByDescending { it.lastLoginTime }
        } catch (e: Exception) {
            AppLogger.error("Failed to parse account history", e)
            emptyList()
        }
    }

    /**
     * 保存账号到历史记录
     */
    fun saveAccount(account: AccountHistory) {
        val accounts = getAccounts().toMutableList()

        // 移除旧的同用户记录
        accounts.removeAll { it.userId == account.userId }

        // 添加到列表开头
        val storedAccount = account.copy(
            lastLoginTime = System.currentTimeMillis(),
            cookieData = if (storage.storesCredentials) account.cookieData else emptyMap()
        )
        accounts.add(0, storedAccount)

        // 限制最大数量
        val limited = accounts.take(MAX_ACCOUNTS)

        prefs.edit {
            putString(KEY_ACCOUNTS, json.encodeToString(limited))
        }
        AppLogger.info("[AccountHistory] Saved account: userId=${account.userId}, nickname=${account.nickname}")
    }

    /**
     * 删除指定账号
     */
    fun deleteAccount(userId: Long) {
        val accounts = getAccounts().toMutableList()
        accounts.removeAll { it.userId == userId }
        prefs.edit {
            putString(KEY_ACCOUNTS, json.encodeToString(accounts))
        }
        AppLogger.info("[AccountHistory] Deleted account: userId=$userId")
    }

    /**
     * 清空所有历史账号
     */
    fun clearAll() {
        prefs.edit {
            remove(KEY_ACCOUNTS)
        }
        AppLogger.info("[AccountHistory] Cleared all accounts")
    }

    /**
     * 根据用户ID获取账号
     */
    fun getAccount(userId: Long): AccountHistory? {
        return getAccounts().firstOrNull { it.userId == userId }
    }

    private fun openStorage(context: Context): Storage {
        val encryptedPrefs = runCatching {
            createEncryptedPrefs(context)
        }.recoverCatching { firstError ->
            AppLogger.warn("Failed to open encrypted account history, recreating: ${firstError.message}")
            context.deleteSharedPreferences(SECURE_PREFS_NAME)
            createEncryptedPrefs(context)
        }.getOrNull()

        return if (encryptedPrefs != null) {
            Storage(encryptedPrefs, storesCredentials = true)
        } else {
            AppLogger.error("Encrypted account history unavailable; storing metadata only")
            Storage(
                context.getSharedPreferences(METADATA_PREFS_NAME, Context.MODE_PRIVATE),
                storesCredentials = false
            )
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            SECURE_PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun migrateLegacyHistory() {
        val legacyJson = legacyPrefs.getString(KEY_ACCOUNTS, null) ?: return
        val migrated = if (prefs.getString(KEY_ACCOUNTS, null) == null) {
            val valueToStore = if (storage.storesCredentials) {
                legacyJson
            } else {
                runCatching {
                    val accounts = json.decodeFromString<List<AccountHistory>>(legacyJson)
                        .map { it.copy(cookieData = emptyMap()) }
                    json.encodeToString(accounts)
                }.getOrDefault("[]")
            }
            prefs.edit().putString(KEY_ACCOUNTS, valueToStore).commit()
        } else {
            true
        }
        if (migrated) {
            legacyPrefs.edit().remove(KEY_ACCOUNTS).commit()
            AppLogger.info("Migrated account history out of plain storage")
        } else {
            AppLogger.error("Account history migration failed; legacy data retained")
        }
    }
}
