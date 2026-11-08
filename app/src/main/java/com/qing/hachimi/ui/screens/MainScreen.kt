package com.qing.hachimi.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
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
import com.qing.hachimi.ui.navigation.NavKey2
import com.qing.hachimi.ui.navigation.rememberMainNavBackStack
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import com.qing.hachimi.ui.theme.AppSpacing
import com.qing.hachimi.util.AppLogger
import com.qing.hachimi.util.HapticLevel
import com.qing.hachimi.util.BuildChannel
import com.qing.hachimi.util.OfficialBuild
import com.qing.hachimi.util.StoragePermissionManager
import com.qing.hachimi.util.haptic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.basic.NavigationRailValue
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState

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
    // 侧边导航栏：够宽或横屏大尺寸时启用（参考 SukiSU），展开状态跟随设置保存。
    // 用户开启了悬浮底栏特效（毛玻璃/液态玻璃）时，优先保留底部悬浮栏，不换成侧边栏。
    val railScreen = rememberIsNavigationRailScreen()
    val useRail = railScreen && !uiState.blurEffect && !uiState.liquidGlass
    // 宽屏布局与侧边栏是两件事：开了玻璃特效仍然算宽屏，只是底栏不换成侧边栏
    val isWideLayout = railScreen
    // 设置页改用导航栈：二级页是栈上的一层，返回由 NavDisplay 处理
    val navBackStack = rememberMainNavBackStack()
    // 搜索页的返回栈：由这里持有，顶部返回箭头和 NavDisplay 共用同一个栈
    val searchNavStack = remember { mutableStateListOf<NavKey>(NavKey2.SearchHome) }
    val settingsNavTop = navBackStack.lastOrNull() as? NavKey2
    val showAppearanceSettings = settingsNavTop == NavKey2.AppearanceSettings
    val showDownloadSettings = settingsNavTop == NavKey2.DownloadSettings
    val showFileTagSettings = settingsNavTop == NavKey2.FileTagSettings
    val showAccountSettings = settingsNavTop == NavKey2.AccountSettings
    val showPreviewFeatures = settingsNavTop == NavKey2.PreviewFeatures
    val showAboutSettings = settingsNavTop == NavKey2.AboutSettings
    val showQualityUpgrade = settingsNavTop == NavKey2.QualityUpgrade
    val pushSettingsPage: (NavKey2) -> Unit = { key -> navBackStack.add(key) }
    val popSettingsPage: () -> Unit = {
        if (navBackStack.size > 1) navBackStack.removeAt(navBackStack.lastIndex)
    }
    val railState = rememberNavigationRailState(
        initialValue = if (settingsManager.navigationRailExpanded) {
            NavigationRailValue.Expanded
        } else {
            NavigationRailValue.Collapsed
        },
    )
    LaunchedEffect(railState.currentValue) {
        settingsManager.navigationRailExpanded = railState.isExpanded
    }
    val scope = rememberCoroutineScope()
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
                        // 双击设置 Tab：退回设置首页（清掉栈上所有二级页）
                        while (navBackStack.size > 1) {
                            navBackStack.removeAt(navBackStack.lastIndex)
                        }
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
    val showSearchBack = false

    val showDiscoverBack = currentTab == Tab.DISCOVER && discoverState.canNavigateBack
    val showMyBack = currentTab == Tab.MY && (myUiState.section != MySection.HOME || myUiState.isPlaylistDetail)
    val showSearchSubBack = currentTab == Tab.SEARCH && searchState.canNavigateBack
    val contextBackTarget = resolveContextBackTarget(
        showAppearanceSettings = currentTab == Tab.SETTINGS && showAppearanceSettings,
        showDownloadSettings = currentTab == Tab.SETTINGS && showDownloadSettings,
        showFileTagSettings = currentTab == Tab.SETTINGS && showFileTagSettings,
        showAccountSettings = currentTab == Tab.SETTINGS && showAccountSettings,
        showPreviewFeatures = currentTab == Tab.SETTINGS && showPreviewFeatures,
        showAboutSettings = currentTab == Tab.SETTINGS && showAboutSettings,
        showQualityUpgrade = currentTab == Tab.SETTINGS && showQualityUpgrade,
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

    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val mainBottomContentPadding = when {
        useRail -> floatingContentBottomPadding(systemBottom, hasFloatingBar = false)
        uiState.floatingNavigationBar -> floatingContentBottomPadding(systemBottom, hasFloatingBar = true)
        else -> dockedContentBottomPadding(systemBottom)
    }

    // 宽屏下按页面类型放宽内容上限：发现页网格铺满，列表/详情次之，其余居中
    val discoverGridHome = discoverState.currentLevel == DiscoverLevel.HOME &&
        discoverState.sourceMode in setOf(
            SourceMode.RECOMMEND,
            SourceMode.PLAYLISTS,
            SourceMode.PODCASTS,
            SourceMode.NEW_ALBUMS,
        )
    val contentMaxWidth = when {
        !isWideLayout -> WideScreenContentMaxWidth
        currentTab == Tab.DISCOVER -> if (discoverGridHome) 1000.dp else 720.dp
        currentTab == Tab.SETTINGS -> 900.dp
        else -> 800.dp
    }

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
            ContextBackTarget.APPEARANCE_SETTINGS,
            ContextBackTarget.DOWNLOAD_SETTINGS,
            ContextBackTarget.FILE_TAG_SETTINGS,
            ContextBackTarget.ACCOUNT_SETTINGS,
            ContextBackTarget.QUALITY_UPGRADE,
            ContextBackTarget.PREVIEW_FEATURES,
            ContextBackTarget.ABOUT_SETTINGS,
            -> popSettingsPage()
            ContextBackTarget.SEARCH_DETAIL -> {
                if (searchNavStack.size > 1) searchNavStack.removeAt(searchNavStack.lastIndex)
                searchViewModel.goBack()
            }
            ContextBackTarget.DISCOVER -> discoverViewModel.goBack()
            ContextBackTarget.MY_SECTION -> {
                // 由导航栈决定回哪一层
                if (myUiState.isPlaylistDetail) {
                    myViewModel.clearSongs()
                    myViewModel.backToSectionList()
                } else {
                    myViewModel.backToHome()
                }
            }
            null -> Unit
        }
    }

    // 返回手势与预测性返回动画由各 Tab 的 NavDisplay 统一处理，这里不再手写位移
    val suppressNavTransition = false

    val topChromeFadeDistancePx = with(density) {
        (if (showContextHeader) 44.dp + 96.dp else AppSpacing.sm + 56.dp).toPx()
    }
    // 顶部磨砂的进度：0 = 完全透明，1 = 不透明。
    // 只在绘制阶段读取滚动值；如果在组合里读，滚动每变化一像素都会重组整个主界面。
    val topChromeProgress: () -> Float = {
        if (showContextHeader || currentTab != Tab.SEARCH) {
            (topChromeScrollOffset.floatValue / topChromeFadeDistancePx).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        if (useRail) {
            MainNavigationRail(
                currentTab = currentTab,
                isLoggedIn = uiState.isLoggedIn,
                railState = railState,
                onTabChange = onTabClick,
            )
        }
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
    ) {

        // Foreground
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                            else -> settingsNavTop.settingsTitle() ?: currentTab.label
                        },
                        // 详情页的内容顶部本来就有大标题，磨砂栏的标题跟着滚动渐显，避免同一屏出现两遍
                        titleAlpha = if (
                            (showDiscoverBack && discoverState.isDetailView) || showSearchSubBack
                        ) topChromeProgress else { { 1f } },
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
                        WidthLimiter(maxWidth = contentMaxWidth) {
                            TabContent(
                    currentTab = currentTab,
                    uiState = uiState,
                    discoverViewModel = discoverViewModel,
                    searchViewModel = searchViewModel,
                    myViewModel = myViewModel,
                    upgradeViewModel = upgradeViewModel,
                    downloadProgress = downloadProgress,
                    viewModel = viewModel,
                    navBackStack = navBackStack,
                    searchNavStack = searchNavStack,
                    onPopSettingsPage = popSettingsPage,
                    innerPadding = innerPadding,
                    isWideLayout = isWideLayout,
                    onOpenDownloadedFile = onOpenDownloadedFile,
                    onPickDownloadDir = onPickDownloadDir,
                    onPickMusicFolder = onPickMusicFolder,
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
                    onOpenAppearanceSettings = { pushSettingsPage(NavKey2.AppearanceSettings) },
                    onOpenDownloadSettings = { pushSettingsPage(NavKey2.DownloadSettings) },
                    onOpenFileTagSettings = { pushSettingsPage(NavKey2.FileTagSettings) },
                    onOpenAccountSettings = { pushSettingsPage(NavKey2.AccountSettings) },
                    onOpenPreviewFeatures = { pushSettingsPage(NavKey2.PreviewFeatures) },
                    onOpenQualityUpgrade = { pushSettingsPage(NavKey2.QualityUpgrade) },
                    onClosePreviewFeatures = performContextBack,
                    onOpenAboutSettings = { pushSettingsPage(NavKey2.AboutSettings) },
                    onCloseAppearanceSettings = performContextBack,
                    onCloseDownloadSettings = performContextBack,
                    onCloseFileTagSettings = performContextBack,
                    onCloseAccountSettings = performContextBack,
                    onCloseAboutSettings = performContextBack,
                    onToggleBlurEffect = viewModel::toggleBlurEffect,
                    onToggleLiquidGlass = viewModel::toggleLiquidGlass,
                    onImportClick = { showImportDialog = true },
                    onOpenStoragePermissionSettings = openStoragePermissionSettings,
                    sessionStartedAt = downloadSessionStartedAt,
                    topChromeScrollOffset = topChromeScrollOffset,
                        )
                        }
                    }
                }

                // 顶部渐变模糊层：位于 body 之上、Scaffold topBar（标题栏）之下。
                // 由“距顶部距离”决定透明度：远离顶部（已滚过标题区）时满模糊固定显示；
                // 仅当滚动接近顶部（滚动偏移小于标题区+fade 高度）时才渐出，回顶即完全取消。
                // topBar 在 Scaffold 中被绘制在此层之上，标题保持清晰。
                // 搜索主页顶部有冻结的搜索框（自带背景），无需磨砂，强制隐藏；
                // 搜索详情页（showContextHeader）仍保留磨砂以支持透明标题栏。
                TopChromeOverlay(
                    backdrop = backdrop.takeIf { uiState.blurEffect },
                    headerHeight = if (showContextHeader) 44.dp else AppSpacing.sm,
                    fadeExtension = if (showContextHeader) 96.dp else 56.dp,
                    progress = topChromeProgress,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
        }
        }

        // 底部操作区位于导航动画层之外，返回手势时原地不动；实时模糊取的是其下方真实内容
        if (!useRail) {
            if (uiState.floatingNavigationBar) {
                MainBottomBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    currentTab = currentTab,
                    isLoggedIn = uiState.isLoggedIn,
                    backdrop = backdrop,
                    isBlurEnabled = uiState.blurEffect,
                    isLiquidGlassEnabled = uiState.liquidGlass,
                    onTabChange = onTabClick,
                )
            } else {
                MainDockedNavigationBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    currentTab = currentTab,
                    isLoggedIn = uiState.isLoggedIn,
                    onTabChange = onTabClick,
                )
            }
        }
    }
    }

    // Dialogs live outside Scaffold, so use window dialogs instead of Scaffold-hosted overlays.
    CookieLoginDialog(
        show = showCookieDialog,
        loginMode = loginMode,
        input = cookieInput,
        error = cookieError,
        onInputChange = { cookieInput = it; cookieError = null },
        onDismiss = {
            showCookieDialog = false
            cookieInput = ""
            cookieError = null
        },
        onCancel = {
            showCookieDialog = false
            cookieInput = ""
            cookieError = null
        },
        onConfirm = {
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
        },
    )

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
    StoragePermissionDialog(
        show = showPermissionDialog,
        storageCompatLayer = storageCompatLayer,
        onDismiss = { showPermissionDialog = false },
        onOpenSettings = {
            showPermissionDialog = false
            openStoragePermissionSettings()
        },
    )

    ReloginDialog(
        show = showReloginDialog,
        onDismiss = { showReloginDialog = false },
        onRelogin = {
            showReloginDialog = false
            val intent = Intent(context, NeteaseWebLoginActivity::class.java)
            intent.putExtra(NeteaseWebLoginActivity.EXTRA_CLEAR_CACHE, true)
            webLoginLauncher.launch(intent)
        },
    )

    LogoutDialog(
        show = showLogoutDialog,
        onDismiss = { showLogoutDialog = false },
        onConfirm = {
            showLogoutDialog = false
            viewModel.logout()
            discoverViewModel.clearPrivateContent()
            myViewModel.clearAccountContent()
            showCookieDialog = false
        },
    )
    // Re-login dialog

    // Export account data dialog
    ExportAccountDialog(
        show = showExportDialog,
        exportedData = exportedData,
        onDismiss = { showExportDialog = false },
    )

    // Import account data dialog
    ImportAccountDialog(
        show = showImportDialog,
        input = importInput,
        error = importError,
        onInputChange = { importInput = it; importError = null },
        onDismiss = {
            showImportDialog = false
            importInput = ""
            importError = null
        },
        onCancel = {
            showImportDialog = false
            importInput = ""
            importError = null
        },
        onConfirm = {
            val result = viewModel.importAccountData(importInput.trim())
            if (result) {
                showImportDialog = false
                importInput = ""
                importError = null
            } else {
                importError = uiState.statusMessage ?: "导入失败：数据格式错误"
            }
        },
    )

    // 浏览器登录缓存询问对话框
    val accountHistory = remember(showBrowserLoginCacheDialog, accountHistoryVersion) {
        viewModel.getAccountHistory()
    }
    BrowserLoginDialog(
        show = showBrowserLoginCacheDialog,
        accounts = accountHistory,
        onDismiss = { showBrowserLoginCacheDialog = false },
        onSwitchAccount = { account ->
            showBrowserLoginCacheDialog = false
            if (viewModel.switchToAccount(account)) {
                discoverViewModel.clearPrivateContent()
                myViewModel.clearAccountContent()
                Toast.makeText(context, "已切换到 ${account.nickname}", Toast.LENGTH_SHORT).show()
            }
        },
        onDeleteAccount = { account ->
            viewModel.deleteAccountHistory(account.userId)
            accountHistoryVersion++
        },
        onQuickLogin = {
            showBrowserLoginCacheDialog = false
            webLoginLauncher.launch(Intent(context, NeteaseWebLoginActivity::class.java))
        },
        onNewLogin = {
            showBrowserLoginCacheDialog = false
            val intent = Intent(context, NeteaseWebLoginActivity::class.java)
            intent.putExtra(NeteaseWebLoginActivity.EXTRA_CLEAR_CACHE, true)
            webLoginLauncher.launch(intent)
        },
    )
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
    APPEARANCE_SETTINGS(true),
    DOWNLOAD_SETTINGS(true),
    FILE_TAG_SETTINGS(true),
    ACCOUNT_SETTINGS(true),
    PREVIEW_FEATURES(true),
    ABOUT_SETTINGS(true),
    QUALITY_UPGRADE(true),
    SEARCH_DETAIL(true),
    DISCOVER(true),
    MY_SECTION(false),
}

/** 设置二级页在磨砂标题栏里显示的名字 */
private fun NavKey2?.settingsTitle(): String? = when (this) {
    NavKey2.AppearanceSettings -> "外观"
    NavKey2.DownloadSettings -> "下载"
    NavKey2.FileTagSettings -> "文件与标签"
    NavKey2.AccountSettings -> "账户"
    NavKey2.AboutSettings -> "关于"
    NavKey2.PreviewFeatures -> "预览功能"
    NavKey2.QualityUpgrade -> "音质升级"
    else -> null
}

internal fun resolveContextBackTarget(
    showAppearanceSettings: Boolean,
    showDownloadSettings: Boolean,
    showAccountSettings: Boolean,
    showDiscoverBack: Boolean,
    showMyPlaylistBack: Boolean,
    showSearchSubBack: Boolean,
    showPreviewFeatures: Boolean = false,
    showAboutSettings: Boolean = false,
    showQualityUpgrade: Boolean = false,
    showFileTagSettings: Boolean = false,
): ContextBackTarget? = when {
    showQualityUpgrade -> ContextBackTarget.QUALITY_UPGRADE
    showPreviewFeatures -> ContextBackTarget.PREVIEW_FEATURES
    showAboutSettings -> ContextBackTarget.ABOUT_SETTINGS
    showAppearanceSettings -> ContextBackTarget.APPEARANCE_SETTINGS
    showDownloadSettings -> ContextBackTarget.DOWNLOAD_SETTINGS
    showFileTagSettings -> ContextBackTarget.FILE_TAG_SETTINGS
    showAccountSettings -> ContextBackTarget.ACCOUNT_SETTINGS
    showSearchSubBack -> ContextBackTarget.SEARCH_DETAIL
    showDiscoverBack -> ContextBackTarget.DISCOVER
    showMyPlaylistBack -> ContextBackTarget.MY_SECTION
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
private fun TabContent(
    currentTab: Tab,
    uiState: MainUiState,
    downloadProgress: Map<Long, DownloadProgress>,
    viewModel: MainViewModel,
    discoverViewModel: DiscoverViewModel,
    searchViewModel: SearchViewModel,
    myViewModel: MyViewModel,
    upgradeViewModel: UpgradeViewModel,
    navBackStack: androidx.compose.runtime.snapshots.SnapshotStateList<NavKey>,
    searchNavStack: androidx.compose.runtime.snapshots.SnapshotStateList<NavKey>,
    onPopSettingsPage: () -> Unit,
    innerPadding: PaddingValues,
    onOpenDownloadedFile: (Long) -> Unit,
    onPickDownloadDir: () -> Unit,
    onPickMusicFolder: () -> Unit,
    onMyBack: () -> Unit,
    onBrowserLoginClick: () -> Unit,
    onCookieLoginClick: () -> Unit,
    onMusicULoginClick: () -> Unit,
    onLogout: () -> Unit,
    onExportCookies: () -> Unit,
    context: android.content.Context,
    onOpenAppearanceSettings: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    onOpenFileTagSettings: () -> Unit = {},
    onOpenAccountSettings: () -> Unit,
    onOpenPreviewFeatures: () -> Unit = {},
    onOpenQualityUpgrade: () -> Unit = {},
    onClosePreviewFeatures: () -> Unit = {},
    onOpenAboutSettings: () -> Unit = {},
    onCloseAppearanceSettings: () -> Unit,
    onCloseDownloadSettings: () -> Unit,
    onCloseFileTagSettings: () -> Unit = {},
    onCloseAccountSettings: () -> Unit,
    onCloseAboutSettings: () -> Unit = {},
    onToggleBlurEffect: () -> Unit = {},
    onToggleLiquidGlass: () -> Unit = {},
    onImportClick: () -> Unit,
    onOpenStoragePermissionSettings: () -> Unit = {},
    sessionStartedAt: Long = 0L,
    topChromeScrollOffset: MutableFloatState = mutableFloatStateOf(0f),
    isWideLayout: Boolean = false,
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
            directionalTabTransition(
                isForward = targetState.ordinal > initialState.ordinal,
                layoutDirection = horizontalDirection,
                reduceMotion = reduceMotion,
                density = density,
            )
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
                    onBack = discoverViewModel::goBack,
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
                    onBack = searchViewModel::goBack,
                    navStack = searchNavStack,
                    artistDetailContent = {
                        val artistState by discoverViewModel.uiState.collectAsStateWithLifecycle()
                        ArtistDetailPage(
                            state = artistState,
                            songs = artistState.artistDetailSongs,
                            downloadProgress = downloadProgress,
                            onToggleSelect = discoverViewModel::toggleSongSelection,
                            onSelectAll = discoverViewModel::toggleSelectAll,
                            onDownload = discoverViewModel::downloadSelected,
                            onAlbumClick = discoverViewModel::loadAlbumFromResult,
                            onArtistClick = { a -> discoverViewModel.loadArtistDetail(a) },
                            onSetSection = discoverViewModel::setArtistDetailSection,
                            onLoadMore = discoverViewModel::loadMoreArtistSection,
                            onRetry = { discoverViewModel.refreshCurrentSource() },
                            innerPadding = stablePagePadding(innerPadding, contextPage = true),
                        )
                    },
                    onSearchQueryChange = searchViewModel::updateSearchQuery,
                    onSearch = searchViewModel::search,
                    onParseUrl = { searchViewModel.updatePlaylistUrl(it); searchViewModel.parseUrl() },
                    onToggleSelect = searchViewModel::toggleSongSelection,
                    onSelectAll = searchViewModel::toggleSelectAll,
                    onSetSearchCategory = searchViewModel::setSearchCategory,
                    onLoadMore = searchViewModel::loadMoreSearch,
                    onAlbumClick = searchViewModel::loadAlbumFromResult,
                    onArtistClick = { artist ->
                        // 歌手详情留在搜索 Tab 内，返回时回到搜索结果
                        discoverViewModel.loadArtistDetail(artist, SourceMode.SEARCH)
                        searchViewModel.openArtistDetail()
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
                SettingsNavHost(
                    navBackStack = navBackStack,
                    onPopSettingsPage = onPopSettingsPage,
                    uiState = uiState,
                    viewModel = viewModel,
                    upgradeViewModel = upgradeViewModel,
                    discoverViewModel = discoverViewModel,
                    context = context,
                    innerPadding = innerPadding,
                    scrollToTopTrigger = scrollToTopTrigger,
                    isWide = isWideLayout,
                    onLogout = onLogout,
                    onPickDownloadDir = onPickDownloadDir,
                    onPickMusicFolder = onPickMusicFolder,
                    onOpenAppearanceSettings = onOpenAppearanceSettings,
                    onOpenDownloadSettings = onOpenDownloadSettings,
                    onOpenFileTagSettings = onOpenFileTagSettings,
                    onOpenAccountSettings = onOpenAccountSettings,
                    onOpenPreviewFeatures = onOpenPreviewFeatures,
                    onOpenQualityUpgrade = onOpenQualityUpgrade,
                    onClosePreviewFeatures = onClosePreviewFeatures,
                    onOpenAboutSettings = onOpenAboutSettings,
                    onCloseAppearanceSettings = onCloseAppearanceSettings,
                    onCloseDownloadSettings = onCloseDownloadSettings,
                    onCloseFileTagSettings = onCloseFileTagSettings,
                    onCloseAccountSettings = onCloseAccountSettings,
                    onCloseAboutSettings = onCloseAboutSettings,
                    onToggleBlurEffect = onToggleBlurEffect,
                    onToggleLiquidGlass = onToggleLiquidGlass,
                    onExportCookies = onExportCookies,
                    onImportClick = onImportClick,
                    onOpenStoragePermissionSettings = onOpenStoragePermissionSettings,
                    onBrowserLoginClick = onBrowserLoginClick,
                    onCookieLoginClick = onCookieLoginClick,
                    onMusicULoginClick = onMusicULoginClick,
                )
            }
            }
        }
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