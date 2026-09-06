package com.qing.hachimi.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qing.hachimi.data.api.NeteaseApi
import com.qing.hachimi.data.local.FolderNamingFormat
import com.qing.hachimi.data.local.NamingFormat
import com.qing.hachimi.ui.theme.ColorMode
import com.qing.hachimi.util.OfficialBuild
import com.qing.hachimi.util.StoragePermissionManager
import com.qing.hachimi.util.storageAccessSummary
import java.io.File
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
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
import top.yukonga.miuix.kmp.icon.extended.FolderFill
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

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun SettingsTab(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onLogout: () -> Unit,
    onPickDownloadDir: () -> Unit = {},
    onToggleFuckAiMode: () -> Unit = {},
    onQualitySelect: (String) -> Unit = {},
    onSetNamingFormat: (NamingFormat) -> Unit = {},
    onToggleDownloadLyrics: () -> Unit = {},
    onSetFolderNamingFormat: (FolderNamingFormat) -> Unit = {},
    onToggleEnableWriteTags: () -> Unit = {},
    onOpenAppearanceSettings: () -> Unit = {},
    onOpenDownloadSettings: () -> Unit = {},
    onOpenAccountSettings: () -> Unit = {},
    onOpenPreviewFeatures: () -> Unit = {},
    onThemeBaseModeChanged: (Int) -> Unit = {},
    onToggleMonetTheme: () -> Unit = {},
    onShareLog: () -> Unit = {},
    onExportCookies: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onClearSeenClick: () -> Unit = {},
    onClearCache: () -> Unit = {},
    innerPadding: PaddingValues = PaddingValues(0.dp),
    scrollToTopTrigger: Int = 0,
) {
    val listState = rememberLazyListState()
    var showDisclaimerDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val versionSummary = remember(context) { OfficialBuild.versionSummary(context) }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            listState.animateScrollToItem(0)
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = innerPadding,
    ) {
        // ═════════════════════════════════════════════════════════════
        // 页面大标题
        // ═════════════════════════════════════════════════════════════
        item {
            ScreenHeading(
                title = "设置",
                modifier = Modifier.padding(horizontal = 12.dp),
            )
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
            item {
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
                    summary = "音质、目录、命名、歌词等下载设置",
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
        // 关于
        // ═════════════════════════════════════════════════════════════
        item {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(vertical = 12.dp)
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
                            Intent(Intent.ACTION_VIEW, Uri.parse(OfficialBuild.RELEASES_URL)),
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
                    onClick = onClearSeenClick
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

        item {
            Spacer(modifier = Modifier.height(LocalMainBottomContentPadding.current))
        }
    }
    DisclaimerDialog(
        show = showDisclaimerDialog,
        requireAccept = false,
        onDismiss = { showDisclaimerDialog = false },
    )
    VerticalScrollBar(
        adapter = rememberScrollBarAdapter(listState),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
    )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 下载设置二级页面
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun DownloadSettingsScreen(
    uiState: MainUiState,
    onPickDownloadDir: () -> Unit,
    onOpenStoragePermissionSettings: () -> Unit = {},
    onToggleFuckAiMode: () -> Unit,
    onQualitySelect: (String) -> Unit,
    onSetNamingFormat: (NamingFormat) -> Unit,
    onToggleDownloadLyrics: () -> Unit,
    onSetFolderNamingFormat: (FolderNamingFormat) -> Unit,
    onToggleEnableWriteTags: () -> Unit,
    onToggleSaveTlLrc: () -> Unit = {},
    onToggleSaveRomaLrc: () -> Unit = {},
    onToggleSaveYrc: () -> Unit = {},
    onSetCustomNamingTemplate: (String) -> Unit = {},
    onSetArtistDelimiter: (String) -> Unit = {},
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
        item { SecondaryPageHeader("下载设置", onBack) }
        item {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 12.dp)
                    .fillMaxWidth()
            ) {
                val qualityOptions = uiState.qualityPickerOptions.map { key ->
                    NeteaseApi.QUALITY_MAP[key] ?: key
                }
                OverlayDropdownPreference(
                    title = "默认音质",
                    summary = "选择下载音质",
                    items = qualityOptions,
                    selectedIndex = uiState.qualityPickerOptions.indexOf(uiState.quality).coerceAtLeast(0),
                    startAction = {
                        Icon(
                            MiuixIcons.Music,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "默认音质",
                            tint = colorScheme.onBackground
                        )
                    },
                    onSelectedIndexChange = { index ->
                        onQualitySelect(uiState.qualityPickerOptions[index])
                    }
                )

                ArrowPreference(
                    title = "下载目录",
                    summary = if (uiState.downloadDir.isNotBlank())
                        uiState.downloadDir
                    else "Hachimi",
                    startAction = {
                        Icon(
                            MiuixIcons.Folder,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "下载目录",
                            tint = colorScheme.onBackground
                        )
                    },
                    onClick = onPickDownloadDir
                )

                var storageSummary by remember { mutableStateOf("") }
                fun refreshStorageStatus() {
                    val granted = StoragePermissionManager.hasAllFilesAccess()
                    val dir = uiState.downloadDir.takeIf { it.isNotBlank() }?.let(::File)
                    val writable = dir?.let { StoragePermissionManager.probeCanWrite(it) } ?: granted
                    storageSummary = storageAccessSummary(granted, writable)
                }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, uiState.downloadDir) {
                    refreshStorageStatus()
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) refreshStorageStatus()
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                ArrowPreference(
                    title = "存储权限",
                    summary = storageSummary.ifBlank { "点击查看授权状态" },
                    startAction = {
                        Icon(
                            MiuixIcons.Settings,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "存储权限",
                            tint = colorScheme.onBackground
                        )
                    },
                    onClick = onOpenStoragePermissionSettings
                )

                SwitchPreference(
                    title = "Fuck AI Mode",
                    summary = "隐藏AI增强音质，保留杜比全景声",
                    startAction = {
                        Icon(
                            MiuixIcons.Settings,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "Fuck AI Mode",
                            tint = colorScheme.onBackground
                        )
                    },
                    checked = uiState.fuckAiMode,
                    onCheckedChange = { onToggleFuckAiMode() }
                )

                val namingOptions = NamingFormat.entries.map { it.label }
                OverlayDropdownPreference(
                    title = "文件命名",
                    summary = "设置下载文件命名格式",
                    items = namingOptions,
                    selectedIndex = NamingFormat.entries.indexOf(uiState.namingFormat),
                    startAction = {
                        Icon(
                            MiuixIcons.Notes,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "文件命名",
                            tint = colorScheme.onBackground
                        )
                    },
                    onSelectedIndexChange = { index ->
                        onSetNamingFormat(NamingFormat.entries[index])
                    }
                )

                SwitchPreference(
                    title = "下载歌词",
                    summary = "同时下载 .lrc 歌词文件",
                    startAction = {
                        Icon(
                            MiuixIcons.Notes,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "下载歌词",
                            tint = colorScheme.onBackground
                        )
                    },
                    checked = uiState.downloadLyrics,
                    onCheckedChange = { onToggleDownloadLyrics() }
                )

                val folderNamingOptions = FolderNamingFormat.entries.map { it.label }
                OverlayDropdownPreference(
                    title = "下载文件夹规则",
                    summary = "设置下载文件夹命名规则",
                    items = folderNamingOptions,
                    selectedIndex = FolderNamingFormat.entries.indexOf(uiState.folderNamingFormat),
                    startAction = {
                        Icon(
                            MiuixIcons.FolderFill,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "下载文件夹规则",
                            tint = colorScheme.onBackground
                        )
                    },
                    onSelectedIndexChange = { index ->
                        onSetFolderNamingFormat(FolderNamingFormat.entries[index])
                    }
                )

                SwitchPreference(
                    title = "写入音频标签",
                    summary = "下载完成后写入歌曲信息与封面（默认关闭）",
                    startAction = {
                        Icon(
                            MiuixIcons.Info,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "写入音频标签",
                            tint = colorScheme.onBackground
                        )
                    },
                    checked = uiState.enableWriteTags,
                    onCheckedChange = { onToggleEnableWriteTags() }
                )

                SwitchPreference(
                    title = "保存歌词翻译",
                    summary = "在歌词中包含翻译（如果可用）",
                    startAction = {
                        Icon(
                            MiuixIcons.Notes,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "保存歌词翻译",
                            tint = colorScheme.onBackground
                        )
                    },
                    checked = uiState.saveTlLrc,
                    onCheckedChange = { onToggleSaveTlLrc() }
                )

                SwitchPreference(
                    title = "保存歌词罗马音",
                    summary = "在歌词中包含罗马音（如果可用）",
                    startAction = {
                        Icon(
                            MiuixIcons.Notes,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "保存歌词罗马音",
                            tint = colorScheme.onBackground
                        )
                    },
                    checked = uiState.saveRomaLrc,
                    onCheckedChange = { onToggleSaveRomaLrc() }
                )

                SwitchPreference(
                    title = "保存逐字歌词",
                    summary = "尝试保存逐字歌词（如果可用）",
                    startAction = {
                        Icon(
                            MiuixIcons.Notes,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "保存逐字歌词",
                            tint = colorScheme.onBackground
                        )
                    },
                    checked = uiState.saveYrc,
                    onCheckedChange = { onToggleSaveYrc() }
                )

                var showNamingTemplateDialog by remember { mutableStateOf(false) }
                var namingTemplateInput by remember { mutableStateOf(uiState.customNamingTemplate) }

                ArrowPreference(
                    title = "自定义文件命名模板",
                    summary = if (uiState.customNamingTemplate.isNotBlank()) uiState.customNamingTemplate else "使用默认命名格式",
                    startAction = {
                        Icon(
                            MiuixIcons.Notes,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "自定义命名模板",
                            tint = colorScheme.onBackground
                        )
                    },
                    onClick = {
                        namingTemplateInput = uiState.customNamingTemplate
                        showNamingTemplateDialog = true
                    }
                )

                if (showNamingTemplateDialog) {
                    WindowDialog(
                        show = true,
                        title = "自定义命名模板",
                        summary = "可用变量：\${name} \${artists} \${album} \${quality}\n\n留空使用默认命名格式",
                        onDismissRequest = { showNamingTemplateDialog = false }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                            TextField(
                                value = namingTemplateInput,
                                onValueChange = { namingTemplateInput = it },
                                label = "命名模板",
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    text = "取消",
                                    onClick = { showNamingTemplateDialog = false }
                                )
                                Spacer(Modifier.width(12.dp))
                                Button(
                                    onClick = {
                                        onSetCustomNamingTemplate(namingTemplateInput.trim())
                                        showNamingTemplateDialog = false
                                    }
                                ) {
                                    Text("保存")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                var showDelimiterDialog by remember { mutableStateOf(false) }
                var delimiterInput by remember { mutableStateOf(uiState.artistDelimiter) }

                ArrowPreference(
                    title = "艺术家分隔符",
                    summary = "当前：${uiState.artistDelimiter}",
                    startAction = {
                        Icon(
                            MiuixIcons.Settings,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "艺术家分隔符",
                            tint = colorScheme.onBackground
                        )
                    },
                    onClick = {
                        delimiterInput = uiState.artistDelimiter
                        showDelimiterDialog = true
                    }
                )

                if (showDelimiterDialog) {
                    WindowDialog(
                        show = true,
                        title = "艺术家分隔符",
                        summary = "多个艺术家之间的分隔符（同时影响文件名和 ID3 标签）",
                        onDismissRequest = { showDelimiterDialog = false }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                            TextField(
                                value = delimiterInput,
                                onValueChange = { delimiterInput = it },
                                label = "分隔符",
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    text = "取消",
                                    onClick = { showDelimiterDialog = false }
                                )
                                Spacer(Modifier.width(12.dp))
                                Button(
                                    onClick = {
                                        onSetArtistDelimiter(if (delimiterInput.isBlank()) "/" else delimiterInput)
                                        showDelimiterDialog = false
                                    }
                                ) {
                                    Text("保存")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
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
        item { SecondaryPageHeader("账户", onBack) }
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
    onToggleBlurEffect: () -> Unit = {},
    onToggleLiquidGlass: () -> Unit = {},
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
        item { SecondaryPageHeader("外观", onBack) }
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

                SwitchPreference(
                    title = "毛玻璃效果",
                    summary = "底部导航栏和顶栏使用模糊效果",
                    startAction = {
                        Icon(
                            MiuixIcons.Tune,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "毛玻璃效果",
                            tint = colorScheme.onBackground
                        )
                    },
                    checked = uiState.blurEffect,
                    onCheckedChange = { onToggleBlurEffect() }
                )

                SwitchPreference(
                    title = "液态玻璃",
                    summary = "为悬浮导航栏增加折射效果",
                    startAction = {
                        Icon(
                            MiuixIcons.Tune,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "液态玻璃",
                            tint = colorScheme.onBackground
                        )
                    },
                    checked = uiState.liquidGlass,
                    onCheckedChange = { onToggleLiquidGlass() }
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
            item { SecondaryPageHeader("预览功能", onBack) }
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

