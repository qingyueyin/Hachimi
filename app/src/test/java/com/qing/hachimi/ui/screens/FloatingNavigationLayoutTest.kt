package com.qing.hachimi.ui.screens

import androidx.compose.ui.unit.dp
import com.qing.hachimi.data.local.FloatingBarEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingNavigationLayoutTest {

    @Test
    fun `phone content clears system navigation and floating bar`() {
        assertEquals(116.dp, floatingContentBottomPadding(24.dp, hasFloatingBar = true))
    }

    @Test
    fun `tablet content only clears system navigation and breathing room`() {
        assertEquals(36.dp, floatingContentBottomPadding(24.dp, hasFloatingBar = false))
    }

    @Test
    fun `docked bar clears system navigation and bar height`() {
        assertEquals(96.dp, dockedContentBottomPadding(24.dp))
    }

    @Test
    fun `backdrop is captured when blur or liquid glass is on`() {
        assertTrue(shouldCaptureBarBackdrop(blurEnabled = true, effect = FloatingBarEffect.NONE))
        assertTrue(
            shouldCaptureBarBackdrop(
                blurEnabled = false,
                effect = FloatingBarEffect.LIQUID_GLASS,
            ),
        )
        assertTrue(
            shouldCaptureBarBackdrop(
                blurEnabled = true,
                effect = FloatingBarEffect.FROSTED_GLASS,
            ),
        )
        assertFalse(shouldCaptureBarBackdrop(blurEnabled = false, effect = FloatingBarEffect.NONE))
    }

    @Test
    fun `liquid glass wins when backdrop is available`() {
        assertEquals(
            FloatingBarStyle.LIQUID,
            resolveFloatingBarStyle(
                effect = FloatingBarEffect.LIQUID_GLASS,
                blurEnabled = true,
                hasBackdrop = true,
            ),
        )
    }

    @Test
    fun `hachimi blur is used for the frosted style`() {
        assertEquals(
            FloatingBarStyle.HACHIMI_BLUR,
            resolveFloatingBarStyle(
                effect = FloatingBarEffect.FROSTED_GLASS,
                blurEnabled = true,
                hasBackdrop = true,
            ),
        )
    }

    @Test
    fun `solid bar is used when effects are unavailable`() {
        assertEquals(
            FloatingBarStyle.SOLID,
            resolveFloatingBarStyle(
                effect = FloatingBarEffect.LIQUID_GLASS,
                blurEnabled = false,
                hasBackdrop = false,
            ),
        )
        assertEquals(
            FloatingBarStyle.SOLID,
            resolveFloatingBarStyle(
                effect = FloatingBarEffect.NONE,
                blurEnabled = true,
                hasBackdrop = true,
            ),
        )
    }

    @Test
    fun `three tabs keep lyrico-sized islands`() {
        assertEquals(236.dp, compactFloatingBarWidth(3, 400.dp))
    }

    @Test
    fun `five tabs shrink before they overflow a phone`() {
        assertEquals(308.dp, compactFloatingBarWidth(5, 400.dp))
    }

    @Test
    fun `six tabs clamp to the available width`() {
        assertEquals(320.dp, compactFloatingBarWidth(6, 400.dp))
        assertEquals(312.dp, compactFloatingBarWidth(6, 312.dp))
    }

    @Test
    fun `empty bar has no width`() {
        assertEquals(0.dp, compactFloatingBarWidth(0, 400.dp))
    }

    @Test
    fun `tablet keeps the bar compact instead of stretching tabs`() {
        // 平板可用宽度很大时，标签宽度不会被拉到 88dp 以上，整条不会被拉成一整行长条
        assertEquals(68.dp * 4 + 8.dp, compactFloatingBarWidth(4, 1280.dp))
        assertEquals(76.dp * 3 + 8.dp, compactFloatingBarWidth(3, 1280.dp))
        assertEquals(52.dp * 6 + 8.dp, compactFloatingBarWidth(6, 1280.dp))
    }
}
