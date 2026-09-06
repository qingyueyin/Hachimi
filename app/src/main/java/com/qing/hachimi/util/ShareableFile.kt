package com.qing.hachimi.util

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * Builds a content:// URI for sharing a local file without exposing the
 * entire external storage tree through FileProvider.
 */
object ShareableFile {
    private const val MAX_CACHE_COPY_BYTES = 80L * 1024 * 1024

    fun contentUri(context: Context, file: File): Uri? {
        val authority = "${context.packageName}.fileprovider"
        runCatching { FileProvider.getUriForFile(context, authority, file) }
            .getOrNull()
            ?.let { return it }

        mediaStoreAudioUri(context, file)?.let { return it }

        return copyToCacheUri(context, file, authority)
    }

    @Suppress("DEPRECATION")
    private fun mediaStoreAudioUri(context: Context, file: File): Uri? {
        val path = file.absolutePath
        if (path.isBlank()) return null
        return try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.MediaColumns.DATA}=?",
                arrayOf(path),
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val id = cursor.getLong(0)
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            }
        } catch (e: Exception) {
            AppLogger.warn("[Share] MediaStore lookup failed for ${file.name}", e)
            null
        }
    }

    private fun copyToCacheUri(context: Context, file: File, authority: String): Uri? {
        if (!file.isFile || file.length() <= 0L || file.length() > MAX_CACHE_COPY_BYTES) return null
        return try {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val dest = File(dir, shareCacheFileName(file.name))
            file.copyTo(dest, overwrite = true)
            FileProvider.getUriForFile(context, authority, dest)
        } catch (e: Exception) {
            AppLogger.warn("[Share] cache copy failed for ${file.name}", e)
            null
        }
    }
}

internal fun shareCacheFileName(original: String): String {
    val base = original
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .replace(Regex("[\\x00-\\x1f]"), "_")
        .ifBlank { "file" }
    return base.take(180)
}
