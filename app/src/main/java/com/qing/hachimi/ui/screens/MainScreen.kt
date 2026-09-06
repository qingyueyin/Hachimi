package com.qing.hachimi.ui.screens

import androidx.activity.BackEventCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import com.qing.hachimi.activity.NeteaseWebLoginActivity
import com.qing.hachimi.data.local.SettingsManager
import com.qing.hachimi.data.model.SourceMode
import com.qing.hachimi.downloader.DownloadProgress
import com.qing.hachimi.ui.animation.rememberIsReduceMotionEnabled
import com.qing.hachimi.ui.navigation.AppPredictiveBackHandler
import com.qing.hachimi.ui.navigation.NavTransitionEasing
import com.qing.hachimi.ui.theme.AppShapes
import com.qing.hachimi.ui.theme.AppSpacing
import com.qing.hachimi.util.AppLogger
import com.qing.hachimi.util.HapticLevel
import com.qing.hachimi.util.BuildChannel
import com.qing.hachimi.util.OfficialBuild
import com.qing.hachimi.util.ShareableFile
import com.qing.hachimi.util.StoragePermissionManager
import com.qing.hachimi.util.haptic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog

import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Delete


enum class Tab(val label: String, val icon: ImageVector) {
    DISCOVER("发现", MiuixIcons.Music),
    SEARCH("搜索", MiuixIcons.Search),
    MY("我的", MiuixIcons.Playlist),
    DOWNLOADS("下载", MiuixIcons.Download),
    SETTINGS("设置", MiuixIcons.Settings)
}

private const val DOUBLE_TAP_INTERVAL_MS = 300L
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    discoverViewModel: DiscoverViewModel,
    searchViewModel: SearchViewModel,
    myViewModel: MyViewModel,
    upgradeViewModel: UpgradeViewModel,
    settingsManager: SettingsManager,
    onPickDownloadDir: () -> Unit = {},
    onPickMusicFolder: () -> Unit = {},
    onOpenDownloadedFile: (Long) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val downloadSessionStartedAt = viewModel.downloadSessionStartedAt
    val myUiState by myViewModel.uiState.collectAsStateWithLifecycle()
    val discoverState by discoverViewModel.uiState.collectAsStateWithLifecycle()
    val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()
    var currentTab by remember { mutableStateOf(Tab.DISCOVER) }
    LaunchedEffect(currentTab) {
        AppLogger.currentScreen = "Tab.${currentTab.name}"
        viewModel.updateCurrentTab(currentTab.name)
    }
    var showAppearanceSettings by remember { mutableStateOf(false) }
    var showDownloadSettings by remember { mutableStateOf(false) }
    var showAccountSettings by remember { mutableStateOf(false) }
    var showPreviewFeatures by remember { mutableStateOf(false) }
    var showQualityUpgrade by remember { mutableStateOf(false) }
    var showCookieDialog by remember { mutableStateOf(false) }
    var cookieInput by remember { mutableStateOf("") }
    var cookieError by remember { mutableStateOf<String?>(null) }
    var loginMode by remember { mutableStateOf(0) } // 0 = MUSIC_U, 1 = 完整Cookie
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportedData by remember { mutableStateOf<String>("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var importInput by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    var showReloginDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showBrowserLoginCacheDialog by remember { mutableStateOf(false) }
    var accountHistoryVersion by remember { mutableIntStateOf(0) }
    var skipTabTransition by remember { mutableStateOf(false) }
    var skipSettingsTransition by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(skipTabTransition) {
        if (skipTabTransition) {
            delay(1)
            skipTabTransition = false
        }
    }
    LaunchedEffect(skipSettingsTransition) {
        if (skipSettingsTransition) {
            delay(1)
            skipSettingsTransition = false
        }
    }
    val context = LocalContext.current
    val view = LocalView.current
    var lastTabClickTab by remember { mutableStateOf<Tab?>(null) }
    var lastTabClickTime by remember { mutableLongStateOf(0L) }

    // 底部 Tab 震动反馈分级：切换轻点、滚动到顶部轻刻度、双击回主页确认感
    val haptic: (HapticLevel) -> Unit = { level -> view.haptic(level) }

    // 双击当前 Tab 回到该页主页，单击滚动到顶部，切换 Tab 时触发系统震动反馈
    val onTabClick: (Tab) -> Unit = { tab ->
        val now = SystemClock.uptimeMillis()
        val isDoubleTap = tab == lastTabClickTab && now - lastTabClickTime < DOUBLE_TAP_INTERVAL_MS
        lastTabClickTab = tab
        lastTabClickTime = now
        when {
            tab != currentTab -> {
                haptic(HapticLevel.Medium)
                currentTab = tab
            }
            isDoubleTap -> {
                haptic(HapticLevel.Strong)
                when (tab) {
                    Tab.DISCOVER -> discoverViewModel.resetToHome()
                    Tab.SEARCH -> searchViewModel.resetToHome()
                    Tab.MY -> myViewModel.resetToHome()
                    Tab.SETTINGS -> {
                        showAccountSettings = false
                        showAppearanceSettings = false
                        showDownloadSettings = false
                        showPreviewFeatures = false
                        showQualityUpgrade = false
                        viewModel.scrollToTop()
                    }
                    Tab.DOWNLOADS -> viewModel.scrollToTop()
                }
            }
            else -> {
                haptic(HapticLevel.Light)
                viewModel.scrollToTop()
            }
        }
    }
    val webLoginLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val json = result.data?.getStringExtra(NeteaseWebLoginActivity.RESULT_COOKIE) ?: "{}"
            val cookies = parseCookieJson(json)
            if (viewModel.saveCookieMap(cookies)) {
                discoverViewModel.clearPrivateContent()
                myViewModel.clearAccountContent()
                showCookieDialog = false
                cookieInput = ""
                cookieError = null
            } else {
                cookieError = uiState.statusMessage ?: "浏览器登录失败"
                Toast.makeText(context, cookieError, Toast.LENGTH_SHORT).show()
            }
        } else {
            cookieError = "浏览器登录已取消"
            Toast.makeText(context, cookieError, Toast.LENGTH_SHORT).show()
        }
    }

    val openBrowserLogin = {
        AppLogger.debug("Netease browser login requested")
        cookieError = null
        // 每次都询问用户是否清除缓存
        showBrowserLoginCacheDialog = true
    }

    val openCookieLoginDialog = { mode: Int ->
        AppLogger.debug("Cookie login dialog requested, mode=$mode")
        loginMode = mode
        cookieInput = ""
        cookieError = null
        showCookieDialog = true
    }

    // First-launch disclaimer dialog (免责声明)
    var showDisclaimerDialog by remember { mutableStateOf(!settingsManager.disclaimerAccepted) }
    // First-launch storage permission check (Android 11+)
    // Wait until the disclaimer dialog is dismissed, so the two dialogs don't overlap.
    LaunchedEffect(showDisclaimerDialog) {
        if (!showDisclaimerDialog) {
            val dir = settingsManager.getEffectiveDownloadDir()
            val granted = StoragePermissionManager.hasAllFilesAccess()
            val writable = StoragePermissionManager.probeCanWrite(dir)
            val needsPermission = !granted || !writable
            AppLogger.debug("StoragePermission: granted=$granted writable=$writable needsPermission=$needsPermission")
            if (needsPermission) {
                showPermissionDialog = true
            }
        }
    }

    // Storage permission hardening: compatibility-layer aware settings jump + re-check on resume.
    // 兼容层（卓易通等）可能不存在「所有文件访问」设置页，必须走三级降级跳转；
    // 从设置页返回后需复查授权状态并做真实写入探测（开关状态可能失真）。
    val storageCompatLayer = remember { StoragePermissionManager.detectCompatibilityLayer(context) }
    var pendingStoragePermissionCheck by remember { mutableStateOf(false) }
    val openStoragePermissionSettings = {
        val launched = StoragePermissionManager.openAllFilesAccessSettings(context)
        if (launched) {
            pendingStoragePermissionCheck = true
            AppLogger.debug("StoragePermission: settings page launched")
        } else {
            Toast.makeText(
                context,
                "无法打开系统设置，请手动进入 系统设置 → 应用管理 授予所有文件访问权限",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && pendingStoragePermissionCheck) {
                pendingStoragePermissionCheck = false
                when {
                    StoragePermissionManager.hasAllFilesAccess() &&
                        StoragePermissionManager.probeCanWrite(settingsManager.getEffectiveDownloadDir()) -> {
                        AppLogger.debug("StoragePermission: granted and writable")
                        Toast.makeText(context, "存储权限已授予", Toast.LENGTH_SHORT).show()
                    }
                    StoragePermissionManager.hasAllFilesAccess() -> {
                        AppLogger.warn("StoragePermission: granted but probe write failed")
                        Toast.makeText(
                            context,
                            "系统已授权但目录仍不可写，建议在下载设置中更换下载目录",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> {
                        AppLogger.warn("StoragePermission: not granted after returning from settings")
                        Toast.makeText(context, "未授予所有文件访问权限，下载将失败", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Show back button on SEARCH tab when displaying content from DISCOVER
    val showSearchBack = currentTab == Tab.SEARCH && uiState.previousSourceMode != SourceMode.SEARCH

    val showDiscoverBack = currentTab == Tab.DISCOVER && discoverState.canNavigateBack
    val showMyBack = currentTab == Tab.MY && (myUiState.section != MySection.HOME || myUiState.isPlaylistDetail)
    val showSearchSubBack = currentTab == Tab.SEARCH && searchState.isDetailView
    val contextBackTarget = resolveContextBackTarget(
        showAppearanceSettings = currentTab == Tab.SETTINGS && showAppearanceSettings,
        showDownloadSettings = currentTab == Tab.SETTINGS && showDownloadSettings,
        showAccountSettings = currentTab == Tab.SETTINGS && showAccountSettings,
        showPreviewFeatures = currentTab == Tab.SETTINGS && showPreviewFeatures,
        showQualityUpgrade = currentTab == Tab.SETTINGS && showQualityUpgrade,
        showSearchBack = showSearchBack,
        showDiscoverBack = showDiscoverBack,
        showMyPlaylistBack = showMyBack,
        showSearchSubBack = showSearchSubBack,
    )
    val showContextHeader = contextBackTarget?.usesContextHeader == true

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let {
            delay(3000)
            viewModel.clearStatus()
        }
    }

    LaunchedEffect(uiState.searchTabRequestId) {
        if (uiState.searchTabRequestId > 0L) {
            currentTab = Tab.SEARCH
        }
    }

    LaunchedEffect(showCookieDialog) {
        if (showCookieDialog) {
            cookieError = null
        }
    }

    val mainBottomContentPadding = floatingContentBottomPadding(
        systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        hasFloatingBar = true,
    )

    val backdrop = rememberBlurBackdrop(
        enableBlur = shouldCaptureFloatingBackdrop(uiState.blurEffect, uiState.liquidGlass),
    )

    // 顶部磨砂层依据的滚动距离（px）：各 Tab 的滚动容器通过 nestedScroll 上报实际消费的位移。
    // 距离顶部越近越透明，完全到顶为 0（不显示）；远离顶部时固定为满模糊。
    // 同一 Tab 的页面分别保存进度，避免列表页的滚动距离泄漏到新打开的详情页。
    val density = LocalDensity.current
    val topChromePageKey = resolveTopChromePageKey(
        currentTab = currentTab,
        contextBackTarget = contextBackTarget,
        discoverState = discoverState,
        searchState = searchState,
        myState = myUiState,
    )
    val topChromeScrollOffsets = remember {
        mutableMapOf<TopChromePageKey, MutableFloatState>()
    }
    val topChromeScrollOffset = remember(topChromePageKey) {
        topChromeScrollOffsets.getOrPut(topChromePageKey) { mutableFloatStateOf(0f) }
    }
    LaunchedEffect(currentTab) {
        topChromeScrollOffsets.keys.removeAll { it.tab != currentTab }
        topChromeScrollOffset.floatValue = 0f
    }

    val performContextBack: () -> Unit = {
        when (contextBackTarget) {
            ContextBackTarget.APPEARANCE_SETTINGS -> showAppearanceSettings = false
            ContextBackTarget.DOWNLOAD_SETTINGS -> showDownloadSettings = false
            ContextBackTarget.ACCOUNT_SETTINGS -> showAccountSettings = false
            ContextBackTarget.QUALITY_UPGRADE -> showQualityUpgrade = false
            ContextBackTarget.PREVIEW_FEATURES -> showPreviewFeatures = false
            ContextBackTarget.SEARCH_DETAIL -> searchViewModel.goBack()
            ContextBackTarget.DISCOVER -> {
                val returnToSearch = discoverState.shouldReturnToSearch
                discoverViewModel.goBack()
                if (returnToSearch) currentTab = Tab.SEARCH
            }
            ContextBackTarget.MY_SECTION -> {
                if (myUiState.isPlaylistDetail) myViewModel.clearSongs() else myViewModel.backToHome()
            }
            ContextBackTarget.LEGACY_SEARCH -> {
                val previousSource = uiState.previousSourceMode
                viewModel.goBack()
                if (previousSource != SourceMode.SEARCH && previousSource != SourceMode.ARTIST_DETAIL) {
                    currentTab = Tab.DISCOVER
                }
            }
            null -> Unit
        }
    }

    // Predictive back: 手势进度直接驱动内容位移/缩放（按钮按压走"无进度立即提交"路径）
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

    // Keep last known target alive so background preview is visible during gesture
    var lastContextBackTarget by remember { mutableStateOf<ContextBackTarget?>(null) }
    if (contextBackTarget != null) lastContextBackTarget = contextBackTarget

    // 手势提交期间跳过 tab 内部层级过渡（手势本身就是过渡）
    var suppressNavTransition by remember { mutableStateOf(false) }

    var backCommitPending by remember { mutableStateOf(false) }
    val latestContextBackTarget by rememberUpdatedState(contextBackTarget)
    LaunchedEffect(backCommitPending) {
        if (!backCommitPending) return@LaunchedEffect
        var waitedFrames = 0
        while (latestContextBackTarget != null && waitedFrames < 6) {
            withFrameNanos { }
            waitedFrames++
        }
        lastContextBackTarget = null
        backProgress = 0f
        withFrameNanos { }
        suppressNavTransition = false
        backCommitPending = false
    }

    AppPredictiveBackHandler(
        enabled = contextBackTarget != null,
        onBackProgress = { event ->
            if (backCommitPending) {
                backCommitPending = false
                suppressNavTransition = false
            }
            backProgress = event.progress
            backSwipeEdge = event.swipeEdge
        },
        onBackSettled = {
            if (!backCommitPending) {
                lastContextBackTarget = null
                backProgress = 0f
            }
        },
        onBack = {
            backCommitPending = true
            suppressNavTransition = true
            performContextBack()
        },
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Background preview: shows target page during predictive back gesture
        if (backProgress > 0f && lastContextBackTarget != null) {
            val target = lastContextBackTarget!!
            val previewInsets = WindowInsets.systemBars.asPaddingValues()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.surface)
                    .graphicsLayer {
                        val direction = if (backSwipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                        val screenWidth = android.content.res.Resources.getSystem().displayMetrics.widthPixels.toFloat()
                        translationX = screenWidth * direction * (1f - NavTransitionEasing.transform(backProgress))
                    }
            ) {
                ContextBackPreview(
                    target = target,
                    uiState = uiState,
                    viewModel = viewModel,
                    searchViewModel = searchViewModel,
                    discoverViewModel = discoverViewModel,
                    myViewModel = myViewModel,
                    currentTab = currentTab,
                    innerPadding = PaddingValues(
                        top = previewInsets.calculateTopPadding() + AppSpacing.sm,
                        bottom = previewInsets.calculateBottomPadding(),
                        start = previewInsets.calculateStartPadding(LocalLayoutDirection.current),
                        end = previewInsets.calculateEndPadding(LocalLayoutDirection.current),
                    ),
                )
            }
        }

        // Foreground
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val direction = if (backSwipeEdge == BackEventCompat.EDGE_RIGHT) 1f else -1f
                    val screenWidth = android.content.res.Resources.getSystem().displayMetrics.widthPixels.toFloat()
                    translationX = screenWidth * direction * NavTransitionEasing.transform(backProgress)
                    val scale = 1f - backProgress * 0.05f
                    scaleX = scale
                    scaleY = scale
                }
        ) {
        CompositionLocalProvider(LocalSuppressNavTransition provides suppressNavTransition) {
        Scaffold(
            topBar = {
                if (showContextHeader) {
                    ContextPageHeader(
                        onBackClick = performContextBack,
                        customTitle = when {
                            showDiscoverBack -> discoverState.navigationTitle
                            showSearchSubBack -> searchState.playlistName.ifEmpty { "歌曲" }
                            else -> currentTab.label
                        },
                        actions = {
                            if (
                                showDiscoverBack &&
                                !discoverState.isDetailView &&
                                !discoverState.isLoading &&
                                discoverState.sourceMode != SourceMode.RECOMMEND
                            ) {
                                DiscoverCategoryActions(
                                    sourceMode = discoverState.sourceMode,
                                    columns = discoverState.gridColumns,
                                    onRefresh = discoverViewModel::refreshCurrentSource,
                                    onSetColumns = discoverViewModel::setGridColumns,
                                )
                            }
                        },
                    )
                } else {
                    PrimaryPageTopInset()
                }
            },
            contentWindowInsets = WindowInsets.systemBars
                .only(WindowInsetsSides.Horizontal)
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
                ) {
                    CompositionLocalProvider(
                        LocalMainBottomContentPadding provides mainBottomContentPadding,
                    ) {
                        TabContent(
                    currentTab = currentTab,
                    uiState = uiState,
                    discoverViewModel = discoverViewModel,
                    searchViewModel = searchViewModel,
                    myViewModel = myViewModel,
                    upgradeViewModel = upgradeViewModel,
                    downloadProgress = downloadProgress,
                    viewModel = viewModel,
                    innerPadding = innerPadding,
                    onOpenDownloadedFile = onOpenDownloadedFile,
                    onPickDownloadDir = onPickDownloadDir,
                    onPickMusicFolder = onPickMusicFolder,
                    onSwitchToDiscover = { currentTab = Tab.DISCOVER },
                    onMyBack = performContextBack,
                    onBrowserLoginClick = openBrowserLogin,
                    onCookieLoginClick = { openCookieLoginDialog(1) },
                    onMusicULoginClick = { openCookieLoginDialog(0) },
                    onLogout = {
                        showLogoutDialog = true
                    },
                    onExportCookies = {
                        val data = viewModel.getExportAccountData()
                        exportedData = buildString {
                            append("=== Hachimi 账户数据 ===\n\n")
                            append("[MUSIC_U]\n${data.musicU}\n\n")
                            append("[__csrf]\n${data.csrf}\n\n")
                            append("[MUSIC_A]\n${data.musicA}\n\n")
                            append("[NMTID]\n${data.nmtid}\n\n")
                            append("[WEVNSM]\n${data.wevnsm}\n\n")
                            append("[WNMCID]\n${data.wnmcid}\n\n")
                            if (data.userId > 0L) {
                                append("[用户ID]\n${data.userId}\n\n")
                            }
                            append("[完整Cookie]\n${data.rawCookies}\n")
                        }
                        showExportDialog = true
                    },
                    context = context,
                    showAppearanceSettings = showAppearanceSettings,
                    showDownloadSettings = showDownloadSettings,
                    showAccountSettings = showAccountSettings,
                    showPreviewFeatures = showPreviewFeatures,
                    showQualityUpgrade = showQualityUpgrade,
                    onOpenAppearanceSettings = { showAppearanceSettings = true },
                    onOpenDownloadSettings = { showDownloadSettings = true },
                    onOpenAccountSettings = { showAccountSettings = true },
                    onOpenPreviewFeatures = { showPreviewFeatures = true },
                    onOpenQualityUpgrade = { showPreviewFeatures = true; showQualityUpgrade = true },
                    onCloseAppearanceSettings = performContextBack,
                    onCloseDownloadSettings = performContextBack,
                    onCloseAccountSettings = performContextBack,
                    onClosePreviewFeatures = performContextBack,
                    onCloseQualityUpgrade = performContextBack,
                    onToggleBlurEffect = viewModel::toggleBlurEffect,
                    onToggleLiquidGlass = viewModel::toggleLiquidGlass,
                    onImportClick = { showImportDialog = true },
                    onOpenStoragePermissionSettings = openStoragePermissionSettings,
                    skipTabTransition = skipTabTransition,
                    skipSettingsTransition = skipSettingsTransition,
                    sessionStartedAt = downloadSessionStartedAt,
                    topChromeScrollOffset = topChromeScrollOffset,
                        )
                    }
                }

                // 顶部渐变模糊层：位于 body 之上、Scaffold topBar（标题栏）之下。
                // 由“距顶部距离”决定透明度：远离顶部（已滚过标题区）时满模糊固定显示；
                // 仅当滚动接近顶部（滚动偏移小于标题区+fade 高度）时才渐出，回顶即完全取消。
                // topBar 在 Scaffold 中被绘制在此层之上，标题保持清晰。
                // 搜索主页顶部有冻结的搜索框（自带背景），无需磨砂，强制隐藏；
                // 搜索详情页（showContextHeader）仍保留磨砂以支持透明标题栏。
                val topChromeFadeDistancePx = with(density) {
                    (if (showContextHeader) 44.dp + 96.dp else AppSpacing.sm + 56.dp).toPx()
                }
                val topChromeTarget = if (showContextHeader || currentTab != Tab.SEARCH) {
                    (topChromeScrollOffset.floatValue / topChromeFadeDistancePx).coerceIn(0f, 1f)
                } else 0f
                val topChromeAlpha by animateFloatAsState(
                    targetValue = topChromeTarget,
                    animationSpec = if (suppressNavTransition) {
                        snap()
                    } else {
                        tween(durationMillis = 220, easing = FastOutSlowInEasing)
                    },
                    label = "TopChromeAlpha",
                )
                TopChromeOverlay(
                    backdrop = backdrop.takeIf { uiState.blurEffect },
                    headerHeight = if (showContextHeader) 44.dp else AppSpacing.sm,
                    fadeExtension = if (showContextHeader) 96.dp else 56.dp,
                    progress = topChromeAlpha,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
        }
        }

        // 底部操作区（Bottom bar），置于前景之外，实时模糊始终正确
        MainBottomBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            currentTab = currentTab,
            isLoggedIn = uiState.isLoggedIn,
            backdrop = backdrop,
            isBlurEnabled = uiState.blurEffect,
            isLiquidGlassEnabled = uiState.liquidGlass,
            onTabChange = onTabClick,
        )
    }

        // Dialogs live outside Scaffold, so use window dialogs instead of Scaffold-hosted overlays.
    WindowDialog(
        show = showCookieDialog,
        title = if (loginMode == 0) "MUSIC_U 登录" else "Cookie 登录",
        summary = if (loginMode == 0) "直接粘贴 MUSIC_U 值即可" else "从浏览器复制完整 Cookie 后粘贴",
        onDismissRequest = {
            showCookieDialog = false
            cookieInput = ""
            cookieError = null
        }
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            // TextField
            TextField(
                value = cookieInput,
                onValueChange = { cookieInput = it; cookieError = null },
                label = if (cookieError != null) cookieError!!
                    else if (loginMode == 0) "粘贴 MUSIC_U 值"
                    else "MUSIC_U=xxx; __csrf=xxx; ...",
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )

            if (cookieError != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    cookieError!!,
                    color = colorScheme.error,
                    style = MiuixTheme.textStyles.body2
                )
            }

            Spacer(Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    text = "取消",
                    onClick = {
                        showCookieDialog = false
                        cookieInput = ""
                        cookieError = null
                    }
                )
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = {
                        AppLogger.debug("Cookie login attempt, mode=$loginMode")
                        val result = if (loginMode == 0) {
                            viewModel.saveMusicUDirect(cookieInput.trim())
                        } else {
                            viewModel.saveCookies(cookieInput.trim())
                        }
                        if (result) {
                            discoverViewModel.clearPrivateContent()
                            myViewModel.clearAccountContent()
                            showCookieDialog = false
                            cookieInput = ""
                            cookieError = null
                        } else {
                            cookieError = uiState.statusMessage ?: "格式错误"
                            AppLogger.warn("Cookie login failed: ${cookieError}")
                        }
                    }
                ) {
                    Text("登录")
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    DisclaimerDialog(
        show = showDisclaimerDialog,
        requireAccept = true,
        onDismiss = {},
        onAccept = {
            settingsManager.disclaimerAccepted = true
            showDisclaimerDialog = false
        },
    )

    var showUnofficialWarning by remember {
        mutableStateOf(
            OfficialBuild.channel(context) == BuildChannel.UNOFFICIAL &&
                !settingsManager.unofficialBuildWarningDismissed,
        )
    }
    WindowDialog(
        show = !showDisclaimerDialog && showUnofficialWarning,
        title = "非官方构建",
        summary = "当前安装包的签名不是官方密钥，可能被改包或倒卖。请从 GitHub Releases 下载官方包。",
        onDismissRequest = {
            settingsManager.unofficialBuildWarningDismissed = true
            showUnofficialWarning = false
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                text = "稍后再说",
                onClick = {
                    settingsManager.unofficialBuildWarningDismissed = true
                    showUnofficialWarning = false
                },
            )
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = {
                    settingsManager.unofficialBuildWarningDismissed = true
                    showUnofficialWarning = false
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(OfficialBuild.RELEASES_URL)),
                    )
                },
            ) {
                Text("打开官方页面")
            }
        }
    }

    // Storage permission dialog (Android 11+)
    WindowDialog(
        show = showPermissionDialog,
        title = "存储权限",
        summary = "保存到公共目录需要「所有文件访问权限」。请在接下来的系统页面中开启此权限。未授权时下载会失败。" +
            (storageCompatLayer?.let { "\n\n检测到 $it 环境：若找不到对应开关，请到 $it 的应用设置中开启。" } ?: ""),
        onDismissRequest = { showPermissionDialog = false }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                text = "稍后",
                onClick = { showPermissionDialog = false }
            )
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = {
                    showPermissionDialog = false
                    openStoragePermissionSettings()
                }
            ) {
                Text("去授权")
            }
        }
    }

    // Re-login dialog
    WindowDialog(
        show = showReloginDialog,
        title = "已登录",
        summary = "是否清除 WebView 缓存重新登录？",
        onDismissRequest = { showReloginDialog = false }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                text = "取消",
                onClick = { showReloginDialog = false }
            )
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = {
                    showReloginDialog = false
                    val intent = Intent(context, NeteaseWebLoginActivity::class.java)
                    intent.putExtra(NeteaseWebLoginActivity.EXTRA_CLEAR_CACHE, true)
                    webLoginLauncher.launch(intent)
                }
            ) {
                Text("重新登录")
            }
        }
    }

    WindowDialog(
        show = showLogoutDialog,
        title = "退出登录",
        summary = "将清除本机保存的登录状态和当前账户内容。下载记录不会受影响。",
        onDismissRequest = { showLogoutDialog = false }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                text = "取消",
                onClick = { showLogoutDialog = false }
            )
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = {
                    showLogoutDialog = false
                    viewModel.logout()
                    discoverViewModel.clearPrivateContent()
                    myViewModel.clearAccountContent()
                    showCookieDialog = false
                },
                colors = ButtonDefaults.buttonColors(
                    color = colorScheme.error,
                    contentColor = colorScheme.onError,
                )
            ) {
                Text("退出")
            }
        }
    }

    // Export account data dialog
    WindowDialog(
        show = showExportDialog,
        title = "导出账户数据",
        summary = "请妥善保管，不要泄露给他人",
        summaryColor = colorScheme.error,
        onDismissRequest = { showExportDialog = false }
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            // Scrollable text area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorScheme.surfaceContainer)
                    .padding(12.dp),
            ) {
                Text(
                    text = exportedData,
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    // Copy button
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Hachimi账户数据", exportedData))
                        }
                    ) {
                        Text("复制")
                    }
                    Spacer(Modifier.width(12.dp))
                    // Share button
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, exportedData)
                            }
                            context.startActivity(Intent.createChooser(intent, "分享账户数据"))
                        }
                    ) {
                        Text("分享")
                    }
                }
                TextButton(
                    text = "关闭",
                    onClick = { showExportDialog = false }
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    // Import account data dialog
    WindowDialog(
        show = showImportDialog,
        title = "导入账户数据",
        summary = "粘贴之前导出的完整账户数据",
        onDismissRequest = {
            showImportDialog = false
            importInput = ""
            importError = null
        }
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            // 输入框
            TextField(
                value = importInput,
                onValueChange = { importInput = it; importError = null },
                label = importError ?: "粘贴导出的数据",
                modifier = Modifier.fillMaxWidth(),
                minLines = 8,
                maxLines = 12
            )

            if (importError != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    importError!!,
                    color = colorScheme.error,
                    style = MiuixTheme.textStyles.body2
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    text = "取消",
                    onClick = {
                        showImportDialog = false
                        importInput = ""
                        importError = null
                    }
                )
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = {
                        val result = viewModel.importAccountData(importInput.trim())
                        if (result) {
                            showImportDialog = false
                            importInput = ""
                            importError = null
                        } else {
                            importError = uiState.statusMessage ?: "导入失败：数据格式错误"
                        }
                    }
                ) {
                    Text("导入")
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    // 浏览器登录缓存询问对话框
    WindowDialog(
        show = showBrowserLoginCacheDialog,
        title = "浏览器登录",
        summary = "选择账号或新建登录",
        onDismissRequest = { showBrowserLoginCacheDialog = false }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .heightIn(max = 500.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 历史账号列表
            val accounts = remember(showBrowserLoginCacheDialog, accountHistoryVersion) {
                viewModel.getAccountHistory()
            }

            if (accounts.isNotEmpty()) {
                Text(
                    "历史账号",
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface
                )

                Spacer(Modifier.height(4.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(accounts.size) { index ->
                        val account = accounts[index]
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                                .clickable {
                                    showBrowserLoginCacheDialog = false
                                    if (viewModel.switchToAccount(account)) {
                                        discoverViewModel.clearPrivateContent()
                                        myViewModel.clearAccountContent()
                                        Toast.makeText(context, "已切换到 ${account.nickname}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        account.nickname,
                                        fontWeight = FontWeight.Medium,
                                        color = colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        "UID: ${account.userId}",
                                        style = MiuixTheme.textStyles.body2,
                                        color = colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.deleteAccountHistory(account.userId)
                                        accountHistoryVersion++
                                    }
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Delete,
                                        contentDescription = "删除",
                                        tint = colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = colorScheme.outline.copy(alpha = 0.3f)
                )
            }

            // 快速登录（保留缓存）
            Button(
                onClick = {
                    showBrowserLoginCacheDialog = false
                    webLoginLauncher.launch(Intent(context, NeteaseWebLoginActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("快速登录", fontWeight = FontWeight.Medium)
                    Text(
                        "使用浏览器缓存（如果可用）",
                        style = MiuixTheme.textStyles.body2,
                        color = colorScheme.onPrimary.copy(alpha = 0.95f)
                    )
                }
            }

            // 清除缓存重新登录
            Button(
                onClick = {
                    showBrowserLoginCacheDialog = false
                    val intent = Intent(context, NeteaseWebLoginActivity::class.java)
                    intent.putExtra(NeteaseWebLoginActivity.EXTRA_CLEAR_CACHE, true)
                    webLoginLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    color = colorScheme.secondaryContainer,
                    contentColor = colorScheme.onSecondaryContainer
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("新账号登录", fontWeight = FontWeight.Medium)
                    Text(
                        "清除浏览器缓存，重新登录",
                        style = MiuixTheme.textStyles.body2,
                        color = colorScheme.onSecondaryContainer.copy(alpha = 0.95f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}


@Composable
private fun MainBottomBar(
    modifier: Modifier = Modifier,
    currentTab: Tab,
    isLoggedIn: Boolean,
    backdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop?,
    isBlurEnabled: Boolean,
    isLiquidGlassEnabled: Boolean,
    onTabChange: (Tab) -> Unit,
) {
    val visibleTabs = visibleMainTabs(isLoggedIn)
    val selectedTabIndex = visibleTabs.indexOf(currentTab).coerceAtLeast(0)
    BlurNavigationBar(
        modifier = modifier,
        backdrop = backdrop,
        selectedIndex = selectedTabIndex,
        itemCount = visibleTabs.size,
        isBlurEnabled = isBlurEnabled,
        isLiquidGlassEnabled = isLiquidGlassEnabled,
        onSelectedIndexChange = { index ->
            visibleTabs.getOrNull(index)?.let(onTabChange)
        },
    ) {
        visibleTabs.forEachIndexed { index, tab ->
            BlurNavigationBarItem(
                index = index,
                selected = tab == currentTab,
                onClick = { onTabChange(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(22.dp),
                    )
                },
                label = tab.label,
            )
        }
    }
}

private fun parseCookieJson(json: String): Map<String, String> {
    return runCatching {
        val obj = JSONObject(json)
        val out = linkedMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = obj.optString(key, "")
        }
        out
    }.getOrElse { emptyMap() }
}

internal enum class ContextBackTarget(val usesContextHeader: Boolean) {
    APPEARANCE_SETTINGS(false),
    DOWNLOAD_SETTINGS(false),
    ACCOUNT_SETTINGS(false),
    PREVIEW_FEATURES(false),
    QUALITY_UPGRADE(false),
    SEARCH_DETAIL(true),
    DISCOVER(true),
    MY_SECTION(false),
    LEGACY_SEARCH(true),
}

internal fun resolveContextBackTarget(
    showAppearanceSettings: Boolean,
    showDownloadSettings: Boolean,
    showAccountSettings: Boolean,
    showSearchBack: Boolean,
    showDiscoverBack: Boolean,
    showMyPlaylistBack: Boolean,
    showSearchSubBack: Boolean,
    showPreviewFeatures: Boolean = false,
    showQualityUpgrade: Boolean = false,
): ContextBackTarget? = when {
    showQualityUpgrade -> ContextBackTarget.QUALITY_UPGRADE
    showPreviewFeatures -> ContextBackTarget.PREVIEW_FEATURES
    showAppearanceSettings -> ContextBackTarget.APPEARANCE_SETTINGS
    showDownloadSettings -> ContextBackTarget.DOWNLOAD_SETTINGS
    showAccountSettings -> ContextBackTarget.ACCOUNT_SETTINGS
    showSearchSubBack -> ContextBackTarget.SEARCH_DETAIL
    showDiscoverBack -> ContextBackTarget.DISCOVER
    showMyPlaylistBack -> ContextBackTarget.MY_SECTION
    showSearchBack -> ContextBackTarget.LEGACY_SEARCH
    else -> null
}

internal data class TopChromePageKey(
    val tab: Tab,
    val route: String,
)

internal fun resolveTopChromePageKey(
    currentTab: Tab,
    contextBackTarget: ContextBackTarget?,
    discoverState: DiscoverUiState,
    searchState: SearchUiState,
    myState: MyUiState,
): TopChromePageKey {
    fun detailIdentity(detail: com.qing.hachimi.data.model.CollectionDetail?): String {
        if (detail == null) return "none"
        val fallbackTitle = if (detail.id == 0L) detail.title else ""
        return "${detail.kind.name}:${detail.id}:$fallbackTitle"
    }

    val route = when (currentTab) {
        Tab.DISCOVER -> listOf(
            contextBackTarget?.name,
            discoverState.sourceMode.name,
            detailIdentity(discoverState.collectionDetail),
            discoverState.activeArtist?.id,
            discoverState.activePodcast?.id,
            discoverState.sourceLabel,
            discoverState.playlistTag,
            discoverState.playlistCategory.name,
            discoverState.artistArea.name,
            discoverState.newSongsArea,
            discoverState.artistDetailSection.name,
        ).joinToString("|")
        Tab.SEARCH -> listOf(
            contextBackTarget?.name,
            searchState.isDetailView,
            detailIdentity(searchState.detailHeader),
            searchState.searchCategory.name,
        ).joinToString("|")
        Tab.MY -> listOf(
            contextBackTarget?.name,
            myState.section.name,
            myState.isPlaylistDetail,
            if (myState.isPlaylistDetail) myState.playlistName else "",
        ).joinToString("|")
        Tab.SETTINGS -> contextBackTarget?.name.orEmpty()
        Tab.DOWNLOADS -> currentTab.name
    }
    return TopChromePageKey(tab = currentTab, route = route)
}

internal fun calculateTopChromeScrollOffset(
    currentOffset: Float,
    consumedY: Float,
): Float = (currentOffset - consumedY).coerceAtLeast(0f)

@Composable
private fun PrimaryPageTopInset() {
    TopChrome {
        Column {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(AppSpacing.sm))
        }
    }
}

@Composable
private fun ContextPageHeader(
    onBackClick: () -> Unit,
    customTitle: String,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopChrome {
        Column {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            SecondaryPageHeader(
                title = customTitle,
                onBack = onBackClick,
                actions = actions,
            )
        }
    }
}

@Composable
private fun TopChrome(
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        content()
    }
}

/**
 * 顶部渐变模糊层。
 *
 * 覆盖在页面内容之上、标题栏之下，从状态栏一直向下延伸一段距离，
 * 让标题和从标题下滚过的内容都落在同一条模糊渐变上，过渡更自然。
 */
@Composable
private fun TopChromeOverlay(
    backdrop: LayerBackdrop?,
    headerHeight: Dp,
    fadeExtension: Dp,
    progress: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val surface = colorScheme.surface
    val bodyTint = surface.copy(alpha = if (backdrop != null) 0.28f else 0.78f)
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val solidHeight = statusBarHeight + headerHeight
    val totalHeight = solidHeight + fadeExtension
    val solidFraction = (solidHeight / totalHeight).coerceIn(0f, 1f)
    // 标题区域保持不透，再向下平滑淡出，避免滚动时出现生硬截断
    val chromeMask = Brush.verticalGradient(
        0f to androidx.compose.ui.graphics.Color.Black,
        solidFraction to androidx.compose.ui.graphics.Color.Black,
        1f to androidx.compose.ui.graphics.Color.Transparent,
    )
    val chromeTint = Brush.verticalGradient(
        0f to bodyTint,
        solidFraction to bodyTint,
        1f to surface.copy(alpha = 0f),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
            .graphicsLayer { alpha = progress },
    ) {
        if (backdrop != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .textureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        blurRadius = with(density) { 24.dp.toPx() },
                        colors = BlurColors(saturation = 1.08f),
                        contentBlendMode = BlendMode.DstIn,
                    )
                    .background(chromeMask),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    drawRect(brush = chromeTint, size = size)
                },
        )
    }
}


@Composable
private fun TabContent(
    currentTab: Tab,
    uiState: MainUiState,
    downloadProgress: Map<Long, DownloadProgress>,
    viewModel: MainViewModel,
    discoverViewModel: DiscoverViewModel,
    searchViewModel: SearchViewModel,
    myViewModel: MyViewModel,
    upgradeViewModel: UpgradeViewModel,
    innerPadding: PaddingValues,
    onOpenDownloadedFile: (Long) -> Unit,
    onPickDownloadDir: () -> Unit,
    onPickMusicFolder: () -> Unit,
    onSwitchToDiscover: () -> Unit,
    onMyBack: () -> Unit,
    onBrowserLoginClick: () -> Unit,
    onCookieLoginClick: () -> Unit,
    onMusicULoginClick: () -> Unit,
    onLogout: () -> Unit,
    onExportCookies: () -> Unit,
    context: android.content.Context,
    showAppearanceSettings: Boolean,
    showDownloadSettings: Boolean,
    showAccountSettings: Boolean,
    showPreviewFeatures: Boolean = false,
    showQualityUpgrade: Boolean = false,
    onOpenAppearanceSettings: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    onOpenAccountSettings: () -> Unit,
    onOpenPreviewFeatures: () -> Unit = {},
    onOpenQualityUpgrade: () -> Unit = {},
    onCloseAppearanceSettings: () -> Unit,
    onCloseDownloadSettings: () -> Unit,
    onCloseAccountSettings: () -> Unit,
    onClosePreviewFeatures: () -> Unit = {},
    onCloseQualityUpgrade: () -> Unit = {},
    onToggleBlurEffect: () -> Unit = {},
    onToggleLiquidGlass: () -> Unit = {},
    onImportClick: () -> Unit,
    onOpenStoragePermissionSettings: () -> Unit = {},
    skipTabTransition: Boolean = false,
    skipSettingsTransition: Boolean = false,
    sessionStartedAt: Long = 0L,
    topChromeScrollOffset: MutableFloatState = mutableFloatStateOf(0f),
) {
    val horizontalDirection = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val density = LocalDensity.current
    val reduceMotion = rememberIsReduceMotionEnabled()
    val scrollToTopTrigger =
        if (uiState.scrollToTopTab == currentTab.name) uiState.scrollToTopTrigger else 0
    // 捕获子级实际消费的滚动位移，累积为“距顶部距离”（px）：
    // - 内容上移（查看后方内容）时 consumed.y 为负 → 偏移增大 → 磨砂渐入至满
    // - 回滚时偏移减小，仅当接近顶部（偏移 < 标题区高度）才渐出，到顶为 0（取消）
    // - 忽略 available.y，避免把列表边界上的未消费回弹计入偏移
    val topChromeConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (consumed.y != 0f) {
                    topChromeScrollOffset.floatValue =
                        calculateTopChromeScrollOffset(topChromeScrollOffset.floatValue, consumed.y)
                }
                return Offset.Zero
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .nestedScroll(topChromeConnection),
    ) {
        AnimatedContent(
            targetState = currentTab,
        transitionSpec = {
            if (skipTabTransition) {
                fadeIn(animationSpec = tween(0)) togetherWith fadeOut(animationSpec = tween(0))
            } else {
                directionalTabTransition(
                    isForward = targetState.ordinal > initialState.ordinal,
                    layoutDirection = horizontalDirection,
                    reduceMotion = reduceMotion,
                    density = density,
                )
            }
        },
        label = "TabTransition"
    ) { tab ->
        Box(
            Modifier
                .fillMaxSize()
                .zIndex(if (tab == currentTab) 1f else 0f)
                .then(if (tab == currentTab) Modifier else Modifier.hideTransitionSemantics())
        ) {
            when (tab) {
            Tab.DISCOVER -> {
                val discoverState by discoverViewModel.uiState.collectAsStateWithLifecycle()
                DiscoverTab(
                    uiState = discoverState,
                    isLoggedIn = uiState.isLoggedIn,
                    downloadProgress = downloadProgress,
                    onToggleSelect = discoverViewModel::toggleSongSelection,
                    onSelectAll = discoverViewModel::toggleSelectAll,
                    onDownload = discoverViewModel::downloadSelected,
                    onSetSourceMode = discoverViewModel::setSourceMode,
                    onAlbumClick = discoverViewModel::loadAlbumFromResult,
                    onChartClick = discoverViewModel::loadChartSongs,
                    onLoadRecommendSongs = { discoverViewModel.loadRecommendSongsToPlaylist() },
                    onPlaylistClick = discoverViewModel::loadPlaylistSongs,
                    onPodcastClick = discoverViewModel::loadPodcastPrograms,
                    onArtistClick = { artist -> discoverViewModel.loadArtistDetail(artist) },
                    onLoadNextFmGroup = discoverViewModel::loadNextFmGroup,
                    onSetPlaylistCategory = discoverViewModel::setPlaylistCategory,
                    onSetArtistArea = discoverViewModel::setArtistArea,
                    onSetNewSongsArea = discoverViewModel::setNewSongsArea,
                    onSelectStyleTag = discoverViewModel::selectStyleTag,
                    onSetArtistDetailSection = discoverViewModel::setArtistDetailSection,
                    onLoadMore = discoverViewModel::loadMoreCurrentSource,
                    onLoadMoreDetail = discoverViewModel::loadMorePodcastPrograms,
                    onLoadMoreArtistSection = discoverViewModel::loadMoreArtistSection,
                    onRefresh = { discoverViewModel.refreshCurrentSource() },
                    onRetry = discoverViewModel::retry,
                    innerPadding = innerPadding,
                    scrollToTopTrigger = scrollToTopTrigger,
                )
            }
            Tab.SEARCH -> {
                val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()
                SearchTab(
                    state = searchState,
                    downloadProgress = downloadProgress,
                    onSearchQueryChange = searchViewModel::updateSearchQuery,
                    onSearch = searchViewModel::search,
                    onParseUrl = { searchViewModel.updatePlaylistUrl(it); searchViewModel.parseUrl() },
                    onToggleSelect = searchViewModel::toggleSongSelection,
                    onSelectAll = searchViewModel::toggleSelectAll,
                    onSetSearchCategory = searchViewModel::setSearchCategory,
                    onLoadMore = searchViewModel::loadMoreSearch,
                    onAlbumClick = searchViewModel::loadAlbumFromResult,
                    onArtistClick = { artist ->
                        discoverViewModel.loadArtistDetail(artist, SourceMode.SEARCH)
                        onSwitchToDiscover()
                    },
                    onPlaylistClick = searchViewModel::loadPlaylistFromResult,
                    onPodcastClick = searchViewModel::loadPodcastFromResult,
                    onDownload = searchViewModel::downloadSelected,
                    onRetry = searchViewModel::retry,
                    onHistoryClick = searchViewModel::searchFromHistory,
                    onRemoveHistory = searchViewModel::removeSearchHistory,
                    onClearHistory = searchViewModel::clearSearchHistory,
                    onSuggestionClick = searchViewModel::searchFromSuggestion,
                    innerPadding = innerPadding,
                    scrollToTopTrigger = scrollToTopTrigger,
                )
            }
            Tab.DOWNLOADS -> DownloadsTab(
                downloadProgress = downloadProgress,
                onPause = viewModel::pauseDownload,
                onResume = viewModel::resumeDownload,
                onCancel = viewModel::cancelDownload,
                onRemoveActiveTask = viewModel::removeDownload,
                onDismissCompleted = viewModel::dismissCompletedDownload,
                onDeleteFile = viewModel::removeDownload,
                onOpenFile = onOpenDownloadedFile,
                onPauseAll = viewModel::pauseDownloads,
                onResumeAll = viewModel::resumeDownloads,
                onRetryAll = viewModel::resumeDownloads,
                onDismissCompletedAll = viewModel::dismissCompletedDownloads,
                innerPadding = innerPadding,
                scrollToTopTrigger = scrollToTopTrigger,
                sessionStartedAt = sessionStartedAt,
            )
            Tab.MY -> {
                val myUiState by myViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(currentTab, uiState.isLoggedIn) {
                    if (uiState.isLoggedIn) myViewModel.openHome()
                }
                MyTab(
                    state = myUiState,
                    onOpenSection = myViewModel::openSection,
                    onBack = onMyBack,
                    onClearDetail = onMyBack,
                    onComingSoon = myViewModel::showComingSoon,
                    onPlaylistClick = myViewModel::loadPlaylistSongs,
                    onToggleSong = myViewModel::toggleSongSelection,
                    onSelectAll = myViewModel::toggleSelectAll,
                    onDownload = myViewModel::downloadSelected,
                    onLoadMoreCloud = myViewModel::loadMoreCloudSongs,
                    onCloudSearchQueryChange = myViewModel::updateCloudSearchQuery,
                    onRefresh = { myViewModel.refreshCurrentSection() },
                    onSetRankType = myViewModel::setRankType,
                    onSetFootprintTab = myViewModel::setFootprintTab,
                    onLoadMoreAlbums = myViewModel::loadMoreCollectedAlbums,
                    onLoadMoreArtists = myViewModel::loadMoreCollectedArtists,
                    onAlbumClick = myViewModel::loadCollectedAlbumSongs,
                    onArtistClick = myViewModel::loadCollectedArtistSongs,
                    innerPadding = innerPadding,
                    isLoggedIn = uiState.isLoggedIn,
                    scrollToTopTrigger = scrollToTopTrigger,
                )
            }
            Tab.SETTINGS -> {
                val settingsSubPage = when {
                    showQualityUpgrade -> 5
                    showPreviewFeatures -> 4
                    showAccountSettings -> 3
                    showAppearanceSettings -> 1
                    showDownloadSettings -> 2
                    else -> 0
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(colorScheme.surface)
                ) {
                    // 统一的 AnimatedContent，包含一级和二级页面
                    val suppressNav = LocalSuppressNavTransition.current
                    AnimatedContent(
                        targetState = settingsSubPage,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            hierarchicalNavigationTransition(
                                isForward = targetState > initialState,
                                layoutDirection = horizontalDirection,
                                suppress = suppressNav,
                            )
                        },
                        label = "SettingsSubPage"
                    ) { page ->
                        when (page) {
                            0 -> SettingsTab(
                                uiState = uiState,
                                viewModel = viewModel,
                                onLogout = onLogout,
                                onPickDownloadDir = onPickDownloadDir,
                                onToggleFuckAiMode = viewModel::toggleFuckAiMode,
                                onQualitySelect = viewModel::updateQuality,
                                onSetNamingFormat = viewModel::setNamingFormat,
                                onToggleDownloadLyrics = viewModel::toggleDownloadLyrics,
                                onSetFolderNamingFormat = viewModel::setFolderNamingFormat,
                                onToggleEnableWriteTags = viewModel::toggleEnableWriteTags,
                                onOpenAppearanceSettings = onOpenAppearanceSettings,
                                onOpenDownloadSettings = onOpenDownloadSettings,
                                onOpenAccountSettings = onOpenAccountSettings,
                                onOpenPreviewFeatures = onOpenPreviewFeatures,
                                onThemeBaseModeChanged = viewModel::updateThemeBaseMode,
                                onToggleMonetTheme = viewModel::toggleMonetTheme,
                                onShareLog = {
                                    val logFile = File(AppLogger.getLogFilePath())
                                    val uri = if (logFile.exists()) ShareableFile.contentUri(context, logFile) else null
                                    if (uri != null) {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "导出日志"))
                                    } else {
                                        Toast.makeText(context, "暂无可导出的日志", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onExportCookies = onExportCookies,
                                onImportClick = onImportClick,
                                onClearSeenClick = {
                                    viewModel.clearSeenItems()
                                    discoverViewModel.refreshCurrentSource()
                                },
                                onClearCache = {
                                    viewModel.clearCache(context)
                                },
                                innerPadding = innerPadding,
                                scrollToTopTrigger = scrollToTopTrigger,
                            )
                            1 -> Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(colorScheme.surface)
                            ) {
                                AppearanceSettingsScreen(
                                    uiState = uiState,
                                    onThemeBaseModeChanged = viewModel::updateThemeBaseMode,
                                    onToggleMonetTheme = viewModel::toggleMonetTheme,
                                    onToggleBlurEffect = onToggleBlurEffect,
                                    onToggleLiquidGlass = onToggleLiquidGlass,
                                    onBack = onCloseAppearanceSettings,
                                    innerPadding = innerPadding
                                )
                            }
                            2 -> Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(colorScheme.surface)
                            ) {
                                 DownloadSettingsScreen(
                                     uiState = uiState,
                                     onPickDownloadDir = onPickDownloadDir,
                                     onOpenStoragePermissionSettings = onOpenStoragePermissionSettings,
                                    onToggleFuckAiMode = viewModel::toggleFuckAiMode,
                                    onQualitySelect = viewModel::updateQuality,
                                    onSetNamingFormat = viewModel::setNamingFormat,
                                    onToggleDownloadLyrics = viewModel::toggleDownloadLyrics,
                                    onSetFolderNamingFormat = viewModel::setFolderNamingFormat,
                                    onToggleEnableWriteTags = viewModel::toggleEnableWriteTags,
                                    onToggleSaveTlLrc = viewModel::toggleSaveTlLrc,
                                    onToggleSaveRomaLrc = viewModel::toggleSaveRomaLrc,
                                    onToggleSaveYrc = viewModel::toggleSaveYrc,
                                    onSetCustomNamingTemplate = viewModel::setCustomNamingTemplate,
                                    onSetArtistDelimiter = viewModel::setArtistDelimiter,
                                    onBack = onCloseDownloadSettings,
                                    innerPadding = innerPadding
                                )
                            }
                            3 -> Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(colorScheme.surface)
                            ) {
                                AccountSettingsScreen(
                                    uiState = uiState,
                                    onBrowserLoginClick = onBrowserLoginClick,
                                    onCookieLoginClick = onCookieLoginClick,
                                    onMusicULoginClick = onMusicULoginClick,
                                    onLogout = onLogout,
                                    onExportCookies = onExportCookies,
                                    onImportClick = onImportClick,
                                    onBack = onCloseAccountSettings,
                                    innerPadding = innerPadding
                                )
                            }
                            4 -> Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(colorScheme.surface)
                            ) {
                                PreviewFeaturesScreen(
                                    onOpenQualityUpgrade = onOpenQualityUpgrade,
                                    onBack = onClosePreviewFeatures,
                                    innerPadding = innerPadding,
                                )
                            }
                            5 -> Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(colorScheme.surface)
                            ) {
                                val upgradeState by upgradeViewModel.uiState.collectAsStateWithLifecycle()
                                UpgradeTab(
                                    uiState = upgradeState,
                                    onAddFolder = onPickMusicFolder,
                                    onRemoveFolder = upgradeViewModel::removeFolder,
                                    onStartScan = upgradeViewModel::startScan,
                                    onSelectUpgradeMode = upgradeViewModel::selectUpgradeMode,
                                    onToggleSongSelection = upgradeViewModel::toggleSongSelection,
                                    onSelectAll = upgradeViewModel::selectAllSongs,
                                    onDeselectAll = upgradeViewModel::deselectAllSongs,
                                    onCheckUpgrades = upgradeViewModel::checkForUpgrades,
                                    onStartUpgrade = upgradeViewModel::startUpgrade,
                                    onCancelUpgrade = upgradeViewModel::cancelUpgrade,
                                    onToggleFolderExpand = upgradeViewModel::toggleFolderExpand,
                                    onExpandAllFolders = upgradeViewModel::expandAllFolders,
                                    onCollapseAllFolders = upgradeViewModel::collapseAllFolders,
                                    onSelectFolderSongs = upgradeViewModel::selectFolderSongs,
                                    onDeselectFolderSongs = upgradeViewModel::deselectFolderSongs,
                                    innerPadding = innerPadding,
                                    scrollToTopTrigger = scrollToTopTrigger,
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }
    }
}

/**
 * 预测性返回手势期间渲染的目标页面背景预览。
 *
 * 在返回手势进行中，前景页面滑出的同时，该 composable 在下方渲染目标页面，
 * 让用户在松手前就能看到即将返回到的界面，实现 SukiSU / Melox 风格的预测性返回效果。
 *
 * 回调参数全部传空 lambda——背景预览只用于视觉展示，不处理交互事件。
 */
@Composable
private fun ContextBackPreview(
    target: ContextBackTarget,
    uiState: MainUiState,
    viewModel: MainViewModel,
    searchViewModel: SearchViewModel,
    discoverViewModel: DiscoverViewModel,
    myViewModel: MyViewModel,
    currentTab: Tab,
    innerPadding: PaddingValues,
) {
    when (target) {
        ContextBackTarget.APPEARANCE_SETTINGS,
        ContextBackTarget.DOWNLOAD_SETTINGS,
        ContextBackTarget.ACCOUNT_SETTINGS,
        ContextBackTarget.PREVIEW_FEATURES -> {
            SettingsTab(
                uiState = uiState,
                viewModel = viewModel,
                onLogout = {},
                innerPadding = innerPadding,
            )
        }
        ContextBackTarget.QUALITY_UPGRADE -> {
            PreviewFeaturesScreen(
                onOpenQualityUpgrade = {},
                onBack = {},
                innerPadding = innerPadding,
            )
        }
        ContextBackTarget.SEARCH_DETAIL -> {
            val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()
            val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
            SearchTab(
                state = searchState.copy(isDetailView = false),
                downloadProgress = downloadProgress,
                onSearchQueryChange = {},
                onSearch = {},
                onToggleSelect = {},
                onSelectAll = {},
                onSetSearchCategory = {},
                onLoadMore = {},
                onAlbumClick = {},
                onArtistClick = {},
                onPlaylistClick = {},
                onPodcastClick = {},
                innerPadding = innerPadding,
            )
        }
        ContextBackTarget.DISCOVER -> {
            val discoverState by discoverViewModel.uiState.collectAsStateWithLifecycle()
            val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
            // Show the actual parent page: detail → preSongSourceMode, sub-category → RECOMMEND
            val parentSourceMode = if (discoverState.isDetailView) discoverState.preSongSourceMode else SourceMode.RECOMMEND
            DiscoverTab(
                uiState = discoverState.copy(
                    songs = emptyList(),
                    sourceLabel = "",
                    collectionDetail = null,
                    activeArtist = null,
                    activePodcast = null,
                    isLoading = false,
                    sourceMode = parentSourceMode,
                ),
                isLoggedIn = uiState.isLoggedIn,
                downloadProgress = downloadProgress,
                onToggleSelect = {},
                onSelectAll = {},
                onSetSourceMode = {},
                innerPadding = innerPadding,
            )
        }
        ContextBackTarget.MY_SECTION -> {
            val myUiState by myViewModel.uiState.collectAsStateWithLifecycle()
            // 详情页 → 上一级是 section 列表；section 列表 → 上一级是 HOME
            val previewState = if (myUiState.isPlaylistDetail) {
                myUiState.copy(songs = emptyList())
            } else {
                myUiState.copy(section = MySection.HOME, songs = emptyList())
            }
            MyTab(
                state = previewState,
                onOpenSection = {},
                onBack = {},
                onClearDetail = {},
                onComingSoon = {},
                onPlaylistClick = {},
                onToggleSong = {},
                onSelectAll = {},
                onDownload = {},
                onLoadMoreCloud = {},
                onCloudSearchQueryChange = {},
                onRefresh = {},
                onSetRankType = {},
                onSetFootprintTab = {},
                onLoadMoreAlbums = {},
                onLoadMoreArtists = {},
                onAlbumClick = {},
                onArtistClick = {},
                innerPadding = innerPadding,
                isLoggedIn = uiState.isLoggedIn,
            )
        }
        ContextBackTarget.LEGACY_SEARCH -> {
            val discoverState by discoverViewModel.uiState.collectAsStateWithLifecycle()
            val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
            DiscoverTab(
                uiState = discoverState.copy(
                    songs = emptyList(),
                    sourceLabel = "",
                    collectionDetail = null,
                    activeArtist = null,
                    activePodcast = null,
                    isLoading = false,
                    sourceMode = SourceMode.RECOMMEND,
                ),
                isLoggedIn = uiState.isLoggedIn,
                downloadProgress = downloadProgress,
                onToggleSelect = {},
                onSelectAll = {},
                onSetSourceMode = {},
                innerPadding = innerPadding,
            )
        }
    }
}

@Composable
private fun LoginPrompt(
    onCookieLogin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "♫",
            fontSize = 64.sp,
            color = colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "请先登录",
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "登录后可使用歌单解析、下载等功能",
            color = colorScheme.onSurfaceVariantActions,
            style = MiuixTheme.textStyles.body2
        )
        Spacer(Modifier.height(32.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onCookieLogin
        ) {
            Text("Cookie 登录")
        }
    }
}
