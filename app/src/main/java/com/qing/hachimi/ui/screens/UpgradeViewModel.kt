package com.qing.hachimi.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qing.hachimi.data.local.CookieManager
import com.qing.hachimi.data.model.*
import com.qing.hachimi.service.UpgradeService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class ScanProgress(
    val current: Int,
    val total: Int,
    val currentFolder: String
)

data class SearchProgress(
    val current: Int,
    val total: Int,
    val currentSong: String
)

data class UpgradeCandidate(
    val song: SongInfo,
    val onlineVersion: OnlineVersion,
)

data class UpgradeUiState(
    val selectedFolders: List<File> = emptyList(),
    val scannedFolders: List<ScannedFolder> = emptyList(),
    val isScanning: Boolean = false,
    val scanProgress: ScanProgress? = null,
    val isSearching: Boolean = false,
    val searchProgress: SearchProgress? = null,
    val isUpgrading: Boolean = false,
    val upgradeProgress: UpgradeProgress? = null,
    val selectedUpgradeMode: UpgradeMode = UpgradeMode.AUTO_REPLACE,
    val selectedSongs: Set<String> = emptySet(),
    val upgradeCandidates: List<UpgradeCandidate> = emptyList(),
    val expandedFolders: Set<String> = emptySet(),
    val statusMessage: String? = null,
    val error: String? = null
)

class UpgradeViewModel(
    private val upgradeService: UpgradeService,
    private val cookieManager: CookieManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpgradeUiState())
    val uiState: StateFlow<UpgradeUiState> = _uiState.asStateFlow()
    private var operationJob: Job? = null

    fun addFolder(folder: File) {
        if (_uiState.value.isScanning || _uiState.value.isSearching || _uiState.value.isUpgrading) return
        val currentFolders = _uiState.value.selectedFolders.toMutableList()
        if (!currentFolders.contains(folder)) {
            currentFolders.add(folder)
            _uiState.value = _uiState.value.copy(
                selectedFolders = currentFolders,
                expandedFolders = _uiState.value.expandedFolders + folder.absolutePath,
                upgradeCandidates = emptyList(),
            )
        }
    }

    fun removeFolder(folder: File) {
        if (_uiState.value.isScanning || _uiState.value.isSearching || _uiState.value.isUpgrading) return
        val currentFolders = _uiState.value.selectedFolders.toMutableList()
        currentFolders.remove(folder)
        val removedPath = folder.absolutePath
        _uiState.value = _uiState.value.copy(
            selectedFolders = currentFolders,
            scannedFolders = _uiState.value.scannedFolders.filter { it.folder.absolutePath != removedPath },
            selectedSongs = _uiState.value.selectedSongs.filter { !it.startsWith(removedPath) }.toSet(),
            expandedFolders = _uiState.value.expandedFolders - removedPath,
            upgradeCandidates = emptyList(),
        )
    }

    fun startScan() {
        startScan(completionStatus = null)
    }

    private fun startScan(completionStatus: String?) {
        if (_uiState.value.isScanning || _uiState.value.isSearching || _uiState.value.isUpgrading) return
        val folders = _uiState.value.selectedFolders
        if (folders.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "请先选择文件夹")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                scanProgress = ScanProgress(0, folders.size, ""),
                error = null,
                statusMessage = null,
                scannedFolders = emptyList(),
                selectedSongs = emptySet(),
                upgradeCandidates = emptyList(),
            )

            try {
                val scannedFolders = upgradeService.scanFolders(folders) { current, total, folderName ->
                    _uiState.value = _uiState.value.copy(
                        scanProgress = ScanProgress(current, total, folderName)
                    )
                }

                val totalSongs = scannedFolders.sumOf { it.songs.size }
                _uiState.value = _uiState.value.copy(
                    scannedFolders = scannedFolders,
                    isScanning = false,
                    scanProgress = null,
                    expandedFolders = emptySet(),
                    statusMessage = completionStatus ?: "扫描完成，找到 $totalSongs 首歌曲"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scanProgress = null,
                    error = "扫描失败: ${e.message}"
                )
            }
        }
    }

    fun checkForUpgrades() {
        if (_uiState.value.isScanning || _uiState.value.isSearching || _uiState.value.isUpgrading) return
        val selectedSongs = _uiState.value.selectedSongs
        if (selectedSongs.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "请先选择要检查的歌曲")
            return
        }

        val cookies = cookieManager.getCookies()
        if (cookies.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "请先登录")
            return
        }

        operationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearching = true,
                error = null,
                statusMessage = null,
                upgradeCandidates = emptyList(),
            )

            try {
                val songsToCheck = _uiState.value.scannedFolders
                    .flatMap { it.songs }
                    .filter { it.localFile.absolutePath in selectedSongs }
                _uiState.value = _uiState.value.copy(
                    searchProgress = SearchProgress(0, songsToCheck.size, "")
                )

                val candidates = upgradeService.findUpgradeCandidates(
                    songsToCheck,
                    cookies,
                ) { current, total, songName ->
                    _uiState.value = _uiState.value.copy(
                        searchProgress = SearchProgress(current, total, songName)
                    )
                }.map { (song, version) ->
                    UpgradeCandidate(song, version)
                }

                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchProgress = null,
                    upgradeCandidates = candidates,
                    statusMessage = if (candidates.isEmpty()) {
                        "检查完成，没有发现更高音质版本"
                    } else {
                        "检查完成，${candidates.size} 首歌曲可以升级"
                    },
                )
            } catch (e: CancellationException) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchProgress = null,
                )
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchProgress = null,
                    error = "检查失败: ${e.message}",
                )
            } finally {
                operationJob = null
            }
        }
    }

    fun startUpgrade() {
        if (_uiState.value.isScanning || _uiState.value.isSearching || _uiState.value.isUpgrading) return
        val candidates = _uiState.value.upgradeCandidates
        if (candidates.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "请先检查可升级版本")
            return
        }

        val cookies = cookieManager.getCookies()
        if (cookies.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "请先登录")
            return
        }

        operationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isUpgrading = true,
                error = null,
                statusMessage = null,
            )

            try {
                var successCount = 0
                var failureCount = 0
                candidates.forEachIndexed { index, candidate ->
                    val song = candidate.song
                    val onlineVersion = candidate.onlineVersion
                    _uiState.value = _uiState.value.copy(
                        upgradeProgress = UpgradeProgress(
                            current = index + 1,
                            total = candidates.size,
                            currentSong = song.name,
                            speed = 0,
                            estimatedTimeRemaining = 0
                        )
                    )
                    val result = upgradeService.upgradeSong(
                        song,
                        onlineVersion,
                        _uiState.value.selectedUpgradeMode,
                        cookies
                    )
                    if (result.success) successCount++ else failureCount++
                }

                val completionStatus = when {
                    failureCount == 0 -> "升级完成，成功升级 $successCount 首歌曲"
                    successCount == 0 -> "升级失败，共 $failureCount 首歌曲未完成"
                    else -> "升级完成，成功 $successCount 首，失败 $failureCount 首"
                }
                _uiState.value = _uiState.value.copy(
                    isUpgrading = false,
                    upgradeProgress = null,
                    statusMessage = null,
                )

                startScan(completionStatus)

            } catch (e: CancellationException) {
                _uiState.value = _uiState.value.copy(
                    isUpgrading = false,
                    isSearching = false,
                    searchProgress = null,
                    upgradeProgress = null
                )
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUpgrading = false,
                    isSearching = false,
                    searchProgress = null,
                    upgradeProgress = null,
                    error = "升级失败: ${e.message}"
                )
            } finally {
                operationJob = null
            }
        }
    }

    fun cancelUpgrade() {
        val wasSearching = _uiState.value.isSearching
        operationJob?.cancel()
        operationJob = null
        _uiState.value = _uiState.value.copy(
            isUpgrading = false,
            isSearching = false,
            searchProgress = null,
            upgradeProgress = null,
            statusMessage = if (wasSearching) "检查已取消" else "升级已取消"
        )
    }

    fun selectUpgradeMode(mode: UpgradeMode) {
        _uiState.value = _uiState.value.copy(selectedUpgradeMode = mode)
    }

    fun toggleSongSelection(songPath: String) {
        val currentSelection = _uiState.value.selectedSongs.toMutableSet()
        if (currentSelection.contains(songPath)) {
            currentSelection.remove(songPath)
        } else {
            currentSelection.add(songPath)
        }
        updateSelection(currentSelection)
    }

    fun selectFolderSongs(folderPath: String) {
        val songsInFolder = _uiState.value.scannedFolders
            .firstOrNull { it.folder.absolutePath == folderPath }
            ?.songs
            ?.map { it.localFile.absolutePath }
            ?: return
        val currentSelection = _uiState.value.selectedSongs.toMutableSet()
        currentSelection.addAll(songsInFolder)
        updateSelection(currentSelection)
    }

    fun deselectFolderSongs(folderPath: String) {
        val songsInFolder = _uiState.value.scannedFolders
            .firstOrNull { it.folder.absolutePath == folderPath }
            ?.songs
            ?.map { it.localFile.absolutePath }
            ?: return
        val currentSelection = _uiState.value.selectedSongs.toMutableSet()
        currentSelection.removeAll(songsInFolder)
        updateSelection(currentSelection)
    }

    fun selectAllSongs() {
        val allSongs = _uiState.value.scannedFolders
            .flatMap { it.songs }
            .map { it.localFile.absolutePath }
            .toSet()
        updateSelection(allSongs)
    }

    fun deselectAllSongs() {
        updateSelection(emptySet())
    }

    private fun updateSelection(selection: Set<String>) {
        _uiState.value = _uiState.value.copy(
            selectedSongs = selection,
            upgradeCandidates = emptyList(),
            statusMessage = null,
            error = null,
        )
    }

    fun toggleFolderExpand(folderPath: String) {
        val current = _uiState.value.expandedFolders.toMutableSet()
        if (current.contains(folderPath)) {
            current.remove(folderPath)
        } else {
            current.add(folderPath)
        }
        _uiState.value = _uiState.value.copy(expandedFolders = current)
    }

    fun expandAllFolders() {
        val allPaths = _uiState.value.scannedFolders.map { it.folder.absolutePath }.toSet()
        _uiState.value = _uiState.value.copy(expandedFolders = allPaths)
    }

    fun collapseAllFolders() {
        _uiState.value = _uiState.value.copy(expandedFolders = emptySet())
    }

    fun clearStatus() {
        _uiState.value = _uiState.value.copy(statusMessage = null, error = null)
    }
}
