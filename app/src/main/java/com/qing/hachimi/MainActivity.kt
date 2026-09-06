package com.qing.hachimi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qing.hachimi.data.local.SettingsManager
import com.qing.hachimi.downloader.DownloadEngine
import android.webkit.MimeTypeMap
import com.qing.hachimi.ui.screens.DiscoverViewModel
import com.qing.hachimi.ui.screens.SearchViewModel
import com.qing.hachimi.ui.screens.MyViewModel
import com.qing.hachimi.ui.screens.MainScreen
import com.qing.hachimi.ui.screens.MainViewModel
import com.qing.hachimi.ui.screens.UpgradeViewModel
import com.qing.hachimi.ui.theme.AppThemeSettings
import com.qing.hachimi.ui.theme.ColorMode
import com.qing.hachimi.ui.theme.HachimiTheme
import com.qing.hachimi.util.ShareableFile
import com.materialkolor.dynamiccolor.ColorSpec
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModel()
    private val discoverViewModel: DiscoverViewModel by viewModel()
    private val searchViewModel: SearchViewModel by viewModel()
    private val myViewModel: MyViewModel by viewModel()
    private val upgradeViewModel: UpgradeViewModel by viewModel()
    private val settingsManager: SettingsManager by inject()
    private val downloadEngine: DownloadEngine by inject()

    private val multiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.forEach { (permission, isGranted) ->
                when (permission) {
                    Manifest.permission.POST_NOTIFICATIONS -> {
                        if (!isGranted) {
                            Toast.makeText(
                                this,
                                "通知权限未授予，可能无法接收下载通知",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    Manifest.permission.READ_MEDIA_AUDIO -> {
                        if (!isGranted) {
                            Toast.makeText(
                                this,
                                "音频权限未授予，可能无法访问下载的音乐文件",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    Manifest.permission.WRITE_EXTERNAL_STORAGE -> {
                        if (!isGranted) {
                            Toast.makeText(
                                this,
                                "存储权限未授予，请授予所有文件访问权限后再下载",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }

    private val dirPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                val path = uriToPath(it)
                if (path != null) {
                    settingsManager.downloadDir = path
                    viewModel.updateDownloadDir(path)
                    Toast.makeText(this, "下载目录已更新", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this,
                        "该目录类型暂不支持，请选择内部存储或 SD 卡目录",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    private val musicFolderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val path = uriToPath(it)
                if (path != null) {
                    upgradeViewModel.addFolder(File(path))
                    Toast.makeText(this, "已添加文件夹: ${File(path).name}", Toast.LENGTH_SHORT).show()
                } else {
                    // 尝试使用URI路径
                    val file = File(it.path ?: "")
                    if (file.exists()) {
                        upgradeViewModel.addFolder(file)
                        Toast.makeText(this, "已添加文件夹: ${file.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "无法访问该文件夹", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )

        requestPermissionsIfNeeded()

        // Handle deep link from music.163.com
        handleDeepLink(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val themeSettings = AppThemeSettings(
                colorMode = ColorMode.fromValue(uiState.colorMode),
                keyColor = settingsManager.themeKeyColor,
                paletteStyle = runCatching {
                    com.materialkolor.PaletteStyle.valueOf(settingsManager.themePaletteStyle)
                }.getOrDefault(com.materialkolor.PaletteStyle.TonalSpot),
                colorSpec = runCatching {
                    ColorSpec.SpecVersion.valueOf(settingsManager.themeColorSpec)
                }.getOrDefault(ColorSpec.SpecVersion.SPEC_2025),
            )
            HachimiTheme(settings = themeSettings) {
                MainScreen(
                    discoverViewModel = discoverViewModel,
                    searchViewModel = searchViewModel,
                    myViewModel = myViewModel,
                    upgradeViewModel = upgradeViewModel,
                    viewModel = viewModel,
                    settingsManager = settingsManager,
                    onPickDownloadDir = { dirPickerLauncher.launch(null) },
                    onPickMusicFolder = { musicFolderPickerLauncher.launch(null) },
                    onOpenDownloadedFile = { songId ->
                        openDownloadedFile(songId)
                    }
                )
            }
        }
    }

    /**
     * Handle deep links from music.163.com (e.g., playlist, album, song links).
     */
    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        val url = uri.toString()
        if (!url.contains("music.163.com")) return

        searchViewModel.updatePlaylistUrl(url)
        searchViewModel.parseUrl()
        viewModel.requestSearchTab()
        Toast.makeText(this, "已识别链接，正在解析…", Toast.LENGTH_SHORT).show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        // Status bar tap sends KEYCODE_PAGE_UP on Android 23+
        if (keyCode == android.view.KeyEvent.KEYCODE_PAGE_UP) {
            viewModel.scrollToTop()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Convert a content:// URI to an absolute file path.
     * Works with primary external storage paths.
     */
    private fun uriToPath(uri: Uri): String? {
        val docId = try { DocumentsContract.getTreeDocumentId(uri) } catch (_: Exception) { null }
            ?: return null

        // Android's primary storage is typically /storage/emulated/0
        // The docId for primary is like "primary:Music/Hachimi"
        if (docId.startsWith("primary:")) {
            val relativePath = docId.removePrefix("primary:")
            return File(
                Environment.getExternalStorageDirectory(),
                relativePath
            ).absolutePath
        }

        // For raw paths like /storage/XXXX-XXXX/path
        val split = docId.split(":", limit = 2)
        if (split.size == 2) {
            val volume = split[0]
            return "/storage/$volume/${split[1]}"
        }

        return null
    }

    private fun requestPermissionsIfNeeded() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
        }
        if (permissionsToRequest.isNotEmpty()) {
            multiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    /**
     * Open a downloaded audio file using the system file picker/chooser.
     * This allows the user to play or open the file with any compatible app.
     */
    private fun openDownloadedFile(songId: Long) {
        val file = downloadEngine.getCompletedFilePath(songId)
        if (file == null || !file.exists()) {
            Toast.makeText(this, "文件未找到，可能已被删除或移动", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = ShareableFile.contentUri(this, file)
        if (uri == null) {
            Toast.makeText(this, "无法打开该路径的文件，请用文件管理器查看", Toast.LENGTH_LONG).show()
            return
        }

        val mimeType = getMimeTypeForFile(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(Intent.createChooser(intent, "打开文件"))
        } catch (e: Exception) {
            Toast.makeText(this, "没有可用的应用来打开此文件", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeTypeForFile(file: File): String {
        val extension = file.extension.lowercase()
        return when (extension) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "aac" -> "audio/aac"
            "lrc" -> "text/plain"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        }
    }
}
