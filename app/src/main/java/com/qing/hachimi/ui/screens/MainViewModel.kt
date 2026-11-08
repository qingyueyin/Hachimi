package com.qing.hachimi.ui.screens

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.qing.hachimi.data.api.*
import com.qing.hachimi.data.local.AccountHistory
import com.qing.hachimi.data.local.AccountHistoryManager
import com.qing.hachimi.data.local.CookieManager
import com.qing.hachimi.data.local.DiscoveryCacheManager
import com.qing.hachimi.data.local.FloatingBarEffect
import com.qing.hachimi.data.local.FolderNamingFormat
import com.qing.hachimi.data.local.NamingFormat
import com.qing.hachimi.data.local.SettingsManager
import com.qing.hachimi.data.model.*
import com.qing.hachimi.data.repository.NeteaseRepository
import com.qing.hachimi.downloader.DownloadEngine
import com.qing.hachimi.ui.theme.ColorMode
import com.qing.hachimi.BuildConfig
import com.qing.hachimi.util.AppLogger
import com.qing.hachimi.util.UpdateChecker
import com.qing.hachimi.util.UpdateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@Immutable
data class MainUiState(
    val isLoggedIn: Boolean = false,
    val userId: Long = 0L,
    val statusMessage: String? = null,
    val quality: String = "exhigh",
    val downloadDir: String = "",
    val fuckAiMode: Boolean = false,
    val namingFormat: NamingFormat = NamingFormat.SONG_ARTIST,
    val downloadLyrics: Boolean = true,
    val folderNamingFormat: FolderNamingFormat = FolderNamingFormat.NONE,
    val enableWriteTags: Boolean = false,
    val saveTlLrc: Boolean = false,
    val saveRomaLrc: Boolean = false,
    val saveYrc: Boolean = false,
    val customNamingTemplate: String = "",
    val artistDelimiter: String = "/",
    val qualityPickerOptions: List<String> = emptyList(),
    val colorMode: Int = 5,
    val blurEffect: Boolean = true,
    val floatingBarEffect: FloatingBarEffect = FloatingBarEffect.FROSTED_GLASS,
    val floatingNavigationBar: Boolean = true,
    val searchTabRequestId: Long = 0L,
    val scrollToTopTrigger: Int = 0,
    val scrollToTopTab: String = "",
    val currentTab: String = "DISCOVER",
    // Update check state
    val isCheckingUpdate: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val updateError: String? = null,
    val updateToast: String? = null,
)

class MainViewModel(
    private val repository: NeteaseRepository,
    private val cookieManager: CookieManager,
    private val downloadEngine: DownloadEngine,
    private val settingsManager: SettingsManager,
    private val discoveryCache: DiscoveryCacheManager,
    private val accountHistory: AccountHistoryManager,
    private val okHttpClient: okhttp3.OkHttpClient
) : ViewModel() {

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(MainUiState())
    val uiState: kotlinx.coroutines.flow.StateFlow<MainUiState> = _uiState

    val downloadProgress = downloadEngine.progressMap
    val downloadSessionStartedAt = downloadEngine.sessionStartedAt

    init {
        val quality = settingsManager.quality
        val dir = if (settingsManager.downloadDir.isNotBlank())
            settingsManager.downloadDir else settingsManager.defaultDownloadDir

        // Check for expired login session
        if (!cookieManager.invalidateIfExpired()) {
            AppLogger.info("Login session expired, clearing state")
        }

        _uiState.value = _uiState.value.copy(
            isLoggedIn = cookieManager.isLoggedIn(),
            userId = cookieManager.getUserId(),
            quality = quality,
            downloadDir = dir,
            fuckAiMode = settingsManager.fuckAiMode,
            namingFormat = settingsManager.namingFormat,
            downloadLyrics = settingsManager.downloadLyrics,
            folderNamingFormat = settingsManager.folderNamingFormat,
            enableWriteTags = settingsManager.enableWriteTags,
            saveTlLrc = settingsManager.saveTlLrc,
            saveRomaLrc = settingsManager.saveRomaLrc,
            saveYrc = settingsManager.saveYrc,
            customNamingTemplate = settingsManager.customNamingTemplate,
            artistDelimiter = settingsManager.artistDelimiter,
            qualityPickerOptions = settingsManager.getFilteredQualityKeys(),
            colorMode = settingsManager.themeColorMode,
            blurEffect = settingsManager.blurEffect,
            floatingBarEffect = settingsManager.floatingBarEffect,
            floatingNavigationBar = settingsManager.floatingNavigationBar
        )
        // Initialize anonymous token at startup (ensures MUSIC_A is available for non-logged-in users)
        initAnonymousToken()
    }

    fun checkForUpdate() {
        if (_uiState.value.isCheckingUpdate) return
        _uiState.value = _uiState.value.copy(
            isCheckingUpdate = true,
            updateError = null,
            updateInfo = null,
            updateToast = null,
        )

        viewModelScope.launch {
            try {
                val info = withTimeout(CHECK_UPDATE_TIMEOUT_MS) {
                    UpdateChecker.check(okHttpClient, BuildConfig.VERSION_NAME)
                }
                _uiState.value = if (info.hasUpdate) {
                    _uiState.value.copy(isCheckingUpdate = false, updateInfo = info)
                } else {
                    _uiState.value.copy(
                        isCheckingUpdate = false,
                        updateToast = "已是最新版本 v${info.latestVersion}",
                    )
                }
            } catch (_: TimeoutCancellationException) {
                _uiState.value = _uiState.value.copy(
                    isCheckingUpdate = false,
                    updateError = "检查超时，请检查网络后重试",
                )
            } catch (e: CancellationException) {
                _uiState.value = _uiState.value.copy(isCheckingUpdate = false)
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCheckingUpdate = false,
                    updateError = "检查更新失败: ${e.message}",
                )
            }
        }
    }

    fun dismissUpdateDialog() {
        _uiState.value = _uiState.value.copy(updateInfo = null)
    }

    fun consumeUpdateFeedback() {
        _uiState.value = _uiState.value.copy(updateError = null, updateToast = null)
    }

    companion object {
        private const val CHECK_UPDATE_TIMEOUT_MS = 10_000L
    }

    private fun initAnonymousToken() {
        // Skip if user is already logged in or we already have a usable token
        if (cookieManager.isLoggedIn()) return
        if (cookieManager.hasUsableToken()) return

        // Prevent double-fetch if init was already triggered
        if (!cookieManager.markTokenInitStarted()) return

        // Get anonymous token in background on IO dispatcher
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.loginApi().registerAnonimous()
                if (result != null) {
                    cookieManager.saveAnonToken(result.musicA)
                    // Also save other cookies from anon response (like NMTID, WEVNSM, etc.)
                    cookieManager.saveCookies(result.cookies)
                    AppLogger.info("Anonymous token initialized successfully")
                } else {
                    AppLogger.warn("Failed to initialize anonymous token")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.error("initAnonymousToken exception", e)
            } finally {
                cookieManager.signalTokenInitComplete()
            }
        }
    }

    fun updateQuality(quality: String) {
        settingsManager.quality = quality
        _uiState.value = _uiState.value.copy(quality = quality)
    }

    fun updateDownloadDir(path: String) {
        _uiState.value = _uiState.value.copy(downloadDir = path)
    }

    fun toggleFuckAiMode() {
        val newValue = !_uiState.value.fuckAiMode
        settingsManager.fuckAiMode = newValue
        val filteredKeys = settingsManager.getFilteredQualityKeys()
        val currentQuality = _uiState.value.quality
        val newQuality = if (currentQuality !in filteredKeys) filteredKeys.firstOrNull() ?: "exhigh"
            else currentQuality
        if (newQuality != currentQuality) {
            settingsManager.quality = newQuality
        }
        _uiState.value = _uiState.value.copy(
            fuckAiMode = newValue,
            qualityPickerOptions = filteredKeys,
            quality = newQuality
        )
    }

    fun setNamingFormat(format: NamingFormat) {
        settingsManager.namingFormat = format
        _uiState.value = _uiState.value.copy(namingFormat = format)
    }

    fun toggleDownloadLyrics() {
        val newValue = !_uiState.value.downloadLyrics
        settingsManager.downloadLyrics = newValue
        _uiState.value = _uiState.value.copy(downloadLyrics = newValue)
    }

    fun setFolderNamingFormat(format: FolderNamingFormat) {
        settingsManager.folderNamingFormat = format
        _uiState.value = _uiState.value.copy(folderNamingFormat = format)
    }

    fun toggleEnableWriteTags() {
        val newValue = !_uiState.value.enableWriteTags
        settingsManager.enableWriteTags = newValue
        _uiState.value = _uiState.value.copy(enableWriteTags = newValue)
    }

    fun toggleSaveTlLrc() {
        val newValue = !_uiState.value.saveTlLrc
        settingsManager.saveTlLrc = newValue
        _uiState.value = _uiState.value.copy(saveTlLrc = newValue)
    }

    fun toggleSaveRomaLrc() {
        val newValue = !_uiState.value.saveRomaLrc
        settingsManager.saveRomaLrc = newValue
        _uiState.value = _uiState.value.copy(saveRomaLrc = newValue)
    }

    fun toggleSaveYrc() {
        val newValue = !_uiState.value.saveYrc
        settingsManager.saveYrc = newValue
        _uiState.value = _uiState.value.copy(saveYrc = newValue)
    }

    fun setCustomNamingTemplate(template: String) {
        settingsManager.customNamingTemplate = template
        _uiState.value = _uiState.value.copy(customNamingTemplate = template)
    }

    fun setArtistDelimiter(delimiter: String) {
        val sanitized = delimiter.replace("\\", "/")
        settingsManager.artistDelimiter = sanitized
        _uiState.value = _uiState.value.copy(artistDelimiter = sanitized)
    }

    fun updateColorMode(mode: Int) {
        AppLogger.info("Theme mode changed to: $mode")
        settingsManager.themeColorMode = mode
        _uiState.value = _uiState.value.copy(colorMode = mode)
    }

    fun updateThemeBaseMode(baseMode: Int) {
        val current = ColorMode.fromValue(_uiState.value.colorMode)
        val normalizedBase = baseMode.coerceIn(0, 2)
        val nextMode = if (current.isMonet) normalizedBase + 3 else normalizedBase
        updateColorMode(nextMode)
    }

    fun toggleMonetTheme() {
        val current = ColorMode.fromValue(_uiState.value.colorMode)
        val nextMode = if (current.isMonet) current.toNonMonetMode() else current.toMonetMode()
        updateColorMode(nextMode)
    }

    fun toggleBlurEffect() {
        val newValue = !_uiState.value.blurEffect
        settingsManager.blurEffect = newValue
        _uiState.value = _uiState.value.copy(blurEffect = newValue)
    }

    fun setFloatingBarEffect(effect: FloatingBarEffect) {
        settingsManager.floatingBarEffect = effect
        _uiState.value = _uiState.value.copy(floatingBarEffect = effect)
    }

    fun toggleFloatingNavigationBar() {
        val newValue = !_uiState.value.floatingNavigationBar
        settingsManager.floatingNavigationBar = newValue
        _uiState.value = _uiState.value.copy(floatingNavigationBar = newValue)
    }

    private fun saveCurrentUserId() {
        viewModelScope.launch(Dispatchers.IO) {
            val status = repository.loginApi().checkLoginStatus(cookieManager.getCookies())
            if (status.isLoggedIn) {
                cookieManager.saveUserId(status.userId)
                AppLogger.info("Saved userId: ${status.userId}")
            }
        }
    }

    fun saveCookies(cookieStr: String): Boolean {
        val result = cookieManager.parseAndSaveSmart(cookieStr)
        if (result.success) {
            discoveryCache.clearAll()
            saveCurrentUserId()
        }
        _uiState.value = _uiState.value.copy(
            isLoggedIn = cookieManager.isLoggedIn(),
            userId = cookieManager.getUserId(),
            statusMessage = result.message
        )
        return result.success
    }

    fun saveCookieMap(cookies: Map<String, String>): Boolean {
        val result = cookieManager.saveCookieMapForLogin(cookies)
        if (result.success) {
            discoveryCache.clearAll()
            saveCurrentUserId()
            // 保存账号历史（异步）
            viewModelScope.launch(Dispatchers.IO) {
                saveAccountHistory(cookies)
            }
        }
        _uiState.value = _uiState.value.copy(
            isLoggedIn = cookieManager.isLoggedIn(),
            userId = cookieManager.getUserId(),
            statusMessage = result.message
        )
        return result.success
    }

    fun saveMusicUDirect(musicU: String): Boolean {
        val result = cookieManager.saveMusicUDirect(musicU)
        if (result.success) {
            discoveryCache.clearAll()
            saveCurrentUserId()
        }
        _uiState.value = _uiState.value.copy(
            isLoggedIn = cookieManager.isLoggedIn(),
            userId = cookieManager.getUserId(),
            statusMessage = result.message
        )
        return result.success
    }

    fun logout() {
        cookieManager.clearCookies()
        discoveryCache.clearAll()
        _uiState.value = _uiState.value.copy(isLoggedIn = false, userId = 0L)
    }

    fun getExportAccountData(): com.qing.hachimi.data.local.CookieManager.AccountExportData {
        return cookieManager.exportAccountData()
    }

    fun resetDiscover() {
        discoveryCache.clearAll()
        // discoveryCache 清空后，发现页下次刷新会重新从网络拉取
        _uiState.value = _uiState.value.copy(
            statusMessage = "已重置发现页，正在重新加载"
        )
    }

    fun clearCache(context: android.content.Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Clear Coil image cache
                coil3.SingletonImageLoader.get(context).diskCache?.clear()
                coil3.SingletonImageLoader.get(context).memoryCache?.clear()
                // Clear app external cache
                val cacheDir = context.externalCacheDir
                if (cacheDir != null && cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(statusMessage = "缓存已清除")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(statusMessage = "清除缓存失败: ${e.message}")
                }
            }
        }
    }

    fun clearStatus() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }

    fun scrollToTop() {
        _uiState.value = _uiState.value.copy(
            scrollToTopTrigger = _uiState.value.scrollToTopTrigger + 1,
            scrollToTopTab = _uiState.value.currentTab
        )
    }

    fun updateCurrentTab(tab: String) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
    }

    fun requestSearchTab() {
        _uiState.value = _uiState.value.copy(
            searchTabRequestId = _uiState.value.searchTabRequestId + 1L
        )
    }

    fun pauseDownload(songId: Long) = downloadEngine.pauseDownload(songId)
    fun resumeDownload(songId: Long) = downloadEngine.resumeDownload(songId)
    fun pauseDownloads(songIds: Set<Long>) = songIds.forEach(downloadEngine::pauseDownload)
    fun resumeDownloads(songIds: Set<Long>) = songIds.forEach(downloadEngine::resumeDownload)
    fun cancelDownload(songId: Long) = downloadEngine.cancelDownload(songId)
    fun dismissCompletedDownload(songId: Long) = downloadEngine.dismissCompletedRecord(songId)
    fun dismissCompletedDownloads() = downloadEngine.dismissAllCompletedRecords()
    fun removeDownload(songId: Long) = downloadEngine.removeTask(songId)

    fun importAccountData(data: String): Boolean {
        if (data.isBlank()) {
            _uiState.value = _uiState.value.copy(statusMessage = "数据不能为空")
            return false
        }

        // 解析导出的数据格式
        val musicU = extractField(data, "MUSIC_U")

        if (musicU.isBlank()) {
            _uiState.value = _uiState.value.copy(statusMessage = "未找到 MUSIC_U 数据")
            return false
        }

        // 尝试导入
        val result = cookieManager.saveMusicUDirect(musicU)
        if (result.success) {
            saveCurrentUserId()
            _uiState.value = _uiState.value.copy(
                isLoggedIn = cookieManager.isLoggedIn(),
                userId = cookieManager.getUserId(),
                statusMessage = "导入成功"
            )
            return true
        } else {
            _uiState.value = _uiState.value.copy(statusMessage = result.message)
            return false
        }
    }

    private fun extractField(data: String, fieldName: String): String {
        val patterns = listOf(
            Regex("\\[$fieldName\\]\\s*\\n([^\\[\\n]+)"),
            Regex("$fieldName:\\s*([^\\n]+)"),
            Regex("\\[$fieldName\\]\\s*([^\\[\\n]+)")
        )
        for (pattern in patterns) {
            val match = pattern.find(data)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return ""
    }

    /**
     * 保存账号到历史记录
     */
    private suspend fun saveAccountHistory(cookies: Map<String, String>) {
        try {
            val loginStatus = repository.loginApi().checkLoginStatus(cookies)
            if (loginStatus.isLoggedIn && loginStatus.userId > 0) {
                val account = AccountHistory(
                    userId = loginStatus.userId,
                    nickname = loginStatus.nickname.ifBlank { "用户${loginStatus.userId}" },
                    cookieData = cookies
                )
                accountHistory.saveAccount(account)
                AppLogger.info("[MainVM] Saved account history: ${account.nickname}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.error("[MainVM] Failed to save account history", e)
        }
    }

    /**
     * 获取历史账号列表
     */
    fun getAccountHistory(): List<AccountHistory> {
        return accountHistory.getAccounts()
    }

    /**
     * 删除历史账号
     */
    fun deleteAccountHistory(userId: Long) {
        accountHistory.deleteAccount(userId)
    }

    /**
     * 切换到历史账号
     */
    fun switchToAccount(account: AccountHistory): Boolean {
        val result = cookieManager.saveCookieMapForLogin(account.cookieData)
        if (result.success) {
            discoveryCache.clearAll()
            saveCurrentUserId()
            _uiState.value = _uiState.value.copy(
                isLoggedIn = cookieManager.isLoggedIn(),
                userId = cookieManager.getUserId(),
                statusMessage = "已切换到 ${account.nickname}"
            )
        } else {
            _uiState.value = _uiState.value.copy(
                statusMessage = "切换失败: ${result.message}"
            )
        }
        return result.success
    }
}