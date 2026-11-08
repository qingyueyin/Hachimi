package com.qing.hachimi.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.qing.hachimi.data.model.SourceMode
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
            NavKey2.DiscoverCategory(SourceMode.PLAYLISTS),
        )

        syncNavStack(current, target)

        assertEquals(target, current.snapshot())
    }

    @Test
    fun `back removes only the last entry`() {
        val current = stackOf(
            NavKey2.DiscoverHome,
            NavKey2.DiscoverCategory(SourceMode.PLAYLISTS),
            NavKey2.DiscoverSongList(SourceMode.PLAYLISTS, "歌单"),
        )

        syncNavStack(current, listOf(NavKey2.DiscoverHome, NavKey2.DiscoverCategory(SourceMode.PLAYLISTS)))

        assertEquals(2, current.size)
        assertEquals(NavKey2.DiscoverHome, current[0])
        assertEquals(NavKey2.DiscoverCategory(SourceMode.PLAYLISTS), current[1])
    }

    @Test
    fun `switching sibling page replaces only the tail`() {
        val current = stackOf(
            NavKey2.DiscoverHome,
            NavKey2.DiscoverCategory(SourceMode.PLAYLISTS),
        )
        val target = listOf(
            NavKey2.DiscoverHome,
            NavKey2.DiscoverSongList(SourceMode.PLAYLISTS, "歌单"),
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
