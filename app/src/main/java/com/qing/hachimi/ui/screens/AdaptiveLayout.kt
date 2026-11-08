package com.qing.hachimi.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 平板/宽屏适配。
 *
 * 断点与 Miuix DialogDefaults.isLargeScreen 一致（840dp），保证对话框居中
 * 与侧边导航栏在同一台设备上同时生效。
 */
internal val WideScreenMinWidth = 840.dp

/**
 * 宽屏下内容区的最大宽度，超出的部分左右留白居中。
 */
internal val WideScreenContentMaxWidth = 640.dp

/**
 * 宽屏下网格单元格的最大宽度，超过该值自动加列。
 * 手机端列数完全由用户设置决定，不参与此逻辑。
 */
internal val WideScreenGridCellMaxWidth = 280.dp

internal fun isWideScreen(windowWidth: Dp): Boolean = windowWidth >= WideScreenMinWidth

/**
 * 横屏平板断点（参考 SukiSU）：宽度 ≥600dp 且宽高比 <1.2 时也按宽屏处理，
 * 覆盖横向摆放的中尺寸设备。
 */
internal val LandscapeWideMinWidth = 600.dp

/**
 * 侧边导航栏判定：够宽，或者横向摆放且够大。
 * 注意：与 Miuix 对话框大屏判定无关，仅决定主界面用侧边栏还是底部悬浮栏。
 */
internal fun isNavigationRailScreen(windowWidth: Dp, windowHeight: Dp): Boolean =
    isWideScreen(windowWidth) ||
        (windowWidth >= LandscapeWideMinWidth && windowHeight / windowWidth < 1.2f)

/**
 * 宽屏下的网格列数：在用户设置的基础上自动加列，保证单元格不超过
 * [WideScreenGridCellMaxWidth]；手机端（isWide=false）原样返回用户列数。
 */
internal fun adaptiveGridColumns(userColumns: Int, availableWidth: Dp, isWide: Boolean): Int {
    val base = userColumns.coerceAtLeast(1)
    if (!isWide) return base
    val extra = ((availableWidth / WideScreenGridCellMaxWidth).toInt() - base).coerceAtLeast(0)
    return base + extra
}

@Composable
internal fun rememberIsWideScreen(): Boolean {
    val windowWidth = LocalWindowInfo.current.containerDpSize.width
    return remember(windowWidth) { isWideScreen(windowWidth) }
}

@Composable
internal fun rememberIsNavigationRailScreen(): Boolean {
    val size = LocalWindowInfo.current.containerDpSize
    return remember(size) { isNavigationRailScreen(size.width, size.height) }
}

/**
 * 宽屏下把内容限宽到 [WideScreenContentMaxWidth] 并水平居中；手机端原样透传。
 */
@Composable
internal fun WidthLimiter(content: @Composable () -> Unit) {
    if (rememberIsWideScreen()) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = WideScreenContentMaxWidth)
            ) {
                content()
            }
        }
    } else {
        content()
    }
}
