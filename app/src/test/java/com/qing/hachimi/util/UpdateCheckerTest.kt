package com.qing.hachimi.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `core version strips git hash and v prefix`() {
        assertEquals("1.0.0", UpdateChecker.coreVersion("1.0.0-0c865c0"))
        assertEquals("1.0.0", UpdateChecker.coreVersion("v1.0.0"))
        assertEquals("1.2.3", UpdateChecker.coreVersion("V1.2.3-abc1234"))
    }

    @Test
    fun `same release as git build is not an update`() {
        assertEquals(0, UpdateChecker.compareVersions("1.0.0", "1.0.0-0c865c0"))
        assertEquals(0, UpdateChecker.compareVersions("v1.0.0", "1.0.0"))
    }

    @Test
    fun `newer tag is an update`() {
        assertTrue(UpdateChecker.compareVersions("1.0.1", "1.0.0-0c865c0") > 0)
        assertTrue(UpdateChecker.compareVersions("1.1.0", "1.0.0") > 0)
        assertTrue(UpdateChecker.compareVersions("2.0.0", "1.9.9") > 0)
    }

    @Test
    fun `older tag is not an update`() {
        assertTrue(UpdateChecker.compareVersions("1.0.0", "1.0.1-deadbee") < 0)
    }
}
