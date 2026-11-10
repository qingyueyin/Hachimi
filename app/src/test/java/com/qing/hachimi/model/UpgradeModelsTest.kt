package com.qing.hachimi.model

import com.qing.hachimi.data.model.AudioQualityInfo
import com.qing.hachimi.data.model.ScannedFolder
import com.qing.hachimi.data.model.SongInfo
import com.qing.hachimi.data.model.UpgradedSong
import com.qing.hachimi.data.model.applyUpgrades
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class UpgradeModelsTest {

    private fun quality(format: String, bitrate: Int) = AudioQualityInfo(
        format = format,
        bitrate = bitrate,
        sampleRate = 44_100,
        bitDepth = 16,
        encoder = "test",
        fileSize = 1,
        duration = 1,
    )

    private fun song(file: File, format: String, bitrate: Int) = SongInfo(
        id = 0,
        name = "Song",
        artists = "Artist",
        album = "Album",
        localFile = file,
        qualityInfo = quality(format, bitrate),
    )

    @Test
    fun `upgraded song replaces the row and refreshes quality`() {
        val oldFile = File("D:/Music/Song - Artist.mp3")
        val newFile = File("D:/Music/Song - Artist.flac")
        val folder = ScannedFolder(
            folder = File("D:/Music"),
            songs = listOf(song(oldFile, "mp3", 128)),
            upgradeableCount = 1,
        )

        val (folders, selected) = listOf(folder).applyUpgrades(
            upgrades = listOf(UpgradedSong(song(oldFile, "mp3", 128), newFile, quality("flac", 1000))),
            selected = setOf(oldFile.absolutePath),
        )

        val updated = folders.single().songs.single()
        assertEquals(newFile, updated.localFile)
        assertEquals("flac", updated.qualityInfo.format)
        assertEquals(1000, updated.qualityInfo.bitrate)
        // 升级过的歌不再计入可升级数
        assertEquals(0, folders.single().upgradeableCount)
        // 选中集合跟着新文件走，否则升级后勾选状态会丢
        assertEquals(setOf(newFile.absolutePath), selected)
    }

    @Test
    fun `upgrade keeps the title even when the new file has a quality suffix`() {
        val oldFile = File("D:/Music/Song - Artist.mp3")
        // 保留原文件模式生成的新文件带音质后缀
        val newFile = File("D:/Music/Song - Artist [lossless].flac")
        val folder = ScannedFolder(
            folder = File("D:/Music"),
            songs = listOf(song(oldFile, "mp3", 128)),
            upgradeableCount = 1,
        )

        val (folders, _) = listOf(folder).applyUpgrades(
            upgrades = listOf(UpgradedSong(song(oldFile, "mp3", 128), newFile, quality("flac", 1000))),
            selected = emptySet(),
        )

        val updated = folders.single().songs.single()
        // 歌名和专辑不能被新文件名带歪，否则再点检查会拿怪名字去搜
        assertEquals("Song", updated.name)
        assertEquals("Album", updated.album)
        assertEquals(newFile, updated.localFile)
    }

    @Test
    fun `songs not upgraded keep their original rows`() {        val upgradedOld = File("D:/Music/A - Artist.mp3")
        val upgradedNew = File("D:/Music/A - Artist.flac")
        val untouched = File("D:/Music/B - Artist.mp3")
        val folder = ScannedFolder(
            folder = File("D:/Music"),
            songs = listOf(song(upgradedOld, "mp3", 128), song(untouched, "mp3", 320)),
            upgradeableCount = 2,
        )

        val (folders, _) = listOf(folder).applyUpgrades(
            upgrades = listOf(UpgradedSong(song(upgradedOld, "mp3", 128), upgradedNew, quality("flac", 1000))),
            selected = emptySet(),
        )

        val songs = folders.single().songs
        assertEquals(2, songs.size)
        assertEquals(upgradedNew, songs[0].localFile)
        assertEquals(untouched, songs[1].localFile)
        assertEquals(320, songs[1].qualityInfo.bitrate)
    }
}
