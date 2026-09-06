@file:Suppress("DEPRECATION")
package com.qing.hachimi.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.qing.hachimi.util.AppLogger
import kotlinx.coroutines.CompletableDeferred

class CookieManager(context: Context) {

    private val legacyPrefs: SharedPreferences =
        context.getSharedPreferences("hachimi_prefs", Context.MODE_PRIVATE)
    private val prefs: SharedPreferences = openEncryptedPrefs(context)

    // Token readiness tracking to prevent race conditions
    private var _tokenReady = CompletableDeferred<Unit>()
    val tokenReady: CompletableDeferred<Unit> get() = _tokenReady
    private var _tokenInitStarted = false

    companion object {
        private const val TAG = "CookieManager"
        val KEY_COOKIES = setOf("MUSIC_U", "__csrf", "MUSIC_A", "NMTID", "WEVNSM", "WNMCID")
        private val ALLOWED_COOKIE_KEYS = KEY_COOKIES + setOf(
            "os", "osver", "deviceId", "appver", "versioncode", "mobilename",
            "buildver", "resolution", "channel"
        )
        private val COOKIE_NAME_REGEX = Regex("^[!#${'$'}%&'*+.^_`|~0-9A-Za-z-]+${'$'}")
        private const val REFRESH_INTERVAL_MS = 3L * 24 * 60 * 60 * 1000
        private const val EXPIRE_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
    }

    init {
        migrateLegacyCookiesIfNeeded()
    }

    fun saveCookies(cookies: Map<String, String>) {
        val savedKeys = cookies.keys.filter { it in KEY_COOKIES }
        AppLogger.info("[Cookie] Saving ${cookies.size} cookies, key cookies: $savedKeys")
        AppLogger.debug("[Cookie] MUSIC_U present=${!cookies["MUSIC_U"].isNullOrBlank()}, csrf present=${!cookies["__csrf"].isNullOrBlank()}, os=${cookies["os"]}, appver=${cookies["appver"]}")
        prefs.edit().apply {
            cookies.forEach { (k, v) ->
                putString("ck_$k", v)
                putString("ck_backup_$k", v)
            }
            putStringSet("cookie_keys", cookies.keys)
            putLong("cookie_time", System.currentTimeMillis())
            apply()
        }
        AppLogger.info("[Cookie] Saved successfully")
    }

    fun getCookies(): Map<String, String> {
        val keys = prefs.getStringSet("cookie_keys", emptySet()) ?: return run {
            AppLogger.warn("[Cookie] getCookies: no keys stored")
            emptyMap()
        }
        val result = keys.mapNotNull { k ->
            val v = prefs.getString("ck_$k", null)
            v?.let { k to it }
        }.toMap()
        val musicULen = result["MUSIC_U"]?.length ?: 0
        AppLogger.debug("[Cookie] getCookies: ${result.size} entries, keys=${result.keys}, MUSIC_U len=$musicULen, userId=${getUserId()}")
        return result
    }

    fun isLoggedIn(): Boolean {
        val cookies = getCookies()
        return cookies["MUSIC_U"].orEmpty().isNotBlank()
    }

    /**
     * Marks that token initialization has started. Returns true if this is the first call,
     * false if init was already in progress (prevents double-fetch).
     */
    fun markTokenInitStarted(): Boolean {
        if (_tokenInitStarted) return false
        _tokenInitStarted = true
        return true
    }

    /**
     * Signals that token initialization has completed (success or failure).
     * All waiting coroutines will be released.
     */
    fun signalTokenInitComplete() {
        if (!_tokenReady.isCompleted) {
            _tokenReady.complete(Unit)
        }
    }

    /**
     * Returns true if we already have a token (MUSIC_U or MUSIC_A) saved on disk.
     * Used to decide whether we need to fetch a new anonymous token at startup.
     */
    fun hasUsableToken(): Boolean {
        val cookies = getCookies()
        if (cookies["MUSIC_U"].orEmpty().isNotBlank()) return true
        val musicA = getCookie("MUSIC_A")
        return !musicA.isNullOrBlank()
    }

    /**
     * Returns cookies with a fallback MUSIC_A (anonymous token) if not logged in.
     * This ensures API calls that require authentication (like getSongUrl) work even
     * without user login, using the anonymous token obtained at startup.
     */
    fun getCookiesWithAnonFallback(): Map<String, String> {
        val cookies = getCookies()
        if (cookies["MUSIC_U"].orEmpty().isNotBlank()) {
            AppLogger.debug("[Cookie] getCookiesWithAnonFallback: using logged-in cookies (MUSIC_U len=${cookies["MUSIC_U"]?.length ?: 0})")
            return cookies
        }

        // Not logged in: ensure we have MUSIC_A
        val musicA = prefs.getString("ck_MUSIC_A", null)
            ?: prefs.getString("ck_backup_MUSIC_A", null)
        AppLogger.warn("[Cookie] getCookiesWithAnonFallback: NOT logged in, MUSIC_A present=${musicA != null}")
        if (musicA.isNullOrBlank()) return cookies

        return cookies + ("MUSIC_A" to musicA)
    }

    fun saveAnonToken(musicA: String) {
        prefs.edit().apply {
            putString("ck_MUSIC_A", musicA)
            putString("ck_backup_MUSIC_A", musicA)
            apply()
        }
        AppLogger.debug("Anonymous token saved")
    }

    fun needsRefresh(): Boolean {
        if (!isLoggedIn()) return false
        val lastTime = prefs.getLong("cookie_time", 0L)
        if (lastTime == 0L) return false
        return System.currentTimeMillis() - lastTime > REFRESH_INTERVAL_MS
    }

    fun touchCookieTime() {
        prefs.edit().putLong("cookie_time", System.currentTimeMillis()).apply()
    }

    fun getUserId(): Long = prefs.getLong("user_id", 0L)

    fun saveUserId(userId: Long) {
        AppLogger.info("[Cookie] saveUserId: $userId")
        prefs.edit().putLong("user_id", userId).apply()
    }

    fun clearCookies() {
        val keys = prefs.getStringSet("cookie_keys", emptySet()) ?: return
        prefs.edit().apply {
            keys.forEach {
                remove("ck_$it")
                remove("ck_backup_$it")
            }
            remove("cookie_keys")
            remove("cookie_time")
            remove("user_id")
            apply()
        }
        AppLogger.debug("Cookies cleared")
    }

    fun saveCookieMapForLogin(cookies: Map<String, String>): ParseResult {
        val cleanMap = sanitizeCookies(cookies)
        if (cleanMap["MUSIC_U"].isNullOrBlank()) {
            return ParseResult(false, "缺少 MUSIC_U 字段。请确认网页登录成功后再导入 Cookie。")
        }
        saveCookies(cleanMap)
        return ParseResult(true, "登录成功", cleanMap.keys.filter { it in KEY_COOKIES })
    }

    data class ParseResult(
        val success: Boolean,
        val message: String,
        val foundKeys: List<String> = emptyList()
    )

    /**
     * Directly saves MUSIC_U value (user pastes only the token value).
     */
    fun saveMusicUDirect(musicU: String): ParseResult {
        val trimmed = musicU.trim()
        if (trimmed.isBlank()) {
            return ParseResult(false, "MUSIC_U 为空")
        }
        return saveCookieMapForLogin(mapOf("MUSIC_U" to trimmed))
    }

    /**
     * Parses complete cookie string (e.g. from browser), extracts only MUSIC_U.
     */
    fun parseAndSaveSmart(cookieStr: String): ParseResult {
        if (cookieStr.isBlank()) {
            return ParseResult(false, "Cookie 为空，请从浏览器复制完整 Cookie")
        }

        val decoded = try {
            if (cookieStr.contains("%")) {
                java.net.URLDecoder.decode(cookieStr, "UTF-8")
            } else cookieStr
        } catch (_: Exception) {
            cookieStr
        }

        // Try standard cookie format first (key=value; key=value; ...)
        val pairs = decoded
            .replace("\n", ";").replace("\r", "")
            .split(";")
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains("=") }

        if (pairs.isNotEmpty()) {
            val map = mutableMapOf<String, String>()
            for (pair in pairs) {
                val eq = pair.indexOf('=')
                if (eq > 0) {
                    val k = pair.substring(0, eq).trim()
                    val v = pair.substring(eq + 1).trim()
                    if (k.isNotBlank() && v.isNotBlank()) map[k] = v
                }
            }
            if (map.isNotEmpty()) {
                val musicU = map["MUSIC_U"]
                if (!musicU.isNullOrBlank()) {
                    return saveCookieMapForLogin(map)
                }
            }
        }

        // Fallback: try exported format ([KEY]\nVALUE\n\n)
        val sectionRegex = Regex("""\[(\w+)]\s*\n([^\[]+)""")
        val sectionMatches = sectionRegex.findAll(decoded)
        if (sectionMatches.any()) {
            val map = mutableMapOf<String, String>()
            for (match in sectionRegex.findAll(decoded)) {
                val key = match.groupValues[1].trim()
                val value = match.groupValues[2].trim().lines().first().trim()
                when (key) {
                    "MUSIC_U" -> map["MUSIC_U"] = value
                    "__csrf" -> map["__csrf"] = value
                    "MUSIC_A" -> map["MUSIC_A"] = value
                    "NMTID" -> map["NMTID"] = value
                    "WEVNSM" -> map["WEVNSM"] = value
                    "WNMCID" -> map["WNMCID"] = value
                }
            }
            if (!map["MUSIC_U"].isNullOrBlank()) return saveCookieMapForLogin(map)
        }

        return ParseResult(false, "未识别到有效 Cookie 键值对，请检查格式")
    }

    fun parseAndSave(cookieStr: String): Boolean {
        return parseAndSaveSmart(cookieStr).success
    }

    fun getCookie(key: String): String? {
        val primary = prefs.getString("ck_$key", null)
        if (!primary.isNullOrBlank()) return primary
        return prefs.getString("ck_backup_$key", null)
    }

    fun getRawCookies(): String {
        val cookies = getCookies()
        return cookies.map { (k, v) -> "$k=$v" }.joinToString("/")
    }

    fun exportAccountData(): AccountExportData {
        val cookies = getCookies()
        val musicU = cookies["MUSIC_U"] ?: ""
        val csrf = cookies["__csrf"] ?: ""
        val musicA = prefs.getString("ck_MUSIC_A", null)
            ?: prefs.getString("ck_backup_MUSIC_A", null)
            ?: ""
        val nmtid = cookies["NMTID"] ?: ""
        val wevnsm = cookies["WEVNSM"] ?: ""
        val wnmcid = cookies["WNMCID"] ?: ""

        return AccountExportData(
            musicU = musicU,
            csrf = csrf,
            musicA = musicA,
            nmtid = nmtid,
            wevnsm = wevnsm,
            wnmcid = wnmcid,
            rawCookies = getRawCookies(),
            userId = getUserId()
        )
    }

    data class AccountExportData(
        val musicU: String,
        val csrf: String,
        val musicA: String,
        val nmtid: String,
        val wevnsm: String,
        val wnmcid: String,
        val rawCookies: String,
        val userId: Long
    )

    private fun sanitizeCookies(cookies: Map<String, String>): Map<String, String> {
        val clean = linkedMapOf<String, String>()
        cookies.forEach { (rawKey, rawValue) ->
            val key = rawKey.trim()
            val value = rawValue.trim()
            if (key !in ALLOWED_COOKIE_KEYS) return@forEach
            if (!COOKIE_NAME_REGEX.matches(key)) return@forEach
            if (value.isBlank() || value.any { it.isISOControl() } || ';' in value) return@forEach
            clean[key] = value
        }
        if (clean.isNotEmpty()) {
            clean.putIfAbsent("os", "pc")
            clean.putIfAbsent("appver", "3.1.17.204416")
        }
        return clean
    }

    private fun openEncryptedPrefs(context: Context): SharedPreferences {
        return runCatching {
            createEncryptedPrefs(context)
        }.getOrElse { error ->
            AppLogger.warn("Failed to open encrypted cookie storage, recreating: ${error.message}")
            context.deleteSharedPreferences("hachimi_secure_cookies")
            runCatching {
                createEncryptedPrefs(context)
            }.getOrElse { fallbackError ->
                AppLogger.error("Encrypted storage unavailable, falling back to plain prefs", fallbackError)
                context.getSharedPreferences("hachimi_secure_cookies_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            "hachimi_secure_cookies",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun migrateLegacyCookiesIfNeeded() {
        if (prefs.getStringSet("cookie_keys", null) != null) return
        val legacyKeys = legacyPrefs.getStringSet("cookie_keys", emptySet()).orEmpty()
        if (legacyKeys.isEmpty()) return
        prefs.edit {
            legacyKeys.forEach { key ->
                legacyPrefs.getString("ck_$key", null)?.let { value ->
                    putString("ck_$key", value)
                    putString("ck_backup_$key", value)
                }
            }
            putStringSet("cookie_keys", legacyKeys)
            putLong("cookie_time", legacyPrefs.getLong("cookie_time", 0L))
            putLong("user_id", legacyPrefs.getLong("user_id", 0L))
        }
        legacyPrefs.edit {
            legacyKeys.forEach { key ->
                remove("ck_$key")
                remove("ck_backup_$key")
            }
            remove("cookie_keys")
            remove("cookie_time")
            remove("user_id")
        }
        AppLogger.info("Migrated cookies to encrypted storage")
    }

    /**
     * Validates that the current login session is still active.
     * If the session has expired, cookies are cleared automatically.
     * Returns true if the session is valid, false otherwise.
     */
    fun invalidateIfExpired(): Boolean {
        if (!isLoggedIn()) return false
        val lastTime = prefs.getLong("cookie_time", 0L)
        if (lastTime == 0L) return false
        // Check if more than 7 days have passed since last activity
        if (System.currentTimeMillis() - lastTime > EXPIRE_INTERVAL_MS) {
            clearCookies()
            AppLogger.warn("Cookies cleared due to expiration (7 days inactive)")
            return false
        }
        return true
    }
}
