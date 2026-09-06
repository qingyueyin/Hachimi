package com.qing.hachimi.data.api

import com.qing.hachimi.util.AppLogger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

class NeteaseApi(internal val client: OkHttpClient) {

    companion object {
        const val BASE_URL = "https://music.163.com"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"
        // iPhone UA for eapi requests, matching api-enhanced's request.js
        private const val EAPI_USER_AGENT = "NeteaseMusic 9.0.90/5038 (iPhone; iOS 16.2; zh_CN)"

        val QUALITY_MAP = mapOf(
            "standard" to "标准",
            "higher" to "较高",
            "exhigh" to "极高",
            "lossless" to "无损",
            "hires" to "Hi-Res",
            "jyeffect" to "高清环绕声",
            "sky" to "沉浸环绕声",
            "jymaster" to "超清母带"
        )

        private val QUALITY_ORDER = listOf(
            "jymaster", "sky", "jyeffect", "hires", "lossless", "exhigh", "higher", "standard"
        )

        fun bestQuality(available: List<String>): String? {
            for (q in QUALITY_ORDER) {
                if (q in available) return q
            }
            return available.firstOrNull()
        }

        /** Get fallback quality order starting from the requested quality, stepping down to standard */
        fun fallbackQuality(from: String): List<String> {
            val idx = QUALITY_ORDER.indexOf(from)
            if (idx >= 0) return QUALITY_ORDER.subList(idx, QUALITY_ORDER.size)
            // If not in order (e.g. custom), just return from + all others
            return listOf(from) + QUALITY_ORDER.filter { it != from }
        }
    }

    fun formBody(vararg params: String): RequestBody {
        require(params.size % 2 == 0)
        val sb = StringBuilder()
        for (i in params.indices step 2) {
            if (i > 0) sb.append("&")
            sb.append(java.net.URLEncoder.encode(params[i], "UTF-8"))
                .append("=")
                .append(java.net.URLEncoder.encode(params[i + 1], "UTF-8"))
        }
        return sb.toString().toRequestBody("application/x-www-form-urlencoded".toMediaType())
    }

    fun cookieHeader(cookies: Map<String, String>): String =
        cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

    fun post(
        url: String,
        body: RequestBody,
        cookies: Map<String, String>,
        callTimeoutMs: Long? = null,
    ): String {
        return postRaw(url, body, cookies, callTimeoutMs).use { response ->
            response.body.string()
        }
    }

    fun postRaw(
        url: String,
        body: RequestBody,
        cookies: Map<String, String>,
        callTimeoutMs: Long? = null,
    ): Response {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://music.163.com/")
            .header("Cookie", cookieHeader(cookies))
            .post(body)
            .build()
        val call = client.newCall(request)
        callTimeoutMs?.let { call.timeout().timeout(it, TimeUnit.MILLISECONDS) }
        return call.execute()
    }

    /**
     * EAPI POST - matches api-enhanced's request.js eapi format with:
     * - iPhone User-Agent (not Chrome)
     * - Full cookie header with os/device/channel info + URL-encoded values
     * - This ensures the server generates valid download URLs without authSecret
     */
    fun eapiPost(
        path: String,
        payload: String,
        cookies: Map<String, String>,
        callTimeoutMs: Long? = null,
    ): String {
        val params = NeteaseCrypto.encryptParams("/eapi/$path", payload)
        val now = System.currentTimeMillis()
        // cookies first, then override platform fields (matching weapi cookie order)
        val extendedCookies = mutableMapOf<String, String>().apply {
            putAll(cookies)
            put("os", "pc")
            put("osver", "Microsoft-Windows-10-Professional-build-19045-64bit")
            put("appver", "3.1.17.204416")
            put("channel", "netease")
            put("deviceId", cookies["deviceId"] ?: "pyncm!")
            put("buildver", now.toString().take(10))
            put("resolution", "1920x1080")
            put("versioncode", "140")
            put("mobilename", "")
            put("WNMCID", cookies["WNMCID"] ?: "")
            put("WEVNSM", cookies["WEVNSM"] ?: "1.0.0")
        }
        val cookieStr = extendedCookies.entries
            .filter { it.value.isNotEmpty() }
            .joinToString("; ") { "${java.net.URLEncoder.encode(it.key, "UTF-8")}=${java.net.URLEncoder.encode(it.value, "UTF-8")}" }

        val request = Request.Builder()
            .url("$BASE_URL/eapi/$path")
            .header("User-Agent", EAPI_USER_AGENT)
            .header("Referer", "https://music.163.com/")
            .header("Cookie", cookieStr)
            .post(formBody("params", params))
            .build()
        val call = client.newCall(request)
        callTimeoutMs?.let { call.timeout().timeout(it, TimeUnit.MILLISECONDS) }
        return call.execute().use { response ->
            val raw = response.body.string()
            val code = try { org.json.JSONObject(raw).optLong("code") } catch (_: Exception) { -1L }
            val msg = try { org.json.JSONObject(raw).optString("message").ifEmpty { org.json.JSONObject(raw).optString("msg") } } catch (_: Exception) { "" }
            AppLogger.info("[EAPI] <- $path | HTTP ${response.code} | code=$code | msg=$msg | body len=${raw.length}")
            if (code != 200L && raw.isNotBlank()) {
                AppLogger.warn("[EAPI] <- $path error body: ${raw.take(400)}")
            }
            raw
        }
    }

    fun eapiPostRaw(path: String, payload: String, cookies: Map<String, String>): Response {
        val params = NeteaseCrypto.encryptParams("/eapi/$path", payload)
        val now = System.currentTimeMillis()
        val extendedCookies = mutableMapOf<String, String>().apply {
            put("os", "pc")
            put("osver", "Microsoft-Windows-10-Professional-build-19045-64bit")
            put("appver", "3.1.17.204416")
            put("channel", "netease")
            put("deviceId", "pyncm!")
            put("buildver", now.toString().take(10))
            put("resolution", "1920x1080")
            put("versioncode", "140")
            put("mobilename", "")
            putAll(cookies)
        }
        val cookieStr = extendedCookies.entries
            .filter { it.value.isNotEmpty() }
            .joinToString("; ") { "${java.net.URLEncoder.encode(it.key, "UTF-8")}=${java.net.URLEncoder.encode(it.value, "UTF-8")}" }
        val request = Request.Builder()
            .url("$BASE_URL/eapi/$path")
            .header("User-Agent", EAPI_USER_AGENT)
            .header("Referer", "https://music.163.com/")
            .header("Cookie", cookieStr)
            .post(formBody("params", params))
            .build()
        return client.newCall(request).execute()
    }

    /**
     * Build extended cookie header for WEAPI requests.
     * Matches api-enhanced's processCookieObject with all required fields.
     */
    private fun buildWeapiCookieHeader(cookies: Map<String, String>): String {
        val now = System.currentTimeMillis()
        val nuid = cookies["_ntes_nuid"] ?: (1..64).map { "0123456789ABCDEF".random() }.joinToString("")
        val nnid = cookies["_ntes_nnid"] ?: "${nuid},${now}"
        val nmtid = cookies["NMTID"] ?: (1..16).map { "0123456789ABCDEF".random() }.joinToString("")
        val extended = mutableMapOf<String, String>().apply {
            putAll(cookies)
            put("os", "pc")
            put("osver", "Microsoft-Windows-10-Professional-build-19045-64bit")
            put("appver", "3.1.17.204416")
            put("channel", "netease")
            put("deviceId", cookies["deviceId"] ?: "pyncm!")
            put("buildver", now.toString().take(10))
            put("resolution", "1920x1080")
            put("versioncode", "140")
            put("mobilename", "")
            put("_ntes_nuid", nuid)
            put("_ntes_nnid", nnid)
            put("WNMCID", cookies["WNMCID"] ?: "")
            put("WEVNSM", cookies["WEVNSM"] ?: "1.0.0")
            put("__remember_me", "true")
            put("ntes_kaola_ad", "1")
            put("NMTID", nmtid)
        }
        return extended.entries
            .filter { it.value.isNotEmpty() }
            .joinToString("; ") { "${java.net.URLEncoder.encode(it.key, "UTF-8")}=${java.net.URLEncoder.encode(it.value, "UTF-8")}" }
    }

    fun weapiPost(
        path: String,
        payload: String,
        cookies: Map<String, String>,
        callTimeoutMs: Long? = null,
    ): String {
        // WEAPI: csrf_token must ALWAYS be in the encrypted data (even if empty),
        // matching api-enhanced's request.js weapi handling
        val finalPayload = try {
            val obj = org.json.JSONObject(payload)
            obj.put("csrf_token", cookies["__csrf"] ?: "")
            obj.toString()
        } catch (_: Exception) {
            payload
        }
        val encrypted = NeteaseCrypto.encryptWeapi(finalPayload)
        val cookieStr = buildWeapiCookieHeader(cookies)
        val url = "$BASE_URL/weapi/$path"
        val body = formBody(
            "params", encrypted.getValue("params"),
            "encSecKey", encrypted.getValue("encSecKey")
        )
        
        // ── Logging ──
        val cookieCount = cookieStr.split(";").size
        val hasMusicU = !cookies["MUSIC_U"].isNullOrBlank()
        val hasCsrf = !cookies["__csrf"].isNullOrBlank()
        AppLogger.info("[WEAPI] -> $path | cookies=$cookieCount fields | MUSIC_U=$hasMusicU | csrf=$hasCsrf")
        AppLogger.debug("[WEAPI] -> payload=${payload.take(80)} | params len=${encrypted.getValue("params").length}, encSecKey len=${encrypted.getValue("encSecKey").length}")
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", BASE_URL)
            .header("Cookie", cookieStr)
            .post(body)
            .build()
        val call = client.newCall(request)
        callTimeoutMs?.let { call.timeout().timeout(it, TimeUnit.MILLISECONDS) }
        return call.execute().use { response ->
            val raw = response.body.string()
            val code = try { org.json.JSONObject(raw).optLong("code") } catch (_: Exception) { -1L }
            val msg = try { org.json.JSONObject(raw).optString("message").ifEmpty { org.json.JSONObject(raw).optString("msg") } } catch (_: Exception) { "" }
            AppLogger.info("[WEAPI] <- $path | HTTP ${response.code} | code=$code | msg=$msg | body len=${raw.length}")
            if (code != 200L && raw.isNotBlank()) {
                AppLogger.warn("[WEAPI] <- $path error body: ${raw.take(500)}")
            }
            raw
        }
    }

    fun get(
        url: String,
        cookies: Map<String, String>,
        callTimeoutMs: Long? = null,
    ): String {
        val call = client.newCall(Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://music.163.com/")
            .header("Cookie", cookieHeader(cookies))
            .get()
            .build())
        callTimeoutMs?.let { call.timeout().timeout(it, TimeUnit.MILLISECONDS) }
        return call.execute().use { response ->
            response.body.string()
        }
    }
}
