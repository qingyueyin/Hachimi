package com.qing.hachimi.data.local

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import java.io.File

enum class NamingFormat(val label: String) {
    SONG_ARTIST("歌曲名 - 艺术家"),
    ARTIST_SONG("艺术家 - 歌曲名"),
    SONG_ARTIST_QUALITY("歌曲名 - 艺术家 [音质]"),
    ARTIST_SONG_QUALITY("艺术家 - 歌曲名 [音质]")
}

enum class FolderNamingFormat(val label: String) {
    NONE("不使用"),
    PLAYLIST_NAME("按歌单名称"),
    ARTIST_NAME("按歌手名称"),
    ALBUM_NAME("按专辑名称"),
    ARTIST_ALBUM("歌手 - 专辑"),
}

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("hachimi_settings", Context.MODE_PRIVATE)

    // Default download directory: /storage/emulated/0/Hachimi/
    // Public storage writes require MANAGE_EXTERNAL_STORAGE on Android 11+.
    val defaultDownloadDir: String
        get() = "${Environment.getExternalStorageDirectory().absolutePath}/Hachimi"

    var quality: String
        get() = prefs.getString("quality", "exhigh") ?: "exhigh"
        set(value) = prefs.edit().putString("quality", value).apply()

    var downloadDir: String
        get() = prefs.getString("download_dir", "") ?: ""
        set(value) = prefs.edit().putString("download_dir", value).apply()

    /** 免责声明是否已确认（首次启动弹窗用） */
    var disclaimerAccepted: Boolean
        get() = prefs.getBoolean("disclaimer_accepted", false)
        set(value) = prefs.edit().putBoolean("disclaimer_accepted", value).apply()

    var unofficialBuildWarningDismissed: Boolean
        get() = prefs.getBoolean("unofficial_build_warning_dismissed", false)
        set(value) = prefs.edit().putBoolean("unofficial_build_warning_dismissed", value).apply()

    fun getEffectiveDownloadDir(): File {
        val dir = downloadDir
        val effectiveDir = if (dir.isNotBlank()) File(dir) else File(defaultDownloadDir)
        // Try to create directory if it doesn't exist
        if (!effectiveDir.exists()) {
            effectiveDir.mkdirs()
        }
        // Validate directory is writable
        return if (effectiveDir.canWrite()) {
            effectiveDir
        } else {
            // Fallback to default
            val fallback = File(defaultDownloadDir)
            fallback.mkdirs()
            fallback
        }
    }

    var namingFormat: NamingFormat
        get() {
            val name = prefs.getString("naming_format", NamingFormat.SONG_ARTIST.name)
                ?: NamingFormat.SONG_ARTIST.name
            return try { NamingFormat.valueOf(name) } catch (_: Exception) { NamingFormat.SONG_ARTIST }
        }
        set(value) = prefs.edit().putString("naming_format", value.name).apply()

    var concurrentDownloads: Int
        get() = prefs.getInt("concurrent_downloads", 3)
        set(value) = prefs.edit().putInt("concurrent_downloads", value.coerceIn(1, 5)).apply()

    var fuckAiMode: Boolean
        get() = prefs.getBoolean("fuck_ai_mode", false)
        set(value) = prefs.edit().putBoolean("fuck_ai_mode", value).apply()

    var downloadLyrics: Boolean
        get() = prefs.getBoolean("download_lyrics", true)
        set(value) = prefs.edit().putBoolean("download_lyrics", value).apply()

    // --- Playlist Folder ---
    var folderNamingFormat: FolderNamingFormat
        get() {
            val name = prefs.getString("folder_naming_format", FolderNamingFormat.NONE.name)
                ?: FolderNamingFormat.NONE.name
            return try { FolderNamingFormat.valueOf(name) } catch (_: Exception) { FolderNamingFormat.NONE }
        }
        set(value) = prefs.edit().putString("folder_naming_format", value.name).apply()

    // Legacy: kept for backward compatibility with old migrations
    var createPlaylistFolder: Boolean
        get() = folderNamingFormat != FolderNamingFormat.NONE
        set(value) = prefs.edit().putString("folder_naming_format", if (value) FolderNamingFormat.PLAYLIST_NAME.name else FolderNamingFormat.NONE.name).apply()

    var enableWriteTags: Boolean
        get() = prefs.getBoolean("enable_write_tags", false)
        set(value) = prefs.edit().putBoolean("enable_write_tags", value).apply()

    var embedLyrics: Boolean
        get() = prefs.getBoolean("embed_lyrics", true)
        set(value) = prefs.edit().putBoolean("embed_lyrics", value).apply()

    var saveTlLrc: Boolean
        get() = prefs.getBoolean("save_tl_lrc", false)
        set(value) = prefs.edit().putBoolean("save_tl_lrc", value).apply()

    var saveRomaLrc: Boolean
        get() = prefs.getBoolean("save_roma_lrc", false)
        set(value) = prefs.edit().putBoolean("save_roma_lrc", value).apply()

    var saveYrc: Boolean
        get() = prefs.getBoolean("save_yrc", false)
        set(value) = prefs.edit().putBoolean("save_yrc", value).apply()

    var customNamingTemplate: String
        get() = prefs.getString("custom_naming_template", "") ?: ""
        set(value) = prefs.edit().putString("custom_naming_template", value).apply()

    var artistDelimiter: String
        get() = prefs.getString("artist_delimiter", "/") ?: "/"
        set(value) = prefs.edit().putString("artist_delimiter", value.replace("\\", "/")).apply()

    // --- Album Grouping ---
    var albumGrouping: Boolean
        get() = prefs.getBoolean("album_grouping", false)
        set(value) = prefs.edit().putBoolean("album_grouping", value).apply()

    // --- Search History ---
    var searchHistory: List<String>
        get() = prefs.getString("search_history", "")
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.take(20)
            ?: emptyList()
        set(value) = prefs.edit().putString("search_history", value.take(20).joinToString("\n")).apply()

    // --- Grid Columns (per page) ---
    private fun gridColumnsKey(page: String) = "grid_columns_$page"

    var gridColumns: Int
        get() = getGridColumns(GridPage.DEFAULT)
        set(value) = setGridColumns(GridPage.DEFAULT, value)

    fun getGridColumns(page: String): Int =
        prefs.getInt(gridColumnsKey(page), 2).coerceIn(1, 4)

    fun setGridColumns(page: String, value: Int) =
        prefs.edit().putInt(gridColumnsKey(page), value.coerceIn(1, 4)).apply()

    object GridPage {
        const val DEFAULT = "default"
        const val PLAYLISTS = "playlists"
        const val PODCASTS = "podcasts"
        const val NEW_ALBUMS = "new_albums"
    }

    var themeColorMode: Int
        get() = prefs.getInt("theme_color_mode", 5)
        set(value) = prefs.edit().putInt("theme_color_mode", value).apply()

    var themeKeyColor: Int
        get() = prefs.getInt("theme_key_color", 0)
        set(value) = prefs.edit().putInt("theme_key_color", value).apply()

    var themePaletteStyle: String
        get() = prefs.getString("theme_palette_style", "TonalSpot") ?: "TonalSpot"
        set(value) = prefs.edit().putString("theme_palette_style", value).apply()

    var themeColorSpec: String
        get() = prefs.getString("theme_color_spec", "SPEC_2025") ?: "SPEC_2025"
        set(value) = prefs.edit().putString("theme_color_spec", value).apply()

    var blurEffect: Boolean
        get() = prefs.getBoolean("blur_effect", true)
        set(value) = prefs.edit().putBoolean("blur_effect", value).apply()

    var liquidGlass: Boolean
        get() = prefs.getBoolean("liquid_glass", false)
        set(value) = prefs.edit().putBoolean("liquid_glass", value).apply()

    fun getFilteredQualityKeys(): List<String> {
        val aiQualities = setOf("jymaster", "sky", "jyeffect")
        return com.qing.hachimi.data.api.NeteaseApi.QUALITY_MAP.keys
            .filter { !fuckAiMode || it !in aiQualities }
    }

    fun buildFileName(songName: String, artists: String, qualityLabel: String, album: String = ""): String {
        val template = customNamingTemplate
        if (template.isNotBlank()) {
            val result = template
                .replace("\${name}", songName)
                .replace("\${artists}", artists)
                .replace("\${album}", album)
                .replace("\${quality}", if (qualityLabel.isNotBlank() && qualityLabel != "标准") "[$qualityLabel]" else "")
            return sanitize(result)
        }
        val fmt = namingFormat
        val base = when (fmt) {
            NamingFormat.SONG_ARTIST -> "$songName - $artists"
            NamingFormat.ARTIST_SONG -> "$artists - $songName"
            NamingFormat.SONG_ARTIST_QUALITY -> "$songName - $artists [$qualityLabel]"
            NamingFormat.ARTIST_SONG_QUALITY -> "$artists - $songName [$qualityLabel]"
        }
        return sanitize(base)
    }

    companion object {
        fun sanitize(name: String): String {
            return name.replace(Regex("""[<>:"/\\|?*]"""), "_")
                .trim(' ', '.')
                .take(200)
                .ifEmpty { "unknown" }
        }
    }
}
