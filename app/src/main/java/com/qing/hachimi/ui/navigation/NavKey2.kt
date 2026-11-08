package com.qing.hachimi.ui.navigation

import com.qing.hachimi.data.model.SourceMode
import com.qing.hachimi.ui.screens.MySection
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * 全局页面路由。每个 key 对应导航栈里的一层页面。
 *
 * 约定：Tab 根页面只在栈底出现一次，二级/详情页通过 push 叠上去，
 * 返回由导航组件处理，页面自己不再维护“当前在哪一层”的布尔状态。
 */
@Serializable
sealed class NavKey2 : NavKey {

    // ---- Tab 根页面 ----
    @Serializable
    data object DiscoverHome : NavKey2()

    @Serializable
    data object SearchHome : NavKey2()

    @Serializable
    data object MyHome : NavKey2()

    // ---- 发现 ----
    /** 分类页：比如榜单、歌单分类、新碟等，用 SourceMode 区分 */
    @Serializable
    data class DiscoverCategory(val mode: SourceMode) : NavKey2()

    @Serializable
    data class DiscoverSongList(val mode: SourceMode, val title: String, val depth: Int = 0) : NavKey2()

    @Serializable
    data class ArtistDetail(val artistId: Long, val artistName: String) : NavKey2()

    // ---- 搜索 ----
    @Serializable
    data class SearchResult(val query: String) : NavKey2()

    // ---- 我的 ----
    @Serializable
    data class MySectionPage(val section: MySection) : NavKey2()

    @Serializable
    data class MyPlaylistDetail(val playlistId: Long, val name: String) : NavKey2()

    // ---- 设置二级页 ----
    @Serializable
    data object AppearanceSettings : NavKey2()

    @Serializable
    data object DownloadSettings : NavKey2()

    @Serializable
    data object FileTagSettings : NavKey2()

    @Serializable
    data object AccountSettings : NavKey2()

    @Serializable
    data object PreviewFeatures : NavKey2()

    @Serializable
    data object AboutSettings : NavKey2()

    @Serializable
    data object QualityUpgrade : NavKey2()
}
