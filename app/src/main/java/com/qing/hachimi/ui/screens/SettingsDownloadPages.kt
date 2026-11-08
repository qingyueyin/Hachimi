package com.qing.hachimi.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.qing.hachimi.data.api.NeteaseApi
import com.qing.hachimi.data.local.FolderNamingFormat
import com.qing.hachimi.data.local.NamingFormat
import com.qing.hachimi.util.StoragePermissionManager
import com.qing.hachimi.util.storageAccessSummary
import java.io.File
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.FolderFill
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.window.WindowDialog

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
    onToggleDownloadLyrics: () -> Unit,
    onToggleSaveTlLrc: () -> Unit = {},
    onToggleSaveRomaLrc: () -> Unit = {},
    onToggleSaveYrc: () -> Unit = {},
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
                    summary = if (uiState.downloadDir.isNotBlank()) {
                        uiState.downloadDir
                    } else "Hachimi",
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
// 文件与标签二级页面
// ═════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun FileTagSettingsScreen(
    uiState: MainUiState,
    onSetNamingFormat: (NamingFormat) -> Unit,
    onSetFolderNamingFormat: (FolderNamingFormat) -> Unit,
    onToggleEnableWriteTags: () -> Unit,
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
        item {
            Card(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(top = 12.dp)
                    .fillMaxWidth()
            ) {
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

                var showAdvanced by remember { mutableStateOf(false) }
                ArrowPreference(
                    title = "高级选项",
                    summary = if (showAdvanced) {
                        "收起命名模板与艺术家分隔符"
                    } else {
                        "命名模板与艺术家分隔符"
                    },
                    startAction = {
                        Icon(
                            MiuixIcons.Settings,
                            modifier = Modifier.padding(end = 6.dp),
                            contentDescription = "高级选项",
                            tint = colorScheme.onBackground
                        )
                    },
                    onClick = { showAdvanced = !showAdvanced }
                )

                var showNamingTemplateDialog by remember { mutableStateOf(false) }
                var namingTemplateInput by remember { mutableStateOf(uiState.customNamingTemplate) }

                var showDelimiterDialog by remember { mutableStateOf(false) }
                var delimiterInput by remember { mutableStateOf(uiState.artistDelimiter) }

                AnimatedVisibility(visible = showAdvanced) {
                    Column {
                        ArrowPreference(
                            title = "自定义文件命名模板",
                            summary = if (uiState.customNamingTemplate.isNotBlank()) {
                                uiState.customNamingTemplate
                            } else {
                                "使用默认命名格式"
                            },
                            startAction = {
                                Icon(
                                    MiuixIcons.Notes,
                                    modifier = Modifier.padding(end = 6.dp),
                                    contentDescription = "自定义文件命名模板",
                                    tint = colorScheme.onBackground
                                )
                            },
                            onClick = {
                                namingTemplateInput = uiState.customNamingTemplate
                                showNamingTemplateDialog = true
                            }
                        )

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
                    }
                }

                if (showNamingTemplateDialog) {
                    WindowDialog(
                        show = true,
                        title = "自定义文件命名模板",
                        summary = "可用变量：{song} {artist} {album} {quality}",
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
