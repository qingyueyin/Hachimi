package com.qing.hachimi.ui.screens

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutTest {

    @Test
    fun `phone width is not wide screen`() {
        assertFalse(isWideScreen(360.dp))
        assertFalse(isWideScreen(411.dp))
        assertFalse(isWideScreen(839.dp))
    }

    @Test
    fun `tablet width is wide screen`() {
        assertTrue(isWideScreen(840.dp))
        assertTrue(isWideScreen(1280.dp))
    }

    @Test
    fun `landscape mid size screens use the navigation rail`() {
        // 宽 ≥600 且宽高比 <1.2 时按宽屏处理（参考 SukiSU）
        assertTrue(isNavigationRailScreen(700.dp, 500.dp))
        assertTrue(isNavigationRailScreen(600.dp, 480.dp))
        // 竖屏/方屏不动
        assertFalse(isNavigationRailScreen(700.dp, 840.dp))
        assertFalse(isNavigationRailScreen(411.dp, 300.dp))
        // 宽度不到 600 的横屏手机仍是底部悬浮栏
        assertFalse(isNavigationRailScreen(599.dp, 360.dp))
    }

    @Test
    fun `wide devices always use the navigation rail`() {
        assertTrue(isNavigationRailScreen(840.dp, 1200.dp))
        assertTrue(isNavigationRailScreen(1280.dp, 800.dp))
    }

    @Test
    fun `phone keeps user columns untouched`() {
        assertEquals(2, adaptiveGridColumns(2, 400.dp, isWide = false))
        assertEquals(4, adaptiveGridColumns(4, 400.dp, isWide = false))
    }

    @Test
    fun `wide screen adds columns until cells fit the max width`() {
        // 640dp 内容区 / 280dp 上限 → 2 列起步（整除向下取整）
        assertEquals(2, adaptiveGridColumns(1, 640.dp, isWide = true))
        assertEquals(2, adaptiveGridColumns(2, 640.dp, isWide = true))
        // 用户已选 3 列时不再减列，只在必要时加列
        assertEquals(3, adaptiveGridColumns(3, 640.dp, isWide = true))
    }

    @Test
    fun `wide screen without rail width never reduces user columns`() {
        // 900dp 容器 / 280dp → 3 列；用户选 2 → 加到 3；用户选 4 → 保持 4
        assertEquals(3, adaptiveGridColumns(2, 900.dp, isWide = true))
        assertEquals(4, adaptiveGridColumns(4, 900.dp, isWide = true))
    }

    @Test
    fun `user columns floor at one`() {
        // 手机端非法列数直接钳到 1
        assertEquals(1, adaptiveGridColumns(0, 400.dp, isWide = false))
        // 宽屏端列数由可用宽度决定，非法输入不生效
        assertEquals(3, adaptiveGridColumns(0, 900.dp, isWide = true))
    }

    @Test
    fun `list style content uses two columns only on very wide screens`() {
        // 手机横屏（600~839dp）与平板竖屏（840~1199dp）都保持单列，列表条目不会被挤压
        assertFalse(useTwoColumnListLayout(600.dp))
        assertFalse(useTwoColumnListLayout(839.dp))
        assertFalse(useTwoColumnListLayout(840.dp))
        assertFalse(useTwoColumnListLayout(1199.dp))
        // 平板横屏起才分两列
        assertTrue(useTwoColumnListLayout(1200.dp))
        assertTrue(useTwoColumnListLayout(1600.dp))
    }
}
