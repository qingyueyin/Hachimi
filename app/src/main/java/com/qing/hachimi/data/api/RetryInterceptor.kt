package com.qing.hachimi.data.api

import com.qing.hachimi.util.AppLogger
import kotlinx.coroutines.delay
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Retry interceptor for network requests with exponential backoff.
 * Retries failed requests up to [maxRetries] times with increasing delay between attempts.
 */
class RetryInterceptor(
    private val maxRetries: Int = 2,
    private val initialDelayMs: Long = 1000
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val safeUrl = request.url.newBuilder().query(null).fragment(null).build()
        var response: Response? = null
        var lastException: IOException? = null
        var retryCount = 0

        while (retryCount <= maxRetries) {
            try {
                response = chain.proceed(request)
                // If response is successful, return it immediately
                if (response.isSuccessful || response.code in 400..499) {
                    return response
                }
                // Server error (5xx) - retry
                response.close()
            } catch (e: IOException) {
                lastException = e
                AppLogger.warn("Request failed (attempt ${retryCount + 1}): $safeUrl")
            }

            if (retryCount < maxRetries) {
                val delayMs = initialDelayMs * (1L shl retryCount) // Exponential backoff: 1s, 2s, 4s...
                AppLogger.info("Retrying request in ${delayMs}ms (attempt ${retryCount + 1}/$maxRetries)")
                try {
                    Thread.sleep(delayMs)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
            retryCount++
        }

        // All retries exhausted
        lastException?.let {
            AppLogger.error("All $maxRetries retries failed for $safeUrl", it)
            throw it
        }
        return response ?: throw IllegalStateException("No response after retries")
    }
}
