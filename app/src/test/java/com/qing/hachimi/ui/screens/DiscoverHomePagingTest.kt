package com.qing.hachimi.ui.screens

import com.qing.hachimi.data.repository.NeteaseRepository.Companion.coverDisplayUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class ListPaginationTest {

    @Test
    fun `long lists are split into ten item pages`() {
        val items = (1..23).toList()

        assertEquals(3, listPageCount(items.size))
        assertEquals((1..10).toList(), listPage(items, 0))
        assertEquals((11..20).toList(), listPage(items, 1))
        assertEquals((21..23).toList(), listPage(items, 2))
        assertEquals((21..23).toList(), listPage(items, 99))
    }

    @Test
    fun `cover urls request grid resolution instead of cached thumbnails`() {
        val original = "https://p1.music.126.net/cover.jpg?param=150y150"

        assertEquals(
            "https://p1.music.126.net/cover.jpg?param=640y640",
            coverDisplayUrl(original, CoverRequestSize.GRID),
        )
        assertEquals(
            "https://p1.music.126.net/cover.jpg?param=640y640",
            coverDisplayUrl("https://p1.music.126.net/cover.jpg"),
        )
    }
}
