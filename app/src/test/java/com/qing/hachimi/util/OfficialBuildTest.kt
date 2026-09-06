package com.qing.hachimi.util

import org.junit.Assert.assertEquals
import org.junit.Test

class OfficialBuildTest {

    @Test
    fun `sha256 hex of empty input matches known vector`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(ByteArray(0)),
        )
    }

    @Test
    fun `channel labels stay short for settings summary`() {
        assertEquals("官方", OfficialBuild.channelLabel(BuildChannel.OFFICIAL))
        assertEquals("非官方构建", OfficialBuild.channelLabel(BuildChannel.UNOFFICIAL))
        assertEquals("Debug", OfficialBuild.channelLabel(BuildChannel.DEBUG))
        assertEquals("未登记签名", OfficialBuild.channelLabel(BuildChannel.UNPINNED))
    }

    @Test
    fun `official repo is the Hachimi GitHub URL`() {
        assertEquals("https://github.com/qingyueyin/Hachimi", OfficialBuild.REPO_URL)
    }
}
