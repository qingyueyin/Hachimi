package com.qing.hachimi.ui.screens

import com.qing.hachimi.ui.navigation.NavKey2
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * 把「我的」页返回栈调到和 ViewModel 的层级一致。
 *
 * ViewModel 决定用户要去的层（section + 是否在详情），返回栈只负责怎么走回去：
 * 回到首页就清掉首页以上所有层（首页直接打开的详情除外，保留「首页 + 详情」两层）；
 * 换 section 就替换二级页而不是再压一层；不再处于详情层时把详情页出栈。
 * 返回是否有变化，方便调用方只在必要时触发重组。
 */
internal fun syncMyNavStack(
    stack: MutableList<NavKey>,
    section: MySection,
    isPlaylistDetail: Boolean,
    playlistId: Long,
    playlistName: String,
): Boolean {
    if (stack.isEmpty()) return false
    var changed = false
    if (section == MySection.HOME) {
        // 首页也能直接点歌单卡片进详情，这时栈是「首页 + 详情」两层；
        // 不在详情就只留首页，切回首页时把二级页一并清掉
        val existing = stack.firstOrNull { it is NavKey2.MyPlaylistDetail }
        val desired: List<NavKey> = if (isPlaylistDetail) {
            listOf(stack.first(), existing ?: NavKey2.MyPlaylistDetail(playlistId, playlistName))
        } else {
            listOf(stack.first())
        }
        if (stack != desired) {
            stack.clear()
            stack.addAll(desired)
            changed = true
        }
        return changed
    }
    val sectionTop = NavKey2.MySectionPage(section)
    if (stack.none { it is NavKey2.MySectionPage }) {
        stack.add(sectionTop)
        changed = true
    } else if (stack.none { it == sectionTop }) {
        stack[stack.indexOfFirst { it is NavKey2.MySectionPage }] = sectionTop
        changed = true
    }
    val detailIndex = stack.indexOfFirst { it is NavKey2.MyPlaylistDetail }
    if (isPlaylistDetail && detailIndex < 0) {
        stack.add(NavKey2.MyPlaylistDetail(playlistId, playlistName))
        changed = true
    } else if (!isPlaylistDetail && detailIndex >= 0) {
        stack.removeAt(detailIndex)
        changed = true
    }
    return changed
}
