package com.qing.hachimi.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.qing.hachimi.util.AppLogger
import org.json.JSONObject

class NeteaseWebLoginActivity : ComponentActivity() {

    companion object {
        const val RESULT_COOKIE = "result_cookie_map_json"
        const val EXTRA_CLEAR_CACHE = "clear_cache"
        private const val TARGET_URL = "https://music.163.com/"
        private val ALLOWED_DOMAINS = setOf("163.com", "126.net", "163yun.com")
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36"
    }

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var hasReturned = false

    private val checkLoginRunnable = object : Runnable {
        override fun run() {
            if (!hasReturned && !returnIfLoggedIn()) {
                handler.postDelayed(this, 800L)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CookieManager.getInstance().setAcceptCookie(true)

        // 如果请求清缓存，清除 WebView 所有 Cookie 强制重新登录
        if (intent.getBooleanExtra(EXTRA_CLEAR_CACHE, false)) {
            val cm = CookieManager.getInstance()
            cm.removeAllCookies(null)
            cm.flush()
            AppLogger.info("[WebLogin] Cache cleared, forcing fresh login")
        }

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                allowFileAccess = false
                allowContentAccess = false
                userAgentString = DESKTOP_UA
                useWideViewPort = true
                loadWithOverviewMode = true
            }
            webChromeClient = WebChromeClient()
            webViewClient = LoginWebViewClient()
        }
        setContentView(webView)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                applyPredictiveBackProgress(backEvent)
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                applyPredictiveBackProgress(backEvent)
            }

            override fun handleOnBackCancelled() {
                resetPredictiveBackProgress()
            }

            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    resetPredictiveBackProgress()
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        webView.loadUrl(TARGET_URL)
        handler.post(checkLoginRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (this::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun applyPredictiveBackProgress(backEvent: BackEventCompat) {
        if (!this::webView.isInitialized) return

        val progress = backEvent.progress.coerceIn(0f, 1f)
        val direction = if (backEvent.swipeEdge == BackEventCompat.EDGE_RIGHT) 1f else -1f
        val maxTranslationX = 64f * resources.displayMetrics.density
        webView.translationX = maxTranslationX * direction * progress
        webView.alpha = 1f - 0.08f * progress
    }

    private fun resetPredictiveBackProgress() {
        if (!this::webView.isInitialized) return

        webView.animate().cancel()
        webView.translationX = 0f
        webView.alpha = 1f
    }

    private inner class LoginWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val uri = request?.url ?: return false
            if (request.isForMainFrame && !isAllowedUri(uri)) {
                AppLogger.warn("Blocked unexpected login navigation: $uri")
                return true
            }
            scheduleLoginCheck()
            return false
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            scheduleLoginCheck()
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            scheduleLoginCheck()
        }
    }

    private fun scheduleLoginCheck() {
        handler.removeCallbacks(checkLoginRunnable)
        handler.postDelayed(checkLoginRunnable, 250L)
    }

    private fun returnIfLoggedIn(): Boolean {
        CookieManager.getInstance().flush()
        val cookies = readCookieMap()
        if (cookies["MUSIC_U"].isNullOrBlank()) {
            return false
        }
        hasReturned = true
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(RESULT_COOKIE, JSONObject(cookies as Map<*, *>).toString())
        )
        finish()
        return true
    }

    private fun readCookieMap(): Map<String, String> {
        val raw = listOf(
            "https://music.163.com",
            "https://interface.music.163.com",
            "https://interface3.music.163.com"
        ).mapNotNull { CookieManager.getInstance().getCookie(it) }
            .joinToString("; ")
        return raw.split(';')
            .map(String::trim)
            .filter { it.isNotBlank() && it.contains('=') }
            .mapNotNull { part ->
                val index = part.indexOf('=')
                val key = part.substring(0, index).trim()
                val value = part.substring(index + 1).trim()
                if (key.isNotBlank() && value.isNotBlank()) key to value else null
            }
            .toMap()
    }

    private fun isAllowedUri(uri: Uri): Boolean {
        if (uri.toString() == "about:blank") return true
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        return ALLOWED_DOMAINS.any { domain -> host == domain || host.endsWith(".$domain") }
    }
}
