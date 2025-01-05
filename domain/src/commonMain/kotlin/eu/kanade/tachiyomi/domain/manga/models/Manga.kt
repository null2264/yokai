package eu.kanade.tachiyomi.domain.manga.models

import eu.kanade.tachiyomi.source.model.SManga
import java.util.Locale
import yokai.domain.manga.models.MangaUpdate

// TODO: Transform into data class
@Deprecated("Use data class version", ReplaceWith("yokai.domain.manga.models.Manga"))
interface Manga : SManga {

    var id: Long?

    var source: Long

    var favorite: Boolean

    var last_update: Long

    var date_added: Long

    var viewer_flags: Int

    var chapter_flags: Int

    var hide_title: Boolean

    var filtered_scanlators: String?

    var ogTitle: String
    var ogAuthor: String?
    var ogArtist: String?
    var ogDesc: String?
    var ogGenre: String?
    var ogStatus: Int

    var cover_last_modified: Long

    @Deprecated("Use ogTitle directly instead", ReplaceWith("ogTitle"))
    val originalTitle: String
        get() = ogTitle
    val originalAuthor: String?
        get() = ogAuthor ?: author
    val originalArtist: String?
        get() = ogArtist ?: artist
    val originalDescription: String?
        get() = ogDesc ?: description
    val originalGenre: String?
        get() = ogGenre ?: genre
    @Deprecated("Use ogStatus directly instead", ReplaceWith("ogStatus"))
    val originalStatus: Int
        get() = ogStatus

    val hasSameAuthorAndArtist: Boolean
        get() = author == artist || artist.isNullOrBlank() ||
            author?.contains(artist ?: "", true) == true

    fun copyFrom(other: SManga) {
        thumbnail_url = other.thumbnail_url ?: thumbnail_url

        if (other.author != null) {
            author = if (other is Manga) other.originalAuthor else other.author
        }

        if (other.artist != null) {
            artist = if (other is Manga) other.originalArtist else other.artist
        }

        if (other.description != null) {
            description = if (other is Manga) other.originalDescription else other.description
        }

        if (other.genre != null) {
            genre = if (other is Manga) other.originalGenre else other.genre
        }

        status = if (other is Manga) other.originalStatus else other.status

        update_strategy = other.update_strategy

        if (!initialized) {
            initialized = other.initialized
        }
    }

    fun setChapterOrder(sorting: Int, order: Int) {
        setChapterFlags(sorting, CHAPTER_SORTING_MASK)
        setChapterFlags(order, CHAPTER_SORT_MASK)
        setChapterFlags(CHAPTER_SORT_LOCAL, CHAPTER_SORT_LOCAL_MASK)
    }

    fun setSortToGlobal() = setChapterFlags(CHAPTER_SORT_FILTER_GLOBAL, CHAPTER_SORT_LOCAL_MASK)

    fun setFilterToGlobal() = setChapterFlags(CHAPTER_SORT_FILTER_GLOBAL, CHAPTER_FILTER_LOCAL_MASK)
    fun setFilterToLocal() = setChapterFlags(CHAPTER_FILTER_LOCAL, CHAPTER_FILTER_LOCAL_MASK)

    private fun setChapterFlags(flag: Int, mask: Int) {
        chapter_flags = chapter_flags and mask.inv() or (flag and mask)
    }

    fun setViewerFlags(flag: Int, mask: Int) {
        viewer_flags = viewer_flags and mask.inv() or (flag and mask)
    }

    val sortDescending: Boolean
        get() = chapter_flags and CHAPTER_SORT_MASK == CHAPTER_SORT_DESC

    val hideChapterTitles: Boolean
        get() = displayMode == CHAPTER_DISPLAY_NUMBER

    val usesLocalSort: Boolean
        get() = chapter_flags and CHAPTER_SORT_LOCAL_MASK == CHAPTER_SORT_LOCAL

    val usesLocalFilter: Boolean
        get() = chapter_flags and CHAPTER_FILTER_LOCAL_MASK == CHAPTER_FILTER_LOCAL

    fun showChapterTitle(defaultShow: Boolean): Boolean = chapter_flags and CHAPTER_DISPLAY_MASK == CHAPTER_DISPLAY_NUMBER

    fun getOriginalGenres(): List<String>? {
        return (originalGenre ?: genre)?.split(",")
            ?.mapNotNull { tag -> tag.trim().takeUnless { it.isBlank() } }
    }

    fun isSeriesTag(tag: String): Boolean {
        val tagLower = tag.lowercase(Locale.ROOT)
        return isMangaTag(tagLower) || isManhuaTag(tagLower) ||
            isManhwaTag(tagLower) || isComicTag(tagLower) || isWebtoonTag(tagLower)
    }

    fun isMangaTag(tag: String): Boolean {
        return tag in listOf("manga", "манга", "jp") || tag.startsWith("japanese")
    }

    fun isManhuaTag(tag: String): Boolean {
        return tag in listOf("manhua", "маньхуа", "cn", "hk", "zh-Hans", "zh-Hant") || tag.startsWith("chinese")
    }

    fun isLongStrip(): Boolean {
        val currentTags =
            genre?.split(",")?.map { it.trim().lowercase(Locale.US) } ?: emptyList()
        return currentTags.any { it == "long strip" }
    }

    fun isManhwaTag(tag: String): Boolean {
        return tag in listOf("long strip", "manhwa", "манхва", "kr") || tag.startsWith("korean")
    }

    fun isComicTag(tag: String): Boolean {
        return tag in listOf("comic", "комикс", "en", "gb")
    }

    fun isWebtoonTag(tag: String): Boolean {
        return tag.startsWith("webtoon")
    }

    fun isWebtoonSource(sourceName: String): Boolean {
        return sourceName.contains("webtoon", true) ||
            sourceName.contains("manhwa", true) ||
            sourceName.contains("toonily", true)
    }

    fun isComicSource(sourceName: String): Boolean {
        return sourceName.contains("gunnerkrigg", true) ||
            sourceName.contains("dilbert", true) ||
            sourceName.contains("cyanide", true) ||
            sourceName.contains("xkcd", true) ||
            sourceName.contains("tapas", true) ||
            sourceName.contains("ComicExtra", true) ||
            sourceName.contains("Read Comics Online", true) ||
            sourceName.contains("ReadComicOnline", true)
    }

    fun key(): String {
        return "manga-id-$id"
    }

    // Used to display the chapter's title one way or another
    var displayMode: Int
        get() = chapter_flags and CHAPTER_DISPLAY_MASK
        set(mode) = setChapterFlags(mode, CHAPTER_DISPLAY_MASK)

    var readFilter: Int
        get() = chapter_flags and CHAPTER_READ_MASK
        set(filter) = setChapterFlags(filter, CHAPTER_READ_MASK)

    var downloadedFilter: Int
        get() = chapter_flags and CHAPTER_DOWNLOADED_MASK
        set(filter) = setChapterFlags(filter, CHAPTER_DOWNLOADED_MASK)

    var bookmarkedFilter: Int
        get() = chapter_flags and CHAPTER_BOOKMARKED_MASK
        set(filter) = setChapterFlags(filter, CHAPTER_BOOKMARKED_MASK)

    var sorting: Int
        get() = chapter_flags and CHAPTER_SORTING_MASK
        set(sort) = setChapterFlags(sort, CHAPTER_SORTING_MASK)

    fun toMangaUpdate(): MangaUpdate {
        return MangaUpdate(
            id = id!!,
            source = source,
            url = url,
            artist = artist,
            author = author,
            description = description,
            genres = genre?.split(", ").orEmpty(),
            title = title,
            status = status,
            thumbnailUrl = thumbnail_url,
            favorite = favorite,
            lastUpdate = last_update,
            initialized = initialized,
            viewerFlags = viewer_flags,
            hideTitle = hide_title,
            chapterFlags = chapter_flags,
            dateAdded = date_added,
            filteredScanlators = filtered_scanlators,
            updateStrategy = update_strategy,
        )
    }

    companion object {

        // Generic filter that does not filter anything
        const val SHOW_ALL = 0x00000000

        const val CHAPTER_SORT_DESC = 0x00000000
        const val CHAPTER_SORT_ASC = 0x00000001
        const val CHAPTER_SORT_MASK = 0x00000001

        const val CHAPTER_SORT_FILTER_GLOBAL = 0x00000000
        const val CHAPTER_SORT_LOCAL = 0x00001000
        const val CHAPTER_SORT_LOCAL_MASK = 0x00001000
        const val CHAPTER_FILTER_LOCAL = 0x00002000
        const val CHAPTER_FILTER_LOCAL_MASK = 0x00002000

        const val CHAPTER_SHOW_UNREAD = 0x00000002
        const val CHAPTER_SHOW_READ = 0x00000004
        const val CHAPTER_READ_MASK = 0x00000006

        const val CHAPTER_SHOW_DOWNLOADED = 0x00000008
        const val CHAPTER_SHOW_NOT_DOWNLOADED = 0x00000010
        const val CHAPTER_DOWNLOADED_MASK = 0x00000018

        const val CHAPTER_SHOW_BOOKMARKED = 0x00000020
        const val CHAPTER_SHOW_NOT_BOOKMARKED = 0x00000040
        const val CHAPTER_BOOKMARKED_MASK = 0x00000060

        const val CHAPTER_SORTING_SOURCE = 0x00000000
        const val CHAPTER_SORTING_NUMBER = 0x00000100
        const val CHAPTER_SORTING_UPLOAD_DATE = 0x00000200
        const val CHAPTER_SORTING_MASK = 0x00000300

        const val CHAPTER_DISPLAY_NAME = 0x00000000
        const val CHAPTER_DISPLAY_NUMBER = 0x00100000
        const val CHAPTER_DISPLAY_MASK = 0x00100000

        const val TYPE_MANGA = 1
        const val TYPE_MANHWA = 2
        const val TYPE_MANHUA = 3
        const val TYPE_COMIC = 4
        const val TYPE_WEBTOON = 5
    }
}
