package com.qing.hachimi.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.qing.hachimi.data.local.FloatingBarEffect
import com.qing.hachimi.ui.theme.ColorMode
import com.qing.hachimi.util.OfficialBuild
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ContactsCircle
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.FileDownloads
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.RemoveContact
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.window.WindowDialog
import com.qing.hachimi.util.UpdateInfo

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun SettingsTab(
    uiState: MainUiState,
    onOpenAppearanceSettings: () -> Unit = {},
    onOpenDownloadSettings: () -> Unit = {},
    onOpenFileTagSettings: () -> Unit = {},
    onOpenAccountSettings: () -> Unit = {},
    onOpenPreviewFeatures: () -> Unit = {},
    onOpenAboutSettings: () -> Unit = {},
    onResetDiscoverClick: () -> Unit = {},
    onClearCache: () -> Unit = {},
    isWide: Boolean = false,
    innerPadding: PaddingValues = PaddingValues(0.dp),
    scrollToTopTrigger: Int = 0,
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            gridState.animateScrollToItem(0)
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (isWide) 2 else 1),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = innerPadding,
    ) {
        // ═════════════════════════════════════════════════════════════
        // 下载设置 - 跳转到二级页面
        // ═════════════════════════════════════════════════════════════
        item {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 12.dp)
                    .fillMaxWidth()
            ) {
                ArrowPreference(
                    title = "下载",
                    summary = "音质、目录、歌词等下载设置",
                    startAction = {
                        Icon(
                            MiuixIcons.Folder,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "下载",
                            tint = colorScheme.onBackground
                        )
                    },
                    onClick = onOpenDownloadSettings
                )
            }
        }

        // ═════════════════════════════════════════════════════════════
        // 文件与标签 - 跳转到二级页面
        // ═════════════════════════════════════════════════════════════
        item {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 12.dp)
                    .fillMaxWidth()
            ) {
                ArrowPreference(
                    title = "文件与标签",
                    summary = "命名格式、文件夹规则、音频标签",
                    startAction = {
                        Icon(
                            MiuixIcons.Notes,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "文件与标签",
                            tint = colorScheme.onBackground
                        )
                    },
                    onClick = onOpenFileTagSettings
                )
            }
        }

        // ═════════════════════════════════════════════════════════════
        // 账户设置 - 跳转到二级页面
        // ═════════════════════════════════════════════════════════════
        item {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 12.dp)
                    .fillMaxWidth()
            ) {
                ArrowPreference(
                    title = "账户",
                    summary = if (uiState.isLoggedIn) "已登录，点击管理账户" else "未登录，点击登录或导入备份",
                    startAction = {
                        Icon(
                            if (uiState.isLoggedIn) MiuixIcons.Ok else MiuixIcons.ContactsCircle,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "账户",
                            tint = if (uiState.isLoggedIn) colorScheme.primary else colorScheme.onBackground
                        )
                    },
                    onClick = onOpenAccountSettings
                )
            }
        }

        uiState.statusMessage?.let { message ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                val isError = message.contains("失败") ||
                    message.contains("错误") ||
                    message.contains("超时")
                Text(
                    text = message,
                    color = if (isError) colorScheme.error else colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MiuixTheme.textStyles.body2,
                )
            }
        }

        // ═════════════════════════════════════════════════════════════
        // 外观设置 - 跳转到二级页面
        // ═════════════════════════════════════════════════════════════
        item {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 12.dp)
                    .fillMaxWidth()
            ) {
                ArrowPreference(
                    title = "外观",
                    summary = "主题模式、莫奈取色等外观设置",
                    startAction = {
                        Icon(
                            MiuixIcons.Theme,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "外观",
                            tint = colorScheme.onBackground
                        )
                    },
                    onClick = onOpenAppearanceSettings
                )
            }
        }

        item {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 12.dp)
                    .fillMaxWidth()
            ) {
                ArrowPreference(
                    title = "预览功能",
                    summary = "测试中的功能，可能不稳定",
                    startAction = {
                        Icon(
                            MiuixIcons.Tune,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "预览功能",
                            tint = colorScheme.onBackground
                        )
                    },
                    onClick = onOpenPreviewFeatures
                )
            }
        }

        // ═════════════════════════════════════════════════════════════
        // 关于 - 跳转到二级页面
        // ═════════════════════════════════════════════════════════════
        item {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 12.dp)
                    .fillMaxWidth()
            ) {
                ArrowPreference(
                    title = "关于",
                    summary = "版本、更新日志、免责声明",
                    startAction = {
                        Icon(
                            MiuixIcons.Info,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "关于",
                            tint = colorScheme.onBackground
                        )
                    },
                    onClick = onOpenAboutSettings
                )
            }
        }

        // ═════════════════════════════════════════════════════════════
        // 维护工具
        // ═════════════════════════════════════════════════════════════
        item(span = { GridItemSpan(maxLineSpan) }) {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 12.dp)
                    .fillMaxWidth()
            ) {
                ArrowPreference(
                    title = "重置发现页",
                    summary = "清除发现页缓存并重新加载",
                    startAction = {
                        Icon(
                            MiuixIcons.Reset,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "重置发现页",
                            tint = colorScheme.onBackground
                        )
                    },
                    onClick = onResetDiscoverClick
                )

                ArrowPreference(
                    title = "清除缓存",
                    summary = "清除图片缓存和临时文件",
                    startAction = {
                        Icon(
                            MiuixIcons.Delete,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "清除缓存",
                            tint = colorScheme.onBackground
                        )
                    },
                    onClick = onClearCache
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Spacer(modifier = Modifier.height(LocalMainBottomContentPadding.current))
        }
    }
    VerticalScrollBar(
        adapter = rememberScrollBarAdapter(gridState),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
    )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 账户设置二级页面
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun AccountSettingsScreen(
    uiState: MainUiState,
    onBrowserLoginClick: () -> Unit,
    onCookieLoginClick: () -> Unit,
    onMusicULoginClick: () -> Unit,
    onLogout: () -> Unit,
    onExportCookies: () -> Unit,
    onImportClick: () -> Unit = {},
    onBack: () -> Unit,
    innerPadding: PaddingValues = PaddingValues(0.dp)
) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = innerPadding
    ) {
        item {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 12.dp)
                    .fillMaxWidth()
            ) {
                if (uiState.isLoggedIn) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = "已登录",
                            color = colorScheme.primary,
                            style = MiuixTheme.textStyles.title4,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(12.dp))
                        InfoRow(label = "登录凭据", value = "MUSIC_U 已保存")
                        Spacer(Modifier.height(8.dp))
                        InfoRow(label = "备份数据", value = "可导出")
                    }
                    ArrowPreference(
                        title = "导出备份数据",
                        summary = "包含 MUSIC_U 和必要 Cookie，请妥善保存",
                        startAction = {
                            Icon(
                                MiuixIcons.FileDownloads,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = "导出备份数据",
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = onExportCookies
                    )
                    ArrowPreference(
                        title = "退出登录",
                        summary = "清除本机保存的账户数据",
                        startAction = {
                            Icon(
                                MiuixIcons.RemoveContact,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = "退出登录",
                                tint = colorScheme.error
                            )
                        },
                        onClick = onLogout
                    )
                } else {
                    ArrowPreference(
                        title = "浏览器登录",
                        summary = "打开网页登录，成功后自动保存登录凭据",
                        startAction = {
                            Icon(
                                MiuixIcons.ContactsCircle,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = "浏览器登录",
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = onBrowserLoginClick
                    )
                    ArrowPreference(
                        title = "Cookie 登录",
                        summary = "粘贴浏览器复制的完整 Cookie",
                        startAction = {
                            Icon(
                                MiuixIcons.Notes,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = "Cookie 登录",
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = onCookieLoginClick
                    )
                    ArrowPreference(
                        title = "MUSIC_U 登录",
                        summary = "只粘贴 MUSIC_U 值",
                        startAction = {
                            Icon(
                                MiuixIcons.Ok,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = "MUSIC_U 登录",
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = onMusicULoginClick
                    )
                    ArrowPreference(
                        title = "导入备份数据",
                        summary = "粘贴之前导出的完整账户备份",
                        startAction = {
                            Icon(
                                MiuixIcons.FileDownloads,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = "导入备份数据",
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = onImportClick
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(LocalMainBottomContentPadding.current))
        }
    }
    VerticalScrollBar(
        adapter = rememberScrollBarAdapter(listState),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
    )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 外观设置二级页面
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun AppearanceSettingsScreen(
    uiState: MainUiState,
    onThemeBaseModeChanged: (Int) -> Unit,
    onToggleMonetTheme: () -> Unit,
    onToggleFloatingNavigationBar: () -> Unit = {},
    onToggleBlurEffect: () -> Unit = {},
    onFloatingBarEffectChange: (FloatingBarEffect) -> Unit = {},
    onBack: () -> Unit,
    innerPadding: PaddingValues = PaddingValues(0.dp)
) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = innerPadding
    ) {
        // 主题
        item {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 12.dp)
                    .fillMaxWidth()
            ) {
                val baseMode = ColorMode.fromValue(uiState.colorMode).toNonMonetMode()
                val themeModeOptions = listOf("跟随系统", "浅色模式", "深色模式")
                OverlayDropdownPreference(
                    title = "主题模式",
                    summary = "选择应用显示主题",
                    items = themeModeOptions,
                    selectedIndex = baseMode,
                    startAction = {
                        Icon(
                            MiuixIcons.Theme,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "主题模式",
                            tint = colorScheme.onBackground
                        )
                    },
                    onSelectedIndexChange = onThemeBaseModeChanged
                )

                SwitchPreference(
                    title = "莫奈取色",
                    summary = "从系统壁纸提取动态 MD3 配色",
                    startAction = {
                        Icon(
                            MiuixIcons.Image,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "莫奈取色",
                            tint = colorScheme.onBackground
                        )
                    },
                    checked = ColorMode.fromValue(uiState.colorMode).isMonet,
                    onCheckedChange = { onToggleMonetTheme() }
                )
            }
        }

        // 导航栏
        item {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 12.dp)
                    .fillMaxWidth()
            ) {
                SwitchPreference(
                    title = "启用悬浮栏",
                    summary = "关闭后底栏贴底显示",
                    startAction = {
                        Icon(
                            MiuixIcons.Tune,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "启用悬浮栏",
                            tint = colorScheme.onBackground
                        )
                    },
                    checked = uiState.floatingNavigationBar,
                    onCheckedChange = { onToggleFloatingNavigationBar() }
                )

                SwitchPreference(
                    title = "模糊",
                    summary = "顶栏和底栏使用模糊效果",
                    startAction = {
                        Icon(
                            MiuixIcons.Tune,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "模糊",
                            tint = colorScheme.onBackground
                        )
                    },
                    checked = uiState.blurEffect,
                    onCheckedChange = { onToggleBlurEffect() }
                )

                // 悬浮栏的三种效果互斥，用三选一菜单
                if (uiState.floatingNavigationBar) {
                    OverlayDropdownPreference(
                        title = "悬浮栏效果",
                        summary = "悬浮底栏的视觉样式",
                        items = FloatingBarEffect.entries.map { it.label },
                        selectedIndex = FloatingBarEffect.entries.indexOf(uiState.floatingBarEffect)
                            .coerceAtLeast(0),
                        startAction = {
                            Icon(
                                MiuixIcons.Tune,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = "悬浮栏效果",
                                tint = colorScheme.onBackground
                            )
                        },
                        onSelectedIndexChange = { index ->
                            onFloatingBarEffectChange(FloatingBarEffect.entries[index])
                        }
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(LocalMainBottomContentPadding.current))
        }
    }
    VerticalScrollBar(
        adapter = rememberScrollBarAdapter(listState),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
    )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = colorScheme.onSurfaceVariantActions,
            style = MiuixTheme.textStyles.body2
        )
        Text(
            text = value,
            color = colorScheme.onSurface,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun PreviewFeaturesScreen(
    onOpenQualityUpgrade: () -> Unit,
    onBack: () -> Unit,
    innerPadding: PaddingValues = PaddingValues(0.dp),
) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding,
        ) {
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                ) {
                    BasicComponent(
                        title = "测试功能",
                        summary = "这里的功能仍在试验，界面和结果都可能随时调整，请先备份重要文件。",
                        startAction = {
                            Icon(
                                MiuixIcons.Info,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = "测试功能",
                                tint = colorScheme.onBackground
                            )
                        },
                    )
                    ArrowPreference(
                        title = "音质升级",
                        summary = "扫描本地曲库，尝试升级到更高音质",
                        startAction = {
                            Icon(
                                MiuixIcons.Tune,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = "音质升级",
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = onOpenQualityUpgrade,
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(LocalMainBottomContentPadding.current))
            }
        }
        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 关于二级页面
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun AboutSettingsScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onShareLog: () -> Unit,
    onBack: () -> Unit,
    innerPadding: PaddingValues = PaddingValues(0.dp),
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val versionSummary = remember(context) { OfficialBuild.versionSummary(context) }
    var showDisclaimerDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding,
        ) {
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                ) {
                    ArrowPreference(
                        title = "使用前须知",
                        summary = "免责声明与使用限制",
                        startAction = {
                            Icon(
                                MiuixIcons.Notes,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = "使用前须知",
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = { showDisclaimerDialog = true }
                    )

                    ArrowPreference(
                        title = "版本",
                        summary = versionSummary,
                        startAction = {
                            Icon(
                                MiuixIcons.Info,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = "版本",
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("version", versionSummary))
                            Toast.makeText(context, "已复制版本号", Toast.LENGTH_SHORT).show()
                        },
                    )

                    ArrowPreference(
                        title = "检查更新",
                        summary = if (uiState.isCheckingUpdate) "检查中..." else "检查是否有新版本可用",
                        startAction = {
                            Icon(
                                MiuixIcons.FileDownloads,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = "检查更新",
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = { viewModel.checkForUpdate() }
                    )

                    ArrowPreference(
                        title = "GitHub",
                        summary = OfficialBuild.REPO_URL,
                        startAction = {
                            Icon(
                                MiuixIcons.Link,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = "GitHub",
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, OfficialBuild.RELEASES_URL.toUri()),
                            )
                        },
                    )

                    ArrowPreference(
                        title = "导出日志",
                        summary = "用于反馈问题；可能包含设备型号与本地路径，请勿公开分享",
                        startAction = {
                            Icon(
                                MiuixIcons.Report,
                                modifier = Modifier.padding(end = 6.dp),
                                contentDescription = "导出日志",
                                tint = colorScheme.onBackground
                            )
                        },
                        onClick = onShareLog
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(LocalMainBottomContentPadding.current))
            }
        }
        DisclaimerDialog(
            show = showDisclaimerDialog,
            requireAccept = false,
            onDismiss = { showDisclaimerDialog = false },
        )

        // Update check dialog
        uiState.updateInfo?.let { info ->
            if (info.hasUpdate) {
                WindowDialog(
                    show = true,
                    title = "发现新版本 v${info.latestVersion}",
                    summary = info.releaseNotes.take(200).ifBlank { "暂无更新日志" },
                    onDismissRequest = { viewModel.dismissUpdateDialog() }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        if (info.releaseNotes.length > 200) {
                            Text(
                                text = "...",
                                style = MiuixTheme.textStyles.body2,
                                color = colorScheme.onSurfaceVariantActions,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                text = "稍后",
                                onClick = { viewModel.dismissUpdateDialog() }
                            )
                            Spacer(Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, info.downloadUrl.toUri())
                                    )
                                    viewModel.dismissUpdateDialog()
                                }
                            ) {
                                Text("前往下载")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        uiState.updateError?.let { error ->
            LaunchedEffect(error) {
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                viewModel.consumeUpdateFeedback()
            }
        }
        uiState.updateToast?.let { toast ->
            LaunchedEffect(toast) {
                Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
                viewModel.consumeUpdateFeedback()
            }
        }
        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

