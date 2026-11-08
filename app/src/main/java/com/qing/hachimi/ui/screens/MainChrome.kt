package com.qing.hachimi.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qing.hachimi.ui.theme.AppSpacing
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.NavigationRailState
import androidx.compose.ui.graphics.vector.ImageVector

// ═════════════════════════════════════════════════════════════════════════════
// 主界面外壳：侧边导航栏、底部悬浮栏、顶部磨砂层
// ═════════════════════════════════════════════════════════════════════════════

@Composable
internal fun MainNavigationRail(
    currentTab: Tab,
    isLoggedIn: Boolean,
    railState: NavigationRailState,
    onTabChange: (Tab) -> Unit,
) {
    val visibleTabs = visibleMainTabs(isLoggedIn)
    NavigationRail(
        state = railState,
        color = colorScheme.surface,
        expandContentDescription = "展开导航栏",
        collapseContentDescription = "收起导航栏",
    ) {
        visibleTabs.forEach { tab ->
            NavigationRailItem(
                selected = tab == currentTab,
                onClick = { onTabChange(tab) },
                icon = tab.icon,
                label = tab.label,
            )
        }
    }
}

@Composable
internal fun MainBottomBar(
    modifier: Modifier = Modifier,
    currentTab: Tab,
    isLoggedIn: Boolean,
    backdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop?,
    isBlurEnabled: Boolean,
    isLiquidGlassEnabled: Boolean,
    onTabChange: (Tab) -> Unit,
) {
    val visibleTabs = visibleMainTabs(isLoggedIn)
    val selectedTabIndex = visibleTabs.indexOf(currentTab).coerceAtLeast(0)
    BlurNavigationBar(
        modifier = modifier,
        backdrop = backdrop,
        selectedIndex = selectedTabIndex,
        itemCount = visibleTabs.size,
        isBlurEnabled = isBlurEnabled,
        isLiquidGlassEnabled = isLiquidGlassEnabled,
        onSelectedIndexChange = { index ->
            visibleTabs.getOrNull(index)?.let(onTabChange)
        },
    ) {
        visibleTabs.forEachIndexed { index, tab ->
            BlurNavigationBarItem(
                index = index,
                selected = tab == currentTab,
                onClick = { onTabChange(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(22.dp),
                    )
                },
                label = tab.label,
            )
        }
    }
}

@Composable
internal fun MainDockedNavigationBar(
    modifier: Modifier = Modifier,
    currentTab: Tab,
    isLoggedIn: Boolean,
    onTabChange: (Tab) -> Unit,
) {
    val visibleTabs = visibleMainTabs(isLoggedIn)
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        color = colorScheme.surface,
    ) {
        visibleTabs.forEach { tab ->
            NavigationBarItem(
                selected = tab == currentTab,
                onClick = { onTabChange(tab) },
                icon = tab.icon,
                label = tab.label,
            )
        }
    }
}

@Composable
internal fun PrimaryPageTopInset() {
    TopChrome {
        Column {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Spacer(Modifier.height(AppSpacing.sm))
        }
    }
}

/**
 * 按页面类型给一个固定的顶部内边距。
 *
 * Scaffold 的 body 始终从窗口顶部开始，顶栏高度只通过 innerPadding.top 影响页面。
 * 如果一级页和二级页共用同一个 innerPadding，切页瞬间两边会一起被重新排版
 * （一级页 8dp、二级页 44dp，差 36dp）。每个页面按自己的类型取固定值后，
 * 转场过程中内容不再跟着顶栏跳动。
 */
@Composable
internal fun stablePagePadding(
    base: PaddingValues,
    contextPage: Boolean,
): PaddingValues {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val top = statusBar + if (contextPage) 44.dp else AppSpacing.sm
    val layoutDirection = LocalLayoutDirection.current
    val start = base.calculateStartPadding(layoutDirection)
    val end = base.calculateEndPadding(layoutDirection)
    val bottom = base.calculateBottomPadding()
    return remember(start, top, end, bottom) {
        PaddingValues(start = start, top = top, end = end, bottom = bottom)
    }
}

@Composable
internal fun ContextPageHeader(
    onBackClick: () -> Unit,
    customTitle: String,
    titleAlpha: () -> Float = { 1f },
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopChrome {
        Column {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            SecondaryPageHeader(
                title = customTitle,
                onBack = onBackClick,
                titleAlpha = titleAlpha,
                actions = actions,
            )
        }
    }
}

@Composable
internal fun TopChrome(
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        content()
    }
}

/**
 * 顶部渐变模糊层。
 *
 * 覆盖在页面内容之上、标题栏之下，从状态栏一直向下延伸一段距离，
 * 让标题和从标题下滚过的内容都落在同一条模糊渐变上，过渡更自然。
 */
@Composable
internal fun TopChromeOverlay(
    backdrop: LayerBackdrop?,
    headerHeight: Dp,
    fadeExtension: Dp,
    modifier: Modifier = Modifier,
    progress: () -> Float = { 1f },
) {
    val density = LocalDensity.current
    val surface = colorScheme.surface
    val bodyTint = surface.copy(alpha = if (backdrop != null) 0.28f else 0.78f)
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val solidHeight = statusBarHeight + headerHeight
    val totalHeight = solidHeight + fadeExtension
    val solidFraction = (solidHeight / totalHeight).coerceIn(0f, 1f)
    // 标题区域保持不透，再向下平滑淡出，避免滚动时出现生硬截断
    val chromeMask = Brush.verticalGradient(
        0f to androidx.compose.ui.graphics.Color.Black,
        solidFraction to androidx.compose.ui.graphics.Color.Black,
        1f to androidx.compose.ui.graphics.Color.Transparent,
    )
    val chromeTint = Brush.verticalGradient(
        0f to bodyTint,
        solidFraction to bodyTint,
        1f to surface.copy(alpha = 0f),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(totalHeight)
            .graphicsLayer { alpha = progress() },
    ) {
        if (backdrop != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .textureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        blurRadius = with(density) { 24.dp.toPx() },
                        colors = BlurColors(saturation = 1.08f),
                        contentBlendMode = BlendMode.DstIn,
                    )
                    .background(chromeMask),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    drawRect(brush = chromeTint, size = size)
                },
        )
    }
}