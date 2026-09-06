package com.qing.hachimi.ui.screens

import com.qing.hachimi.data.model.CollectionDetail
import com.qing.hachimi.data.model.CollectionKind
import com.qing.hachimi.data.model.SourceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainContextBackTargetTest {

    @Test
    fun `primary tabs do not own a contextual back route`() {
        assertNull(resolve())
    }

    @Test
    fun `search detail returns to results before its legacy source`() {
        assertEquals(
            ContextBackTarget.SEARCH_DETAIL,
            resolve(showSearchSubBack = true, showSearchBack = true),
        )
    }

    @Test
    fun `discover detail owns one back step`() {
        assertEquals(ContextBackTarget.DISCOVER, resolve(showDiscoverBack = true))
    }

    @Test
    fun `my playlist detail owns one back step`() {
        assertEquals(ContextBackTarget.MY_SECTION, resolve(showMyPlaylistBack = true))
    }

    @Test
    fun `settings subpage uses inline header instead of contextual top bar`() {
        val target = resolve(showDownloadSettings = true)

        assertEquals(ContextBackTarget.DOWNLOAD_SETTINGS, target)
        assertFalse(target!!.usesContextHeader)
    }

    @Test
    fun `content details use the contextual top bar`() {
        assertTrue(resolve(showSearchSubBack = true)!!.usesContextHeader)
        assertTrue(resolve(showDiscoverBack = true)!!.usesContextHeader)
        assertFalse(resolve(showMyPlaylistBack = true)!!.usesContextHeader)
    }

    @Test
    fun `top chrome offset follows consumed scroll only`() {
        assertEquals(80f, calculateTopChromeScrollOffset(0f, consumedY = -80f), 0f)
        assertEquals(48f, calculateTopChromeScrollOffset(80f, consumedY = 32f), 0f)
        assertEquals(0f, calculateTopChromeScrollOffset(48f, consumedY = 80f), 0f)
    }

    @Test
    fun `discover detail has a different top chrome page key from its parent`() {
        val parent = resolveTopChromePageKey(
            currentTab = Tab.DISCOVER,
            contextBackTarget = ContextBackTarget.DISCOVER,
            discoverState = DiscoverUiState(sourceMode = SourceMode.CHARTS),
            searchState = SearchUiState(),
            myState = MyUiState(),
        )
        val detail = resolveTopChromePageKey(
            currentTab = Tab.DISCOVER,
            contextBackTarget = ContextBackTarget.DISCOVER,
            discoverState = DiscoverUiState(
                sourceMode = SourceMode.CHARTS,
                sourceLabel = "榜单",
                collectionDetail = CollectionDetail(
                    kind = CollectionKind.CHART,
                    id = 19723756L,
                    title = "飙升榜",
                ),
            ),
            searchState = SearchUiState(),
            myState = MyUiState(),
        )

        assertTrue(parent != detail)
    }

    @Test
    fun `top chrome page key stays stable when the same detail finishes loading`() {
        val initial = DiscoverUiState(
            sourceMode = SourceMode.CHARTS,
            sourceLabel = "榜单",
            collectionDetail = CollectionDetail(
                kind = CollectionKind.CHART,
                id = 19723756L,
                title = "飙升榜",
            ),
        )
        val loaded = initial.copy(
            collectionDetail = initial.collectionDetail!!.copy(
                description = "updated",
                expectedItemCount = 200,
            ),
        )

        assertEquals(
            resolveTopChromePageKey(
                Tab.DISCOVER,
                ContextBackTarget.DISCOVER,
                initial,
                SearchUiState(),
                MyUiState(),
            ),
            resolveTopChromePageKey(
                Tab.DISCOVER,
                ContextBackTarget.DISCOVER,
                loaded,
                SearchUiState(),
                MyUiState(),
            ),
        )
    }

    private fun resolve(
        showAppearanceSettings: Boolean = false,
        showDownloadSettings: Boolean = false,
        showAccountSettings: Boolean = false,
        showSearchBack: Boolean = false,
        showDiscoverBack: Boolean = false,
        showMyPlaylistBack: Boolean = false,
        showSearchSubBack: Boolean = false,
    ): ContextBackTarget? = resolveContextBackTarget(
        showAppearanceSettings = showAppearanceSettings,
        showDownloadSettings = showDownloadSettings,
        showAccountSettings = showAccountSettings,
        showSearchBack = showSearchBack,
        showDiscoverBack = showDiscoverBack,
        showMyPlaylistBack = showMyPlaylistBack,
        showSearchSubBack = showSearchSubBack,
    )
}
