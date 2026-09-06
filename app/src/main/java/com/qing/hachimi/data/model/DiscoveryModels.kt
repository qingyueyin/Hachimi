package com.qing.hachimi.data.model

data class DiscoveryPage<T>(
    val items: List<T> = emptyList(),
    val nextOffset: Int = 0,
    val hasMore: Boolean = false,
)

enum class CollectionKind(val label: String, val itemUnit: String) {
    SONG("歌曲", "首"),
    CHART("榜单", "首"),
    ALBUM("专辑", "首"),
    PLAYLIST("歌单", "首"),
    PODCAST("播客", "期"),
}

data class CollectionDetail(
    val kind: CollectionKind,
    val id: Long = 0,
    val title: String,
    val subtitle: String = "",
    val coverUrl: String = "",
    val description: String = "",
    val expectedItemCount: Int = 0,
    val playCount: Long = 0,
    val publishTime: Long = 0,
    val updateTime: Long = 0,
    val updateFrequency: String = "",
    val company: String = "",
    val subtype: String = "",
    val tags: List<String> = emptyList(),
)

data class CollectionContent(
    val detail: CollectionDetail,
    val songs: List<Song> = emptyList(),
)

data class DiscoveryPlaylist(
    val id: Long,
    val name: String,
    val coverUrl: String = "",
    val creator: String = "",
    val trackCount: Int = 0,
    val playCount: Long = 0,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val createTime: Long = 0,
    val updateTime: Long = 0,
)

data class PodcastChannel(
    val id: Long,
    val name: String,
    val coverUrl: String = "",
    val host: String = "",
    val category: String = "",
    val programCount: Int = 0,
    val playCount: Long = 0,
    val description: String = "",
    val createTime: Long = 0,
    val updateTime: Long = 0,
)

data class ArtistProfile(
    val id: Long,
    val name: String,
    val coverUrl: String = "",
    val avatarUrl: String = "",
    val translatedNames: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val identity: String = "",
    val briefDescription: String = "",
    val albumCount: Int = 0,
    val songCount: Int = 0,
)

data class ArtistIntroduction(
    val title: String,
    val text: String,
)

data class ArtistAbout(
    val briefDescription: String = "",
    val introductions: List<ArtistIntroduction> = emptyList(),
    val similarArtists: List<ArtistResult> = emptyList(),
    val similarArtistsMessage: String? = null,
)

enum class ArtistDetailSection(val label: String) {
    HOT_SONGS("热门"),
    ALBUMS("专辑"),
    ABOUT("简介"),
}

enum class PlaylistCategory(val label: String, val apiValue: String) {
    ALL("全部", "全部"),
    CHINESE("华语", "华语"),
    WESTERN("欧美", "欧美"),
    POP("流行", "流行"),
    ROCK("摇滚", "摇滚"),
    FOLK("民谣", "民谣"),
    ELECTRONIC("电子", "电子"),
    ACG("ACG", "ACG"),
}

enum class ArtistArea(val label: String, val apiValue: Int?) {
    HOT("热门", null),
    ALL("全部", -1),
    CHINESE("华语", 7),
    WESTERN("欧美", 96),
    JAPANESE("日本", 8),
    KOREAN("韩国", 16),
}
