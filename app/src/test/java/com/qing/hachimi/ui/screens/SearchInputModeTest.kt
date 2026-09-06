package com.qing.hachimi.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchInputModeTest {

    @Test
    fun `input modes use concrete action labels and contextual placeholders`() {
        assertEquals("查找音乐", InputMode.SEARCH.label)
        assertEquals("搜索歌曲、歌手", InputMode.SEARCH.placeholder)
        assertEquals("导入链接", InputMode.URL.label)
        assertEquals("粘贴网易云链接", InputMode.URL.placeholder)
    }
}
