package com.qing.hachimi.model

import com.qing.hachimi.ui.navigation.NavKey2
import com.qing.hachimi.ui.screens.MySection
import com.qing.hachimi.ui.screens.syncMyNavStack
import top.yukonga.miuix.kmp.nav.core.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「我的」页返回栈与 ViewModel 层级的同步规则。
 * 这些规则直接决定顶栏返回是否出现、以及返回后停在哪一层，所以单独测。
 */
class MyNavStackSyncTest {

    private fun initialState(): MutableList<NavKey> = mutableListOf(NavKey2.MyHome)

    private fun sync(
        stack: MutableList<NavKey>,
        section: MySection,
        detail: Boolean = false,
        playlistId: Long = 0L,
        playlistName: String = "",
    ) = syncMyNavStack(stack, section, detail, playlistId, playlistName)

    @Test
    fun `detail opens when view model enters playlist detail`() {
        val stack = initialState()

        assertTrue(sync(stack, MySection.PLAYLISTS))
        assertEquals(2, stack.size)

        sync(stack, MySection.PLAYLISTS, detail = true, playlistId = 42L, playlistName = "每日推荐")
        assertEquals(3, stack.size)
        val detail = stack.last() as NavKey2.MyPlaylistDetail
        assertEquals(42L, detail.playlistId)
        assertEquals("每日推荐", detail.name)
    }

    @Test
    fun `falling back to list pops the detail layer`() {
        val stack = initialState()
        sync(stack, MySection.COLLECTED_ALBUMS, detail = true, playlistId = 7L, playlistName = "专辑")

        assertTrue(sync(stack, MySection.COLLECTED_ALBUMS))
        assertEquals(2, stack.size)
        assertEquals(NavKey2.MySectionPage(MySection.COLLECTED_ALBUMS), stack.last())
    }

    @Test
    fun `returning home clears every layer above home`() {
        val stack = initialState()
        sync(stack, MySection.PLAYLISTS, detail = true, playlistId = 1L, playlistName = "歌单")

        assertTrue(sync(stack, MySection.HOME))
        assertEquals(listOf<NavKey>(NavKey2.MyHome), stack)
    }

    @Test
    fun `switching section replaces the page instead of stacking`() {
        val stack = initialState()
        sync(stack, MySection.PLAYLISTS)

        sync(stack, MySection.LIKED)

        assertEquals(2, stack.size)
        assertEquals(NavKey2.MySectionPage(MySection.LIKED), stack.last())
    }

    @Test
    fun `stable state does not touch the stack`() {
        val stack = initialState()
        sync(stack, MySection.PLAYLISTS, detail = true, playlistId = 3L, playlistName = "歌单")
        val snapshot = stack.toList()

        assertFalse(sync(stack, MySection.PLAYLISTS, detail = true, playlistId = 3L, playlistName = "歌单"))
        assertEquals(snapshot, stack)
    }
}
