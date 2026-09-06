package com.lonx.audiotag.rw

import android.os.ParcelFileDescriptor
import com.lonx.audiotag.TagLib
import com.lonx.audiotag.internal.FdUtils
import com.lonx.audiotag.model.Picture

object AudioTagWriter {

    fun writeTags(
        pfd: ParcelFileDescriptor,
        updates: Map<String, String>,
        preserveOldTags: Boolean = true
    ): Boolean {
        return try {
            val fd = FdUtils.getNativeFd(pfd)
            val mapToSave = HashMap<String, Array<String>>()

            if (preserveOldTags) {
                val oldFd = FdUtils.getNativeFd(pfd)
                val oldMeta = TagLib.getMetadata(oldFd, false)
                if (oldMeta != null) {
                    mapToSave.putAll(oldMeta.propertyMap)
                }
            }

            for ((k, v) in updates) {
                mapToSave[k] = arrayOf(v)
            }

            TagLib.savePropertyMap(fd, mapToSave)
        } catch (e: Exception) {
            false
        }
    }

    fun writePictures(pfd: ParcelFileDescriptor, pictures: List<Picture>): Boolean {
        return try {
            val fd = FdUtils.getNativeFd(pfd)
            val arr = pictures.toTypedArray()
            TagLib.savePictures(fd, arr)
        } catch (e: Exception) {
            false
        }
    }
}
