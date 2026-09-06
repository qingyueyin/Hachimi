package com.qing.hachimi.ui.screens

import com.qing.hachimi.data.api.SearchAlbumsResult
import com.qing.hachimi.data.api.SearchArtistsResult
import com.qing.hachimi.data.api.SearchPlaylistsResult
import com.qing.hachimi.data.api.SearchPodcastsResult
import com.qing.hachimi.data.api.SearchSongsResult
import com.qing.hachimi.data.model.AlbumResultDeduplicator
import com.qing.hachimi.data.model.AlbumResult
import com.qing.hachimi.data.model.SearchCategory
import com.qing.hachimi.data.model.SearchResults
import java.text.Normalizer
import java.util.Locale

data class ProcessedSearchResults(
    val results: SearchResults,
    val initialCategory: SearchCategory,
)

object SearchResultProcessor {
    fun process(query: String, input: SearchResults): ProcessedSearchResults {
        val albums = input.albums
            .let(AlbumResultDeduplicator::canonicalize)
            .sortedByDescending { albumScore(query, it) }
        val results = input.copy(
            songs = input.songs.distinctBy { it.id },
            albums = albums,
            artists = input.artists.distinctBy { normalize(it.name) },
            playlists = input.playlists.distinctBy { it.id },
            podcasts = input.podcasts.distinctBy { it.id },
        )
        val initialCategory = when {
            albums.firstOrNull()?.let { albumScore(query, it) >= ALBUM_MATCH_THRESHOLD } == true -> {
                SearchCategory.ALBUMS
            }
            results.songs.isNotEmpty() -> SearchCategory.SONGS
            albums.isNotEmpty() -> SearchCategory.ALBUMS
            results.artists.isNotEmpty() -> SearchCategory.ARTISTS
            results.playlists.isNotEmpty() -> SearchCategory.PLAYLISTS
            results.podcasts.isNotEmpty() -> SearchCategory.PODCASTS
            else -> SearchCategory.SONGS
        }
        return ProcessedSearchResults(results, initialCategory)
    }

    fun mergeSongs(existing: SearchResults, page: SearchSongsResult): SearchResults = existing.copy(
        songs = (existing.songs + page.songs).distinctBy { it.id },
        songOffset = page.nextOffset,
        hasMoreSongs = page.hasMore,
    )

    fun mergeAlbums(existing: SearchResults, page: SearchAlbumsResult): SearchResults = existing.copy(
        albums = AlbumResultDeduplicator.canonicalize(existing.albums + page.albums),
        albumOffset = page.nextOffset,
        hasMoreAlbums = page.hasMore,
    )

    fun mergeArtists(existing: SearchResults, page: SearchArtistsResult): SearchResults = existing.copy(
        artists = (existing.artists + page.artists).distinctBy { normalize(it.name) },
        artistOffset = page.nextOffset,
        hasMoreArtists = page.hasMore,
    )

    fun mergePlaylists(existing: SearchResults, page: SearchPlaylistsResult): SearchResults = existing.copy(
        playlists = (existing.playlists + page.playlists).distinctBy { it.id },
        playlistOffset = page.nextOffset,
        hasMorePlaylists = page.hasMore,
    )

    fun mergePodcasts(existing: SearchResults, page: SearchPodcastsResult): SearchResults = existing.copy(
        podcasts = (existing.podcasts + page.podcasts).distinctBy { it.id },
        podcastOffset = page.nextOffset,
        hasMorePodcasts = page.hasMore,
    )

    internal fun albumScore(query: String, album: AlbumResult): Int {
        val normalizedQuery = normalize(query)
        val normalizedTitle = normalize(album.name)
        if (normalizedQuery.isBlank() || normalizedTitle.isBlank()) return 0

        val queryWords = normalizedQuery.words()
        val titleWords = normalizedTitle.words()
        val artistWords = normalize(album.artist).words()
        var score = 0

        if (normalizedTitle == normalizedQuery) score += 120
        if (
            titleWords.isNotEmpty() &&
            artistWords.isNotEmpty() &&
            queryWords.containsAll(titleWords) &&
            queryWords.containsAll(artistWords)
        ) {
            score += 100
        }
        if (normalizedQuery.contains(normalizedTitle)) score += 80
        if (normalizedTitle.contains(normalizedQuery)) score += 70
        score += titleWords.count(queryWords::contains) * 10
        score += artistWords.count(queryWords::contains) * 5
        return score
    }

    private fun normalize(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .replace(PUNCTUATION, " ")
        .trim()
        .replace(WHITESPACE, " ")

    private fun String.words(): Set<String> = split(' ').filterTo(linkedSetOf()) { it.isNotBlank() }

    private const val ALBUM_MATCH_THRESHOLD = 100
    private val PUNCTUATION = Regex("[\\p{P}]+")
    private val WHITESPACE = Regex("\\s+")
}
