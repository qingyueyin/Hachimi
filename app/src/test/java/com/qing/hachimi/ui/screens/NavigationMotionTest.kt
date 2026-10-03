package com.qing.hachimi.ui.screens

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationMotionTest {

    @Test
    fun `peer content transition disables size transform so nested lists remain scrollable`() {
        assertNull(peerContentTransition().sizeTransform)
    }

    @Test
    fun `tab transition disables size transform so tab lists remain scrollable`() {
        val density = Density(1f)
        assertNull(directionalTabTransition(true, 1, reduceMotion = false, density).sizeTransform)
        assertNull(directionalTabTransition(true, 1, reduceMotion = true, density).sizeTransform)
    }

    @Test
    fun `hierarchical navigation transition disables size transform so nested lists remain scrollable`() {
        assertNull(hierarchicalNavigationTransition(isForward = true, layoutDirection = 1).sizeTransform)
        assertNull(
            hierarchicalNavigationTransition(isForward = true, layoutDirection = 1, suppress = true).sizeTransform,
        )
    }
}
