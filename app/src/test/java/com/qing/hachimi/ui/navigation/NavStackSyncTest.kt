package com.qing.hachimi.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.qing.hachimi.data.model.SourceMode
import com.qing.hachimi.ui.screens.DiscoverLevel
import com.qing.hachimi.ui.screens.DiscoverUiState
import com.qing.hachimi.ui.screens.toNavKey
import org.junit.Assert.assertEquals
import org.junit.Test
import top.yukonga.miuix.kmp.nav.core.NavKey

class NavStackSyncTest {

    private fun stackOf(vararg keys: NavKey): SnapshotStateList<NavKey> =
        mutableStateListOf(*keys)

    private fun SnapshotStateList<NavKey>.snapshot(): List<NavKey> = toList()

    @Test
    fun `push appends to the existing stack`() {
        val current = stackOf(NavKey2.DiscoverHome)
        val target = listOf(
            NavKey2.DiscoverHome,
            NavKey2.DiscoverCategory,
        )

        syncNavStack(current, target)

        assertEquals(target, current.snapshot())
    }

    @Test
    fun `back removes only the last entry`() {
        val current = stackOf(
            NavKey2.DiscoverHome,
            NavKey2.DiscoverCategory,
            NavKey2.DiscoverSongList,
        )

        syncNavStack(current, listOf(NavKey2.DiscoverHome, NavKey2.DiscoverCategory))

        assertEquals(2, current.size)
        assertEquals(NavKey2.DiscoverHome, current[0])
        assertEquals(NavKey2.DiscoverCategory, current[1])
    }

    @Test
    fun `category layer key does not depend on source mode`() {
        // 歌手详情会把 sourceMode 改成 ARTIST_DETAIL，分类层 key 必须保持不变，
        // 否则 NavDisplay 会把分类页当成新页面重建，重建瞬间列表为空就显示「暂时没有内容」
        val inArtistCategory = DiscoverUiState(sourceMode = SourceMode.ARTISTS)
        val inArtistDetail = inArtistCategory.copy(sourceMode = SourceMode.ARTIST_DETAIL)

        assertEquals(
            DiscoverLevel.CATEGORY.toNavKey(inArtistCategory),
            DiscoverLevel.CATEGORY.toNavKey(inArtistDetail),
        )
    }

    @Test
    fun `tab root keeps its key after a detail page overwrites source mode`() {
        // 详情页会把 sourceMode 改成详情自己的值，首页 key 不能跟着变，
        // 否则返回时首页被重建，动画过程中会露出「暂时没有内容」
        val root = DiscoverLevel.HOME.toNavKey(DiscoverUiState())
        listOf(SourceMode.RECOMMEND, SourceMode.ARTIST_DETAIL, SourceMode.SEARCH).forEach { mode ->
            assertEquals(root, DiscoverLevel.HOME.toNavKey(DiscoverUiState(sourceMode = mode)))
        }
    }

    @Test
    fun `switching sibling page replaces only the tail`() {
        val current = stackOf(
            NavKey2.DiscoverHome,
            NavKey2.DiscoverCategory,
        )
        val target = listOf(
            NavKey2.DiscoverHome,
            NavKey2.DiscoverSongList,
        )

        syncNavStack(current, target)

        assertEquals(target, current.snapshot())
    }

    @Test
    fun `detail layer key does not depend on which collection is open`() {
        // 详情页 key 带歌单/专辑/歌手数据的话，换一首歌就会变成新页面被重建，
        // 动画过程中同样会露出「暂时没有内容」
        val first = DiscoverUiState(playlistName = "歌单 A")
        val second = DiscoverUiState(playlistName = "歌单 B", sourceMode = SourceMode.CHARTS)

        assertEquals(
            DiscoverLevel.DETAIL.toNavKey(first),
            DiscoverLevel.DETAIL.toNavKey(second),
        )
    }

    @Test
    fun `already in sync does not change the stack`() {
        val current = stackOf(NavKey2.MyHome, NavKey2.MyPlaylistDetail(1L, "收藏"))
        val before = current.snapshot()

        syncNavStack(current, before)

        assertEquals(before, current.snapshot())
    }

    @Test
    fun `empty stack is filled from the target`() {
        val current = mutableStateListOf<NavKey>()

        syncNavStack(current, listOf(NavKey2.SearchHome))

        assertEquals(listOf<NavKey>(NavKey2.SearchHome), current.snapshot())
    }
}
