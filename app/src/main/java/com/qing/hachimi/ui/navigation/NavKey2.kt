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
    /**
     * 分类页：比如榜单、歌单分类、新碟等，当前显示哪种由 uiState 决定。
     * 这类「内容是数据、不是页面身份」的 key 一律用 data object：带数据的 key 在数据变化时
     * 会被当成新页面重建，重建瞬间露出「暂时没有内容」。
     */
    @Serializable
    data object DiscoverCategory : NavKey2()

    /** 详情页：歌单/榜单/专辑/歌手，内容同样由 uiState 决定 */
    @Serializable
    data object DiscoverSongList : NavKey2()

    /** 搜索页里的歌手详情：歌手由 SearchViewModel 当前状态决定 */
    @Serializable
    data object ArtistDetail : NavKey2()

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
