package com.qing.hachimi.data.api

import com.qing.hachimi.util.AppLogger
import com.qing.hachimi.data.local.CookieManager
import org.json.JSONObject

class LoginApi(private val api: NeteaseApi) {

    data class LoginStatus(val isLoggedIn: Boolean, val userId: Long = 0, val nickname: String = "", val avatarUrl: String = "")
    data class RegisterAnonResult(val musicA: String, val cookies: Map<String, String>)

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

}