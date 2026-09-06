package com.qing.hachimi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalNoticeTest {

    @Test
    fun `notice covers required pre-use clauses`() {
        val text = LegalNotice.fullText
        assertEquals("使用前须知", LegalNotice.TITLE)
        assertEquals(6, LegalNotice.CLAUSES.size)
        listOf("24 小时", "网易云", "版权", "所有文件访问", "风险自负", "github.com/qingyueyin/Hachimi", "设置 → 使用前须知").forEach { token ->
            assertTrue("missing: $token", text.contains(token))
        }
    }
}
