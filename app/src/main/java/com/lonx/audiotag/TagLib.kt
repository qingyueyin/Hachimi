package com.lonx.audiotag

import com.lonx.audiotag.model.Metadata
import com.lonx.audiotag.model.Picture
import com.lonx.audiotag.model.PropertyMap

public object TagLib {

    init {
        System.loadLibrary("lyrico_taglib")
    }

    @JvmStatic
    public external fun getMetadata(
        fd: Int,
        readPictures: Boolean = true,
    ): Metadata?

    @JvmStatic
    public external fun getMetadataPropertyValues(
        fd: Int,
        propertyName: String,
    ): Array<String>?

    @JvmStatic
    public external fun getPictures(fd: Int): Array<Picture>

    @JvmStatic
    public fun getFrontCover(fd: Int): Picture? {
        val pictures = getPictures(fd)
        return pictures.find { picture -> picture.pictureType == "Front Cover" }
            ?: pictures.firstOrNull()
    }

    @JvmStatic
    public external fun savePropertyMap(
        fd: Int,
        propertyMap: PropertyMap,
    ): Boolean

    @JvmStatic
    public external fun savePictures(
        fd: Int,
        pictures: Array<Picture>,
    ): Boolean
}
