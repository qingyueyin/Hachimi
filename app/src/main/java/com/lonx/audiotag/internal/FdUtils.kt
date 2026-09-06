package com.lonx.audiotag.internal

import android.os.ParcelFileDescriptor

internal object FdUtils {
    fun getNativeFd(pfd: ParcelFileDescriptor): Int {
        return pfd.dup().detachFd()
    }
}
