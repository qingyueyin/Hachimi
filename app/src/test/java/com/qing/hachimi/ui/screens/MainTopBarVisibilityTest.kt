package com.qing.hachimi.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            resolve(showSearchSubBack = true),
        )
    }

    @Test
    fun `discover detail owns one back step`() {
        assertEquals(ContextBackTarget.DISCOVER, resolve(showDiscoverBack = true))
    }

    @Test
    fun `my section page owns one back step`() {
        assertEquals(ContextBackTarget.MY_SECTION, resolve(showMyPlaylistBack = true))
    }

    @Test
    fun `settings subpage uses the shared top bar`() {
        assertEquals(ContextBackTarget.DOWNLOAD_SETTINGS, resolve(showDownloadSettings = true))
    }

    @Test
    fun `file and tag settings page uses the shared top bar`() {
        assertEquals(ContextBackTarget.FILE_TAG_SETTINGS, resolve(showFileTagSettings = true))
    }

    private fun resolve(
        showAppearanceSettings: Boolean = false,
        showDownloadSettings: Boolean = false,
        showFileTagSettings: Boolean = false,
        showAccountSettings: Boolean = false,
        showDiscoverBack: Boolean = false,
        showMyPlaylistBack: Boolean = false,
        showSearchSubBack: Boolean = false,
    ): ContextBackTarget? = resolveContextBackTarget(
        showAppearanceSettings = showAppearanceSettings,
        showDownloadSettings = showDownloadSettings,
        showFileTagSettings = showFileTagSettings,
        showAccountSettings = showAccountSettings,
        showDiscoverBack = showDiscoverBack,
        showMyPlaylistBack = showMyPlaylistBack,
        showSearchSubBack = showSearchSubBack,
    )
}
