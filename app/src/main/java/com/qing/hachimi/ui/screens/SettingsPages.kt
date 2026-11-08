package com.qing.hachimi.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.widget.Toast
import com.qing.hachimi.ui.navigation.NavKey2
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import com.qing.hachimi.util.AppLogger
import com.qing.hachimi.util.ShareableFile
import java.io.File
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

// ═════════════════════════════════════════════════════════════════════════════
// 设置 Tab 的导航栈：一级设置页在栈底，二级页依次叠上去
// ═════════════════════════════════════════════════════════════════════════════

@Composable
internal fun SettingsNavHost(
    navBackStack: SnapshotStateList<NavKey>,
    onPopSettingsPage: () -> Unit,
    uiState: MainUiState,
    viewModel: MainViewModel,
    upgradeViewModel: UpgradeViewModel,
    discoverViewModel: DiscoverViewModel,
    context: android.content.Context,
    innerPadding: PaddingValues,
    scrollToTopTrigger: Int,
    isWide: Boolean,
    onLogout: () -> Unit,
    onPickDownloadDir: () -> Unit,
    onPickMusicFolder: () -> Unit,
    onOpenAppearanceSettings: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    onOpenFileTagSettings: () -> Unit,
    onOpenAccountSettings: () -> Unit,
    onOpenPreviewFeatures: () -> Unit,
    onOpenQualityUpgrade: () -> Unit,
    onClosePreviewFeatures: () -> Unit,
    onOpenAboutSettings: () -> Unit,
    onCloseAppearanceSettings: () -> Unit,
    onCloseDownloadSettings: () -> Unit,
    onCloseFileTagSettings: () -> Unit,
    onCloseAccountSettings: () -> Unit,
    onCloseAboutSettings: () -> Unit,
    onToggleBlurEffect: () -> Unit,
    onExportCookies: () -> Unit,
    onImportClick: () -> Unit,
    onOpenStoragePermissionSettings: () -> Unit,
    onBrowserLoginClick: () -> Unit,
    onCookieLoginClick: () -> Unit,
    onMusicULoginClick: () -> Unit,
) {
    // 顶栏高度由 Miuix TopAppBar 决定，一级页和二级页统一使用 Scaffold 给的内边距
    val primaryPadding = innerPadding
    val contextPadding = innerPadding
    NavDisplay(
        backStack = navBackStack,
        modifier = Modifier.fillMaxSize(),
        onBack = { onPopSettingsPage() },
        transition = NavTransitions.MiuixDefault,
        effects = NavDisplayEffects(
            cornerClipRadius = rememberNavSystemCornerRadius(),
            dimAmount = 0.5f,
        ),
    ) {
        entry<NavKey2.DiscoverHome> {
            SettingsTab(
                uiState = uiState,
                onOpenAppearanceSettings = onOpenAppearanceSettings,
                onOpenDownloadSettings = onOpenDownloadSettings,
                onOpenFileTagSettings = onOpenFileTagSettings,
                onOpenAccountSettings = onOpenAccountSettings,
                onOpenPreviewFeatures = onOpenPreviewFeatures,
                onOpenAboutSettings = onOpenAboutSettings,
                onResetDiscoverClick = {
                    viewModel.resetDiscover()
                    discoverViewModel.refreshCurrentSource()
                },
                onClearCache = {
                    viewModel.clearCache(context)
                },
                isWide = isWide,
                innerPadding = primaryPadding,
                scrollToTopTrigger = scrollToTopTrigger,
            )
        }
        entry<NavKey2.AppearanceSettings> {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colorScheme.surface)
            ) {
                AppearanceSettingsScreen(
                    uiState = uiState,
                    onThemeBaseModeChanged = viewModel::updateThemeBaseMode,
                    onToggleMonetTheme = viewModel::toggleMonetTheme,
                    onToggleFloatingNavigationBar = viewModel::toggleFloatingNavigationBar,
                    onToggleBlurEffect = onToggleBlurEffect,
                    onFloatingBarEffectChange = viewModel::setFloatingBarEffect,
                    onBack = onCloseAppearanceSettings,
                    innerPadding = contextPadding
                )
            }
        }
        entry<NavKey2.DownloadSettings> {
            Box(
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
                    onToggleDownloadLyrics = viewModel::toggleDownloadLyrics,
                    onToggleSaveTlLrc = viewModel::toggleSaveTlLrc,
                    onToggleSaveRomaLrc = viewModel::toggleSaveRomaLrc,
                    onToggleSaveYrc = viewModel::toggleSaveYrc,
                    onBack = onCloseDownloadSettings,
                    innerPadding = contextPadding
                )
            }
        }
        entry<NavKey2.FileTagSettings> {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colorScheme.surface)
            ) {
                FileTagSettingsScreen(
                    uiState = uiState,
                    onSetNamingFormat = viewModel::setNamingFormat,
                    onSetFolderNamingFormat = viewModel::setFolderNamingFormat,
                    onToggleEnableWriteTags = viewModel::toggleEnableWriteTags,
                    onSetCustomNamingTemplate = viewModel::setCustomNamingTemplate,
                    onSetArtistDelimiter = viewModel::setArtistDelimiter,
                    onBack = onCloseFileTagSettings,
                    innerPadding = contextPadding
                )
            }
        }
        entry<NavKey2.AccountSettings> {
            Box(
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
                    innerPadding = contextPadding
                )
            }
        }
        entry<NavKey2.AboutSettings> {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colorScheme.surface)
            ) {
                AboutSettingsScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onShareLog = {
                        val logFile = File(AppLogger.getLogFilePath())
                        val uri =
                            if (logFile.exists()) ShareableFile.contentUri(context, logFile) else null
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
                    onBack = onCloseAboutSettings,
                    innerPadding = contextPadding,
                )
            }
        }
        entry<NavKey2.PreviewFeatures> {
            PageSurface {
                PreviewFeaturesScreen(
                    onOpenQualityUpgrade = onOpenQualityUpgrade,
                    onBack = onClosePreviewFeatures,
                    innerPadding = contextPadding,
                )
            }
        }
        entry<NavKey2.QualityUpgrade> {
            Box(
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
                    innerPadding = contextPadding,
                    scrollToTopTrigger = scrollToTopTrigger,
                )
            }
        }
    }
}