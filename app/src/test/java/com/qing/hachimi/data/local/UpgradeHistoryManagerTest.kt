package com.qing.hachimi.data.local

import android.content.Context
import android.content.SharedPreferences
import com.qing.hachimi.data.model.UpgradeStatus
import org.junit.Test
import org.junit.Assert.*
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*
import java.io.File

class UpgradeHistoryManagerTest {

    @Test
    fun `test addRecord and getRecords`() {
        val mockContext = mock(Context::class.java)
        val mockPrefs = mock(SharedPreferences::class.java)
        val mockEditor = mock(SharedPreferences.Editor::class.java)

        `when`(mockContext.getSharedPreferences("upgrade_history", Context.MODE_PRIVATE))
            .thenReturn(mockPrefs)
        `when`(mockPrefs.getString("records", "[]")).thenReturn("[]")
        `when`(mockPrefs.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(mockEditor)

        val manager = UpgradeHistoryManager(mockContext)

        val record = UpgradeHistoryManager.UpgradeRecord(
            originalFile = File("/path/to/original.mp3"),
            upgradedFile = File("/path/to/upgraded.flac"),
            originalQuality = "mp3 128kbps",
            upgradedQuality = "flac 1000kbps",
            timestamp = System.currentTimeMillis(),
            status = UpgradeStatus.COMPLETED
        )

        manager.addRecord(record)

        verify(mockEditor).putString(ArgumentMatchers.any(), ArgumentMatchers.any())
    }

    @Test
    fun `test isUpgraded returns true for completed upgrade`() {
        val mockContext = mock(Context::class.java)
        val mockPrefs = mock(SharedPreferences::class.java)
        val mockEditor = mock(SharedPreferences.Editor::class.java)

        `when`(mockContext.getSharedPreferences("upgrade_history", Context.MODE_PRIVATE))
            .thenReturn(mockPrefs)
        `when`(mockPrefs.getString("records", "[]")).thenReturn("[]")
        `when`(mockPrefs.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(mockEditor)

        val manager = spy(UpgradeHistoryManager(mockContext))

        val record = UpgradeHistoryManager.UpgradeRecord(
            originalFile = File("/path/to/original.mp3"),
            upgradedFile = File("/path/to/upgraded.flac"),
            originalQuality = "mp3 128kbps",
            upgradedQuality = "flac 1000kbps",
            timestamp = 1234567890,
            status = UpgradeStatus.COMPLETED
        )

        doReturn(listOf(record)).`when`(manager).getRecords()

        assertTrue(manager.isUpgraded(File("/path/to/original.mp3")))
    }

    @Test
    fun `test isUpgraded returns false for non-upgraded file`() {
        val mockContext = mock(Context::class.java)
        val mockPrefs = mock(SharedPreferences::class.java)
        val mockEditor = mock(SharedPreferences.Editor::class.java)

        `when`(mockContext.getSharedPreferences("upgrade_history", Context.MODE_PRIVATE))
            .thenReturn(mockPrefs)
        `when`(mockPrefs.getString("records", "[]")).thenReturn("[]")
        `when`(mockPrefs.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(mockEditor)

        val manager = spy(UpgradeHistoryManager(mockContext))

        doReturn(emptyList<UpgradeHistoryManager.UpgradeRecord>()).`when`(manager).getRecords()

        assertFalse(manager.isUpgraded(File("/path/to/non-upgraded.mp3")))
    }
}
