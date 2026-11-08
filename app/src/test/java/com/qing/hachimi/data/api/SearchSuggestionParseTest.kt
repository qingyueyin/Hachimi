package com.qing.hachimi.data.api

import com.qing.hachimi.data.model.SearchCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSuggestionParseTest {

    @Test
    fun `parses allMatch keywords with categories`() {
        val raw = """
            {"result":{"allMatch":[
              {"keyword":"周杰伦 晴天","type":1},
              {"keyword":"周杰伦 专辑","type":10},
              {"keyword":"周杰伦","type":1}
            ]}}
        """.trimIndent()

        val result = parseSearchSuggestions(raw, "周杰伦")

        assertEquals(listOf("周杰伦 晴天", "周杰伦 专辑"), result.map { it.keyword })
        assertEquals(SearchCategory.SONGS, result[0].category)
        assertEquals("周杰伦 专辑", result[1].keyword)
    }

    @Test
    fun `falls back to songs when allMatch is missing`() {
        val raw = """
            {"result":{"songs":[
              {"name":"晴天"},
              {"name":"七里香"},
              {"name":"晴天"}
            ]}}
        """.trimIndent()

        val result = parseSearchSuggestions(raw, "周杰伦")

        assertEquals(listOf("晴天", "七里香"), result.map { it.keyword })
        assertTrue(result.all { it.category == SearchCategory.SONGS })
    }

    @Test
    fun `drops the exact query and blanks`() {
        val raw = """
            {"result":{"allMatch":[
              {"keyword":"晴天","type":1},
              {"keyword":"  ","type":1},
              {"keyword":"晴天 伴奏","type":1}
            ]}}
        """.trimIndent()

        val result = parseSearchSuggestions(raw, "晴天")

        assertEquals(listOf("晴天 伴奏"), result.map { it.keyword })
    }

    @Test
    fun `returns empty for malformed or unexpected payloads`() {
        assertTrue(parseSearchSuggestions("", "x").isEmpty())
        assertTrue(parseSearchSuggestions("not json", "x").isEmpty())
        assertTrue(parseSearchSuggestions("""{"result":{}}""", "x").isEmpty())
        assertTrue(parseSearchSuggestions("""{"code":400}""", "x").isEmpty())
    }
}
