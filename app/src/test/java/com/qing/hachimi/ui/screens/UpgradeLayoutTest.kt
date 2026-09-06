package com.qing.hachimi.ui.screens

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class UpgradeLayoutTest {

    @Test
    fun `upgrade action uses the shared floating navigation clearance`() {
        assertEquals(
            116.dp,
            floatingContentBottomPadding(24.dp, hasFloatingBar = true),
        )
    }
}
