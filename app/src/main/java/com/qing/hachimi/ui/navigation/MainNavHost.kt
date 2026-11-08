package com.qing.hachimi.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitions

/**
 * 主界面导航宿主。
 *
 * 返回手势（预测性返回）、页面位移/缩放、圆角裁剪和遮罩全部由 NavDisplay 提供，
 * 页面自己不再手写手势进度与位移。
 *
 * 返回栈根节点固定为 [NavKey2.DiscoverHome]；[onBackEmpty] 在栈里只剩根页面时触发。
 */
@Composable
fun MainNavHost(
    backStack: SnapshotStateList<NavKey>,
    modifier: Modifier = Modifier,
    transition: NavTransition = NavTransitions.MiuixDefault,
    onBackEmpty: () -> Unit = {},
    entries: NavEntryBuilder.() -> Unit,
) {
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        transition = transition,
        effects = NavDisplayEffects(
            cornerClipRadius = rememberNavSystemCornerRadius(),
            dimAmount = 0.5f,
        ),
        onBack = {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            } else {
                onBackEmpty()
            }
        },
    ) {
        entries()
    }
}

/** 只含发现首页的初始返回栈 */
fun createMainBackStack(): SnapshotStateList<NavKey> =
    mutableStateListOf<NavKey>(NavKey2.DiscoverHome)

/**
 * 把目标返回栈增量同步到当前栈：保留公共前缀，尾部该删的删、该加的加。
 *
 * 不要用 clear()+addAll()：那等于告诉 NavDisplay "整套页面都换了"，
 * 返回手势和转场会按全量替换来演，而不是一次正常的出栈。
 */
internal fun syncNavStack(current: SnapshotStateList<NavKey>, target: List<NavKey>) {
    var common = 0
    while (common < current.size && common < target.size && current[common] == target[common]) {
        common++
    }
    while (current.size > common) {
        current.removeAt(current.lastIndex)
    }
    if (common < target.size) {
        current.addAll(target.subList(common, target.size))
    }
}

@Composable
fun rememberMainNavBackStack(): SnapshotStateList<NavKey> =
    remember { createMainBackStack() }
