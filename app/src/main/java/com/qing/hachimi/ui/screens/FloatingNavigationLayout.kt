package com.qing.hachimi.ui.screens

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qing.hachimi.data.local.FloatingBarEffect

internal val FloatingNavigationBarHeight = 64.dp
internal val FloatingNavigationBarBottomMargin = 16.dp
internal val ContentBreathingRoom = 12.dp
internal val FloatingBarEdgeMargin = 16.dp
internal val FloatingBarInnerPadding = 4.dp

internal enum class FloatingBarStyle {
    LIQUID,
    HACHIMI_BLUR,
    SOLID,
}

internal fun shouldCaptureBarBackdrop(
    blurEnabled: Boolean,
    effect: FloatingBarEffect,
): Boolean = blurEnabled || effect == FloatingBarEffect.LIQUID_GLASS

internal fun resolveFloatingBarStyle(
    effect: FloatingBarEffect,
    blurEnabled: Boolean,
    hasBackdrop: Boolean,
): FloatingBarStyle = when {
    effect == FloatingBarEffect.LIQUID_GLASS && hasBackdrop -> FloatingBarStyle.LIQUID
    effect == FloatingBarEffect.FROSTED_GLASS && blurEnabled -> FloatingBarStyle.HACHIMI_BLUR
    else -> FloatingBarStyle.SOLID
}

internal fun floatingBarTabMinWidth(itemCount: Int): Dp = when {
    itemCount <= 3 -> 76.dp
    itemCount <= 4 -> 68.dp
    itemCount <= 5 -> 60.dp
    else -> 52.dp
}

/** 单个标签的宽度上限：平板上不再把每个标签拉宽，整条保持紧凑（参考 SukiSU 的悬浮底栏） */
internal val FloatingBarTabMaxWidth = 88.dp

internal fun compactFloatingBarWidth(itemCount: Int, maxWidth: Dp): Dp {
    if (itemCount <= 0) return 0.dp
    val desired = (floatingBarTabMinWidth(itemCount) * itemCount + FloatingBarInnerPadding * 2)
        .coerceAtMost(FloatingBarTabMaxWidth * itemCount + FloatingBarInnerPadding * 2)
    val limit = maxWidth.coerceAtLeast(0.dp)
    return if (desired < limit) desired else limit
}

internal fun floatingContentBottomPadding(systemBottom: Dp, hasFloatingBar: Boolean): Dp =
    systemBottom + ContentBreathingRoom + if (hasFloatingBar) {
        FloatingNavigationBarHeight + FloatingNavigationBarBottomMargin
    } else {
        0.dp
    }

/** Miuix NavigationBar：itemHeight 64dp + bottomPadding 8dp */
internal val DockedNavigationBarHeight = 72.dp

internal fun dockedContentBottomPadding(systemBottom: Dp): Dp =
    systemBottom + DockedNavigationBarHeight

val LocalMainBottomContentPadding = staticCompositionLocalOf { ContentBreathingRoom }

internal fun visibleMainTabs(isLoggedIn: Boolean): List<Tab> =
    Tab.entries.filter { tab -> tab != Tab.MY || isLoggedIn }
