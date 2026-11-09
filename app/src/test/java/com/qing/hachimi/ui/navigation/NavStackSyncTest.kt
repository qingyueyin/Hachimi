package com.qing.hachimi.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.qing.hachimi.ui.screens.DiscoverLevel
import com.qing.hachimi.ui.screens.DiscoverStackEntry
import com.qing.hachimi.ui.screens.toNavKey
import org.junit.Assert.assertEquals
import org.junit.Test
import top.yukonga.miuix.kmp.nav.core.NavKey

class NavStackSyncTest {

    private fun stackOf(vararg keys: NavKey): SnapshotStateList<NavKey> =
        mutableStateListOf(*keys)

    private fun SnapshotStateList<NavKey>.snapshot(): List<NavKey> = toList()

    @Test
    fun `pushing the same key twice does not duplicate`() {
        // 连点两下歌单卡片会连着入栈两次同一个 key，NavDisplay 会直接抛异常崩掉
        val stack = stackOf(NavKey2.MyHome)
        val detail = NavKey2.MyPlaylistDetail(1L, "某歌单")

        assertEquals(true, pushNavKey(stack, detail))
        assertEquals(false, pushNavKey(stack, detail))

        assertEquals(listOf<NavKey>(NavKey2.MyHome, detail), stack.snapshot())
    }

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
            NavKey2.DiscoverSongList(2),
        )

        syncNavStack(current, listOf(NavKey2.DiscoverHome, NavKey2.DiscoverCategory))

        assertEquals(2, current.size)
        assertEquals(NavKey2.DiscoverHome, current[0])
        assertEquals(NavKey2.DiscoverCategory, current[1])
    }

    @Test
    fun `category layer key does not depend on source mode`() {
        // 分类层是 data object，页面内容由 state 决定：歌手详情把 sourceMode
        // 改成 ARTIST_DETAIL 时，分类层的 key 必须保持不变，否则分类页会被重建
        assertEquals(
            DiscoverStackEntry(DiscoverLevel.CATEGORY, 1).toNavKey(),
            DiscoverStackEntry(DiscoverLevel.CATEGORY, 2).toNavKey(),
        )
    }

    @Test
    fun `tab root keeps its key after a detail page overwrites source mode`() {
        // 首页同样不随内容变 key，返回时不会被重建
        assertEquals(
            DiscoverStackEntry(DiscoverLevel.HOME, 1).toNavKey(),
            DiscoverStackEntry(DiscoverLevel.HOME, 2).toNavKey(),
        )
    }

    @Test
    fun `switching sibling page replaces only the tail`() {
        val current = stackOf(
            NavKey2.DiscoverHome,
            NavKey2.DiscoverCategory,
        )
        val target = listOf(
            NavKey2.DiscoverHome,
            NavKey2.DiscoverSongList(1),
        )

        syncNavStack(current, target)

        assertEquals(target, current.snapshot())
    }

    @Test
    fun `detail layer key stays stable while its data loads`() {
        // 编号只在入栈时分配，歌单/专辑数据加载前后 key 不变，页面不会被重建
        val entry = DiscoverStackEntry(DiscoverLevel.DETAIL, 7)

        assertEquals(entry.toNavKey(), entry.toNavKey())
        assertEquals(
            NavKey2.DiscoverSongList(7),
            DiscoverStackEntry(DiscoverLevel.DETAIL, 7).toNavKey(),
        )
    }

    @Test
    fun `nested detail pages never share a content key`() {
        // 歌手页里点专辑会连着压两层详情。NavDisplay 按 contentKey 判重，
        // 两个详情 entry 的 key 相同会直接抛 Duplicate contentKey 崩掉。
        // 编号每条入栈 +1，哪怕是同一个歌手、同名专辑，也不会撞 key
        val stack = listOf(
            DiscoverStackEntry(DiscoverLevel.HOME, 1),
            DiscoverStackEntry(DiscoverLevel.CATEGORY, 2),
            DiscoverStackEntry(DiscoverLevel.DETAIL, 3),
            DiscoverStackEntry(DiscoverLevel.DETAIL, 4),
        ).map { it.toNavKey() }

        assertEquals(stack.size, stack.distinct().size)
    }

    @Test
    fun `nested detail pages stay in sync when the inner page changes`() {
        // 歌手 → 专辑：只替换最后一层（新专辑替换旧专辑），不能因为 key 相同就漏掉更新
        val current = stackOf(
            DiscoverStackEntry(DiscoverLevel.HOME, 1).toNavKey(),
            DiscoverStackEntry(DiscoverLevel.DETAIL, 2).toNavKey(),
            DiscoverStackEntry(DiscoverLevel.DETAIL, 3).toNavKey(),
        )
        val target = listOf(
            DiscoverStackEntry(DiscoverLevel.HOME, 1).toNavKey(),
            DiscoverStackEntry(DiscoverLevel.DETAIL, 2).toNavKey(),
        )

        syncNavStack(current, target)

        assertEquals(target, current.snapshot())
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
