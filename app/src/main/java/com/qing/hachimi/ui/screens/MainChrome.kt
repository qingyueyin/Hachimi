package com.qing.hachimi.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.qing.hachimi.data.local.FloatingBarEffect
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.NavigationRailState

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
    effect: FloatingBarEffect,
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
        effect = effect,
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
    backdrop: LayerBackdrop?,
    isBlurEnabled: Boolean,
    onTabChange: (Tab) -> Unit,
) {
    val visibleTabs = visibleMainTabs(isLoggedIn)
    FrostedBar(backdrop = backdrop, blurEnabled = isBlurEnabled, modifier = modifier) {
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            // 底色交给 FrostedBar，磨砂才能透出来
            color = Color.Transparent,
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
}


/** 顶栏返回按钮：SmallTopAppBar 的 navigationIcon 槽位 */
@Composable
internal fun TopBarBackButton(onClick: () -> Unit) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    IconButton(onClick = onClick) {
        Icon(
            imageVector = MiuixIcons.Regular.Back,
            contentDescription = "返回",
            modifier = Modifier.graphicsLayer { if (isRtl) scaleX = -1f },
        )
    }
}

/**
 * 栏容器：开启模糊时整条栏用磨砂背景，内容自己保持透明。
 * 关掉模糊时由这里画实色底，搜索框顶栏也能盖住下面的内容。
 * 磨砂之上叠一层半透明 surface，压住背景里的花色，保证文字和图标看得清。
 */
@Composable
internal fun FrostedBar(
    backdrop: LayerBackdrop?,
    blurEnabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.then(
            if (blurEnabled && backdrop != null) {
                Modifier.textureBlur(
                    backdrop = backdrop,
                    shape = RectangleShape,
                    // 和底栏同一个半径，保证上下两条栏的磨砂强度一致（单位是 dp，库内部自己换算像素）
                    blurRadius = 25f,
                    colors = BlurDefaults.blurColors(
                        blendColors = listOf(
                            BlendColorEntry(colorScheme.surface.copy(alpha = 0.6f)),
                        ),
                    ),
                )
            } else {
                Modifier.background(colorScheme.surface)
            },
        ),
    ) {
        content()
    }
}

/** 搜索页顶栏：搜索框直接占满整条栏 */
@Composable
internal fun SearchTopBar(
    url: String,
    searchQuery: String,
    inputMode: InputMode,
    onInputModeChange: (InputMode) -> Unit,
    onUrlChange: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onParse: () -> Unit,
    onSearch: () -> Unit,
    isLoading: Boolean,
    isSearching: Boolean,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            // 搜索页栏比普通顶栏高：40dp 搜索框 + 上下各 16dp，跟改之前的搜索区一致
            .height(72.dp)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        SearchInputBar(
            url = url,
            searchQuery = searchQuery,
            inputMode = inputMode,
            onInputModeChange = onInputModeChange,
            onUrlChange = onUrlChange,
            onSearchQueryChange = onSearchQueryChange,
            onParse = onParse,
            onSearch = onSearch,
            isLoading = isLoading,
            isSearching = isSearching,
            interactionSource = interactionSource,
            insideMargin = DpSize(16.dp, 0.dp),
        )
    }
}
