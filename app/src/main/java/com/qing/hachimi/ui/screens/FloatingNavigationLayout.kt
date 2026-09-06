package com.qing.hachimi.ui.screens

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

internal fun shouldCaptureFloatingBackdrop(
    blurEnabled: Boolean,
    liquidGlassEnabled: Boolean,
): Boolean = blurEnabled || liquidGlassEnabled

internal fun resolveFloatingBarStyle(
    liquidGlassEnabled: Boolean,
    blurEnabled: Boolean,
    hasBackdrop: Boolean,
): FloatingBarStyle = when {
    liquidGlassEnabled && hasBackdrop -> FloatingBarStyle.LIQUID
    blurEnabled -> FloatingBarStyle.HACHIMI_BLUR
    else -> FloatingBarStyle.SOLID
}

internal fun floatingBarTabMinWidth(itemCount: Int): Dp = when {
    itemCount <= 3 -> 76.dp
    itemCount <= 4 -> 68.dp
    itemCount <= 5 -> 60.dp
    else -> 52.dp
}

internal fun compactFloatingBarWidth(itemCount: Int, maxWidth: Dp): Dp {
    if (itemCount <= 0) return 0.dp
    val desired = floatingBarTabMinWidth(itemCount) * itemCount + FloatingBarInnerPadding * 2
    val limit = maxWidth.coerceAtLeast(0.dp)
    return if (desired < limit) desired else limit
}

internal fun floatingContentBottomPadding(systemBottom: Dp, hasFloatingBar: Boolean): Dp =
    systemBottom + ContentBreathingRoom + if (hasFloatingBar) {
        FloatingNavigationBarHeight + FloatingNavigationBarBottomMargin
    } else {
        0.dp
    }

val LocalMainBottomContentPadding = staticCompositionLocalOf { ContentBreathingRoom }

internal fun visibleMainTabs(isLoggedIn: Boolean): List<Tab> =
    Tab.entries.filter { tab -> tab != Tab.MY || isLoggedIn }
