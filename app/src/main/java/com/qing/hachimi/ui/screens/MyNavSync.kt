package com.qing.hachimi.ui.screens

import com.qing.hachimi.ui.navigation.NavKey2
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * 把「我的」页返回栈调到和 ViewModel 的层级一致。
 *
 * ViewModel 决定用户要去的层（section + 是否在详情），返回栈只负责怎么走回去：
 * 回到首页就清掉首页以上所有层；换 section 就替换二级页而不是再压一层；
 * 不再处于详情层时把详情页出栈。返回是否有变化，方便调用方只在必要时触发重组。
 */
internal fun syncMyNavStack(
    stack: MutableList<NavKey>,
    section: MySection,
    isPlaylistDetail: Boolean,
    playlistId: Long,
    playlistName: String,
): Boolean {
    var changed = false
    if (section == MySection.HOME) {
        if (stack.size > 1) {
            stack.subList(1, stack.size).clear()
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
