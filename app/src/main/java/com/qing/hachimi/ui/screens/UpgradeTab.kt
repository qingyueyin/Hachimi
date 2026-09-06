package com.qing.hachimi.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.qing.hachimi.data.model.*
import com.qing.hachimi.ui.animation.LocalScrollDirection
import com.qing.hachimi.ui.animation.directionalEntrance
import com.qing.hachimi.ui.animation.rememberScrollDirection
import com.qing.hachimi.ui.theme.AppSpacing
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import androidx.compose.ui.unit.dp
import java.io.File

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun UpgradeTab(
    uiState: UpgradeUiState,
    onAddFolder: () -> Unit,
    onRemoveFolder: (File) -> Unit,
    onStartScan: () -> Unit,
    onSelectUpgradeMode: (UpgradeMode) -> Unit,
    onToggleSongSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onCheckUpgrades: () -> Unit,
    onStartUpgrade: () -> Unit,
    onCancelUpgrade: () -> Unit,
    onToggleFolderExpand: (String) -> Unit,
    onExpandAllFolders: () -> Unit,
    onCollapseAllFolders: () -> Unit,
    onSelectFolderSongs: (String) -> Unit,
    onDeselectFolderSongs: (String) -> Unit,
    innerPadding: PaddingValues,
    scrollToTopTrigger: Int = 0,
) {
    val showBottomAction = uiState.selectedSongs.isNotEmpty()
    val mainBottomContentPadding = LocalMainBottomContentPadding.current
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) {
            listState.animateScrollToItem(0)
        }
    }
    Scaffold(
        bottomBar = {
            if (showBottomAction) {
                Surface(color = colorScheme.surface) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppSpacing.md)
                            .padding(
                                top = AppSpacing.sm,
                                bottom = mainBottomContentPadding,
                            ),
                    ) {
                        if (uiState.upgradeCandidates.isNotEmpty() && !uiState.isUpgrading) {
                            Text(
                                "${uiState.upgradeCandidates.size} 首可以升级",
                                style = MiuixTheme.textStyles.body2,
                                color = colorScheme.onSurfaceVariantActions,
                            )
                            Spacer(Modifier.height(AppSpacing.xs))
                        }
                        Button(
                            onClick = when {
                                uiState.isSearching || uiState.isUpgrading -> onCancelUpgrade
                                uiState.upgradeCandidates.isNotEmpty() -> onStartUpgrade
                                else -> onCheckUpgrades
                            },
                            enabled = !uiState.isScanning,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                when {
                                    uiState.isSearching -> "取消检查"
                                    uiState.isUpgrading -> "取消升级"
                                    uiState.upgradeCandidates.isNotEmpty() -> "开始升级 (${uiState.upgradeCandidates.size} 首)"
                                    else -> "检查音质 (${uiState.selectedSongs.size} 首)"
                                }
                            )
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .only(WindowInsetsSides.Horizontal)
    ) { scaffoldPadding ->
        CompositionLocalProvider(LocalScrollDirection provides rememberScrollDirection(listState).value) {
        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .padding(horizontal = AppSpacing.md),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = if (showBottomAction) AppSpacing.md else {
                    mainBottomContentPadding
                }
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            item { ScreenHeading("音质升级") }
            item { FolderSelectionSection(selectedFolders = uiState.selectedFolders, onAddFolder = onAddFolder, onRemoveFolder = onRemoveFolder) }

            if (uiState.selectedFolders.isNotEmpty()) {
                item {
                    Button(onClick = onStartScan, enabled = !uiState.isScanning, modifier = Modifier.fillMaxWidth()) {
                        Text(if (uiState.isScanning) "扫描中..." else "扫描")
                    }
                }
            }

            if (uiState.isScanning && uiState.scanProgress != null) {
                item { ProgressCard("扫描中", uiState.scanProgress.current.toFloat() / uiState.scanProgress.total.toFloat(), "${uiState.scanProgress.currentFolder} (${uiState.scanProgress.current}/${uiState.scanProgress.total})") }
            }

            if (uiState.scannedFolders.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(AppSpacing.md)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("扫描结果", style = MiuixTheme.textStyles.headline2, modifier = Modifier.weight(1f))
                                Text("${uiState.selectedSongs.size}/${uiState.scannedFolders.sumOf { it.songs.size }}", style = MiuixTheme.textStyles.body2, color = colorScheme.onSurfaceVariantActions)
                            }
                            Spacer(Modifier.height(AppSpacing.sm))
                            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                                TextButton(text = if (uiState.selectedSongs.size < uiState.scannedFolders.sumOf { it.songs.size }) "全选" else "取消全选", onClick = if (uiState.selectedSongs.size < uiState.scannedFolders.sumOf { it.songs.size }) onSelectAll else onDeselectAll)
                                TextButton(
                                    text = if (uiState.expandedFolders.size == uiState.scannedFolders.size) "全部收起" else "全部展开",
                                    onClick = if (uiState.expandedFolders.size == uiState.scannedFolders.size) {
                                        onCollapseAllFolders
                                    } else {
                                        onExpandAllFolders
                                    },
                                )
                            }
                        }
                    }
                }
            }

            items(uiState.scannedFolders, key = { it.folder.absolutePath }) { scannedFolder ->
                FolderSection(
                    scannedFolder = scannedFolder,
                    isExpanded = uiState.expandedFolders.contains(scannedFolder.folder.absolutePath),
                    selectedSongs = uiState.selectedSongs,
                    onToggleExpand = { onToggleFolderExpand(scannedFolder.folder.absolutePath) },
                    onToggleSong = onToggleSongSelection,
                    onSelectAll = { onSelectFolderSongs(scannedFolder.folder.absolutePath) },
                    onDeselectAll = { onDeselectFolderSongs(scannedFolder.folder.absolutePath) }
                )
            }

            if (uiState.isSearching && uiState.searchProgress != null) {
                item { ProgressCard("检查在线音质", uiState.searchProgress.current.toFloat() / uiState.searchProgress.total.toFloat(), uiState.searchProgress.currentSong) }
            }

            if (uiState.upgradeCandidates.isNotEmpty()) {
                item { UpgradeCandidatesSection(uiState.upgradeCandidates) }
            }

            if (uiState.upgradeCandidates.isNotEmpty() && !uiState.isSearching) {
                item {
                    val modes = listOf(UpgradeMode.AUTO_REPLACE, UpgradeMode.KEEP_ORIGINAL)
                    OverlayDropdownPreference(
                        title = "升级方式",
                        summary = if (uiState.selectedUpgradeMode == UpgradeMode.AUTO_REPLACE) {
                            "替换原文件并保留备份"
                        } else {
                            "新文件保存在原文件旁"
                        },
                        items = listOf("自动替换", "保留原文件"),
                        selectedIndex = modes.indexOf(uiState.selectedUpgradeMode).coerceAtLeast(0),
                        onSelectedIndexChange = { index -> onSelectUpgradeMode(modes[index]) },
                        enabled = !uiState.isUpgrading
                    )
                }
            }

            if (uiState.upgradeProgress != null) {
                item { ProgressCard("升级中", uiState.upgradeProgress.current.toFloat() / uiState.upgradeProgress.total.toFloat(), uiState.upgradeProgress.currentSong) }
            }

            if (uiState.statusMessage != null) {
                item {
                    Text(uiState.statusMessage, style = MiuixTheme.textStyles.body1, color = colorScheme.primary, modifier = Modifier.padding(vertical = AppSpacing.sm))
                }
            }

            if (uiState.error != null) {
                item {
                    Text(uiState.error, style = MiuixTheme.textStyles.body1, color = colorScheme.error, modifier = Modifier.padding(vertical = AppSpacing.sm))
                }
            }

            if (uiState.scannedFolders.isEmpty() && !uiState.isScanning && uiState.selectedFolders.isEmpty()) {
                item { Spacer(Modifier.height(48.dp))
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("添加文件夹开始扫描", style = MiuixTheme.textStyles.body1, color = colorScheme.onSurfaceVariantActions)
                    }
                }
            }

            item { Spacer(Modifier.height(AppSpacing.xxl)) }
        }
        VerticalScrollBar(
            adapter = rememberScrollBarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
        }
        }
    }
}

@Composable
private fun UpgradeCandidatesSection(candidates: List<UpgradeCandidate>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AppSpacing.md)) {
            Text("可升级歌曲", style = MiuixTheme.textStyles.headline2)
            Spacer(Modifier.height(AppSpacing.sm))
            candidates.take(20).forEachIndexed { index, candidate ->
                if (index > 0) {
                    HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.2f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            candidate.song.name,
                            style = MiuixTheme.textStyles.body1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${candidate.song.qualityInfo.format.uppercase()} ${candidate.song.qualityInfo.bitrate}kbps",
                            style = MiuixTheme.textStyles.body2,
                            color = colorScheme.onSurfaceVariantActions,
                        )
                    }
                    Text(
                        "${candidate.onlineVersion.qualityInfo.format.uppercase()} ${candidate.onlineVersion.qualityInfo.bitrate}kbps",
                        style = MiuixTheme.textStyles.body2,
                        color = colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (candidates.size > 20) {
                Text(
                    "另有 ${candidates.size - 20} 首",
                    style = MiuixTheme.textStyles.body2,
                    color = colorScheme.onSurfaceVariantActions,
                )
            }
        }
    }
}

@Composable
private fun FolderSelectionSection(selectedFolders: List<File>, onAddFolder: () -> Unit, onRemoveFolder: (File) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AppSpacing.md)) {
            Text("文件夹", style = MiuixTheme.textStyles.headline2)
            Spacer(Modifier.height(AppSpacing.sm))
            if (selectedFolders.isEmpty()) {
                Text("选择音乐文件夹扫描歌曲", style = MiuixTheme.textStyles.body2, color = colorScheme.onSurfaceVariantActions)
                Spacer(Modifier.height(AppSpacing.sm))
            } else {
                selectedFolders.forEach { folder ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(folder.name, style = MiuixTheme.textStyles.body1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(folder.absolutePath, style = MiuixTheme.textStyles.body2, color = colorScheme.onSurfaceVariantActions, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onRemoveFolder(folder) }) {
                            Icon(MiuixIcons.Close, contentDescription = "移除", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Button(onClick = onAddFolder, modifier = Modifier.fillMaxWidth()) { Text(if (selectedFolders.isEmpty()) "选择文件夹" else "添加") }
        }
    }
}

@Composable
private fun FolderSection(
    scannedFolder: ScannedFolder,
    isExpanded: Boolean,
    selectedSongs: Set<String>,
    onToggleExpand: () -> Unit,
    onToggleSong: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit
) {
    val folder = scannedFolder.folder
    val songs = scannedFolder.songs
    val selectedInFolder = songs.count { it.localFile.absolutePath in selectedSongs }

    Card(modifier = Modifier.fillMaxWidth().directionalEntrance(folder.absolutePath)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggleExpand() }.padding(AppSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(folder.name, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${songs.size} 首", style = MiuixTheme.textStyles.body2, color = colorScheme.onSurfaceVariantActions)
                }
                Text(if (isExpanded) "收起" else "展开", style = MiuixTheme.textStyles.body2, color = colorScheme.primary)
            }

            AnimatedVisibility(visible = isExpanded) {
                Column {
                    HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.2f))
                    Row(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(text = if (selectedInFolder < songs.size) "全选" else "取消全选", onClick = if (selectedInFolder < songs.size) onSelectAll else onDeselectAll)
                        Spacer(Modifier.weight(1f))
                        Text("${selectedInFolder}/${songs.size}", style = MiuixTheme.textStyles.body2, color = colorScheme.onSurfaceVariantActions)
                    }
                    songs.forEach { song ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onToggleSong(song.localFile.absolutePath) }.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                state = if (selectedSongs.contains(song.localFile.absolutePath)) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off,
                                onClick = { onToggleSong(song.localFile.absolutePath) }
                            )
                            Spacer(Modifier.width(AppSpacing.sm))
                            Column(Modifier.weight(1f)) {
                                Text(song.name, style = MiuixTheme.textStyles.body1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${song.artists} · ${song.album}", style = MiuixTheme.textStyles.body2, color = colorScheme.onSurfaceVariantActions, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${song.qualityInfo.format.uppercase()} ${song.qualityInfo.bitrate}kbps", style = MiuixTheme.textStyles.body2, color = colorScheme.onSurfaceVariantActions)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(title: String, progress: Float, subtitle: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(AppSpacing.md)) {
            Text(title, style = MiuixTheme.textStyles.headline2)
            Spacer(Modifier.height(AppSpacing.sm))
            LinearProgressIndicator(progress = progress.coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(AppSpacing.xs))
            Text(subtitle, style = MiuixTheme.textStyles.body2, color = colorScheme.onSurfaceVariantActions, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
