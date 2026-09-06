package com.qing.hachimi.ui.screens

import com.qing.hachimi.data.api.SearchAlbumsResult
import com.qing.hachimi.data.model.AlbumResult
import com.qing.hachimi.data.model.SearchCategory
import com.qing.hachimi.data.model.SearchResults
import com.qing.hachimi.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SearchResultProcessorTest {

    @Test
    fun `artist plus album query opens albums and removes semantic duplicates`() {
        val results = SearchResults(
            songs = listOf(song(1, "petal", "Other")),
            albums = listOf(
                AlbumResult(390031297, "petal", "Ariana Grande", publishTime = 1_000),
                AlbumResult(390031118, "Petal", "ariana grande", publishTime = 2_000),
                AlbumResult(389119973, "我不难过live", "."),
            ),
        )

        val processed = SearchResultProcessor.process("Ariana Grande petal", results)

        assertEquals(SearchCategory.ALBUMS, processed.initialCategory)
        assertEquals(listOf(390031118L, 389119973L), processed.results.albums.map { it.id })
    }

    @Test
    fun `normalization treats width punctuation and case as the same album`() {
        val results = SearchResults(
            albums = listOf(
                AlbumResult(1, "ＰＥＴＡＬ！", "Ariana  Grande"),
                AlbumResult(2, "petal", "ariana-grande"),
            ),
        )

        val processed = SearchResultProcessor.process("Ariana Grande petal", results)

        assertEquals(listOf(1L), processed.results.albums.map { it.id })
    }

    @Test
    fun `page merge keeps unique items but advances by raw server page size`() {
        val existing = SearchResults(
            albums = listOf(AlbumResult(1, "Album", "Artist")),
            albumOffset = 20,
        )
        val page = SearchAlbumsResult(
            albums = listOf(
                AlbumResult(2, "Album", "Artist"),
                AlbumResult(3, "Second", "Artist"),
            ),
            total = 21,
            nextOffset = 22,
            hasMore = false,
        )

        val merged = SearchResultProcessor.mergeAlbums(existing, page)

        assertEquals(listOf(1L, 3L), merged.albums.map { it.id })
        assertEquals(22, merged.albumOffset)
        assertFalse(merged.hasMoreAlbums)
    }

    private fun song(id: Long, name: String, artist: String) = Song(
        id = id,
        name = name,
        artists = artist,
        album = "",
        coverUrl = "",
    )
}
