package com.qing.hachimi.util

import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer

class LoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startTime = System.currentTimeMillis()
        val safeUrl = request.url.newBuilder().query(null).fragment(null).build()

        AppLogger.debug("[HTTP] ${request.method} $safeUrl")

        // Log request body size if present
        request.body?.let { body ->
            AppLogger.debug("[HTTP] Request body size: ${body.contentLength()} bytes")
        }

        val response = chain.proceed(request)
        val elapsed = System.currentTimeMillis() - startTime
        val bodySize = response.body.contentLength()

        AppLogger.debug(
            "[HTTP] ${response.code} $safeUrl (${elapsed}ms, ${formatSize(bodySize)})"
        )

        // Log cookies sent (truncated for security)
        val cookieHeader = request.header("Cookie")
        if (cookieHeader != null) {
            val sanitized = cookieHeader.split(";").joinToString("; ") { part ->
                val (key, _) = part.split("=", limit = 2)
                "$key=***"
            }
            AppLogger.debug("[HTTP] Cookies: $sanitized")
        }

        return response
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 0 -> "unknown"
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            else -> "${bytes / (1024 * 1024)}MB"
        }
    }
}
