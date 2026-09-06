package com.qing.hachimi.ui.screens

import androidx.compose.ui.unit.dp
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
    fun `backdrop is captured when blur or liquid glass is on`() {
        assertTrue(shouldCaptureFloatingBackdrop(blurEnabled = true, liquidGlassEnabled = false))
        assertTrue(shouldCaptureFloatingBackdrop(blurEnabled = false, liquidGlassEnabled = true))
        assertTrue(shouldCaptureFloatingBackdrop(blurEnabled = true, liquidGlassEnabled = true))
        assertFalse(shouldCaptureFloatingBackdrop(blurEnabled = false, liquidGlassEnabled = false))
    }

    @Test
    fun `liquid glass wins when backdrop is available`() {
        assertEquals(
            FloatingBarStyle.LIQUID,
            resolveFloatingBarStyle(
                liquidGlassEnabled = true,
                blurEnabled = true,
                hasBackdrop = true,
            ),
        )
    }

    @Test
    fun `hachimi blur is used when liquid glass is off`() {
        assertEquals(
            FloatingBarStyle.HACHIMI_BLUR,
            resolveFloatingBarStyle(
                liquidGlassEnabled = false,
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
                liquidGlassEnabled = true,
                blurEnabled = false,
                hasBackdrop = false,
            ),
        )
        assertEquals(
            FloatingBarStyle.SOLID,
            resolveFloatingBarStyle(
                liquidGlassEnabled = false,
                blurEnabled = false,
                hasBackdrop = false,
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
}
