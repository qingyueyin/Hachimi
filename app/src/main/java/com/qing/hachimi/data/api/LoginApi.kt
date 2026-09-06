package com.qing.hachimi.data.api

import com.qing.hachimi.util.AppLogger
import com.qing.hachimi.data.local.CookieManager
import org.json.JSONObject

class LoginApi(private val api: NeteaseApi) {

    data class QrResult(val unikey: String, val sessionCookies: Map<String, String> = emptyMap())
    data class QrStatus(val code: Int, val cookies: Map<String, String> = emptyMap(), val message: String = "")
    data class LoginStatus(val isLoggedIn: Boolean, val userId: Long = 0, val nickname: String = "", val avatarUrl: String = "")
    data class RegisterAnonResult(val musicA: String, val cookies: Map<String, String>)

    fun qrKey(): QrResult? {
        val raw = api.eapiPost("login/qrcode/unikey", JSONObject().put("type", 3).toString(), emptyMap())
        val obj = JSONObject(raw)
        if (obj.optLong("code") != 200L) return null
        val unikey = obj.optString("unikey").takeIf { it.isNotBlank() } ?: return null
        val setCookieStr = obj.optString("cookie", "")
        val sessionCookies = if (setCookieStr.isNotBlank()) parseCookieString(setCookieStr) else emptyMap()
        return QrResult(unikey, sessionCookies)
    }

    fun checkQr(unikey: String, sessionCookies: Map<String, String> = emptyMap()): QrStatus {
        val payload = JSONObject().put("key", unikey).put("type", 3).toString()
        return api.eapiPostRaw("login/qrcode/client/login", payload, sessionCookies).use { response ->
            val raw = response.body.string()
            val obj = JSONObject(raw)
            val code = obj.optLong("code")
            when {
                code == 803L -> {
                    val cookies = parseCookiesFromHeaders(response.headers)
                    if (cookies.containsKey("MUSIC_U")) QrStatus(803, cookies = cookies, message = "login success")
                    else QrStatus(803, message = "no MUSIC_U")
                }
                code == 802L -> QrStatus(802, message = "scanned, confirm on phone")
                code == 801L -> QrStatus(801, message = "waiting for scan")
                else -> QrStatus(code.toInt(), message = obj.optString("message", "unknown"))
            }
        }
    }

    fun registerAnonimous(): RegisterAnonResult? {
        return try {
            val deviceId = generateDeviceId()
            val encodedId = encodeDeviceId(deviceId)
            val payload = JSONObject().put("username", encodedId).toString()
            val raw = api.eapiPost("register/anonimous", payload, emptyMap())
            val obj = JSONObject(raw)
            if (obj.optLong("code") != 200L) return null
            val cookieHeader = obj.optString("cookie")
            if (cookieHeader.isBlank()) return null
            val cookies = parseCookieString(cookieHeader)
            val musicA = cookies["MUSIC_A"] ?: return null
            RegisterAnonResult(musicA = musicA, cookies = cookies)
        } catch (e: Exception) {
            AppLogger.error("registerAnonimous failed", e)
            null
        }
    }

    fun checkLoginStatus(cookies: Map<String, String>): LoginStatus {
        return try {
            // Reference: user_account.js uses WEAPI with path /api/nuser/account/get
            val raw = api.weapiPost("nuser/account/get", "{}", cookies)
            AppLogger.debug("[LoginApi] checkLoginStatus raw: ${raw.take(300)}")
            val obj = JSONObject(raw)
            if (obj.optLong("code") == 200L) {
                val profile = obj.optJSONObject("profile") ?: return LoginStatus(false)
                LoginStatus(
                    isLoggedIn = true,
                    userId = profile.optLong("userId"),
                    nickname = profile.optString("nickname", ""),
                    avatarUrl = profile.optString("avatarUrl", ""),
                )
            } else {
                AppLogger.warn("[LoginApi] checkLoginStatus: code=${obj.optLong("code")}, msg=${obj.optString("message")}")
                LoginStatus(false)
            }
        } catch (e: Exception) {
            AppLogger.error("[LoginApi] checkLoginStatus exception", e)
            LoginStatus(false)
        }
    }

    fun refreshLogin(cookies: Map<String, String>): Boolean {
        return try {
            val raw = api.eapiPost("login/token/refresh", "{}", cookies)
            val obj = JSONObject(raw)
            obj.optLong("code") == 200L
        } catch (_: Exception) { false }
    }

    private fun generateDeviceId(): String {
        val hexChars = "0123456789ABCDEF"
        return (1..52).map { hexChars.random() }.joinToString("")
    }

    private fun encodeDeviceId(deviceId: String): String {
        val xorKey = "3go8&\$8*3*3h0k(2)2"
        val xoredString = deviceId.mapIndexed { i, c -> (c.code xor xorKey[i % xorKey.length].code).toChar() }.joinToString("")
        val digest = java.security.MessageDigest.getInstance("MD5").digest(xoredString.toByteArray(Charsets.UTF_8))
        val base64 = android.util.Base64.encodeToString(digest, android.util.Base64.DEFAULT).trim()
        return android.util.Base64.encodeToString("$deviceId $base64".toByteArray(Charsets.UTF_8), android.util.Base64.DEFAULT).trim()
    }

    private fun parseCookieString(cookieStr: String): Map<String, String> {
        return cookieStr.split(";").map { it.trim() }.filter { it.contains("=") }.mapNotNull { pair ->
            val eq = pair.indexOf('=')
            if (eq > 0) {
                val k = pair.substring(0, eq).trim()
                val v = try { java.net.URLDecoder.decode(pair.substring(eq + 1).trim(), "UTF-8") } catch (_: Exception) { pair.substring(eq + 1).trim() }
                k to v
            } else null
        }.toMap()
    }

    private fun parseCookiesFromHeaders(headers: okhttp3.Headers): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val setCookies = headers.values("Set-Cookie")
        for (setCookie in setCookies) {
            for (part in setCookie.split(";")) {
                val eq = part.indexOf('=')
                if (eq > 0) {
                    val key = part.substring(0, eq).trim()
                    val value = part.substring(eq + 1).trim()
                    if (CookieManager.KEY_COOKIES.contains(key)) result[key] = value
                }
            }
        }
        return result
    }
}
