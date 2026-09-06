package com.qing.hachimi.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareableFileTest {

    @Test
    fun `share cache file name strips path separators and control chars`() {
        assertEquals("track.flac", shareCacheFileName("track.flac"))
        assertEquals("track.flac", shareCacheFileName("Music/Hachimi/track.flac"))
        assertEquals("track.flac", shareCacheFileName("C:\\Music\\track.flac"))
        assertEquals("file", shareCacheFileName(""))
        assertEquals("a_b.mp3", shareCacheFileName("a\u0000b.mp3"))
    }
}
