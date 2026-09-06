package com.qing.hachimi.util

import org.junit.Assert.assertEquals
import org.junit.Test

class StoragePermissionManagerTest {

    @Test
    fun `storage access summary covers granted writable and denied`() {
        assertEquals("已授予所有文件访问权限", storageAccessSummary(granted = true, writable = true))
        assertEquals("已授权但目录不可写，点击重试或更换下载目录", storageAccessSummary(granted = true, writable = false))
        assertEquals("未授予，下载将失败，点击去开启", storageAccessSummary(granted = false, writable = false))
        assertEquals("未授予，下载将失败，点击去开启", storageAccessSummary(granted = false, writable = true))
    }
}
