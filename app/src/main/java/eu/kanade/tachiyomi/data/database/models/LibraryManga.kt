package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.ui.library.LibraryItem
import kotlin.math.roundToInt
import yokai.data.updateStrategyAdapter

data class LibraryManga(
    var unread: Int = 0,
    var read: Int = 0,
    var category: Long = 0,
    var bookmarkCount: Int = 0,
    var totalChapters: Int = 0,
    var latestUpdate: Long = 0,
    var lastRead: Long = 0,
    var lastFetch: Long = 0,
) : MangaImpl() {

    var realMangaCount = 0
        get() = if (isBlank()) field else throw IllegalStateException("realMangaCount is only accessible by placeholders")
        set(value) {
            if (!isBlank()) throw IllegalStateException("realMangaCount can only be set by placeholders")
            field = value
        }

    val hasRead
        get() = read > 0

    @Transient
    var items: List<LibraryItem>? = null
        get() = if (isHidden()) field else throw IllegalStateException("items only accessible by placeholders")
        set(value) {
            if (!isHidden()) throw IllegalStateException("items can only be set by placeholders")
            field = value
        }

    companion object {
        fun createBlank(categoryId: Long): LibraryManga = LibraryManga().apply {
            title = ""
            id = Long.MIN_VALUE
            category = categoryId
        }

        fun createHide(categoryId: Long, title: String, hiddenItems: List<LibraryItem>): LibraryManga =
            createBlank(categoryId).apply {
                this.title = title
                this.status = -1
                this.read = hiddenItems.size
                this.items = hiddenItems
            }

        fun mapper(
            // manga
            id: Long,
            source: Long,
            url: String,
            artist: String?,
            author: String?,
            description: String?,
            genre: String?,
            title: String,
            status: Long,
            thumbnailUrl: String?,
            favorite: Boolean,
            lastUpdate: Long?,
            initialized: Boolean,
            viewerFlags: Long,
            hideTitle: Boolean,
            chapterFlags: Long,
            dateAdded: Long?,
            filteredScanlators: String?,
            updateStrategy: Long,
            coverLastModified: Long,
            // libraryManga
            total: Long,
            readCount: Double,
            bookmarkCount: Double,
            categoryId: Long,
            latestUpdate: Long,
            lastRead: Long,
            lastFetch: Long,
        ): LibraryManga = LibraryManga().apply {
            this.id = id
            this.source = source
            this.url = url
            this.artist = artist
            this.author = author
            this.description = description
            this.genre = genre
            this.title = title
            this.status = status.toInt()
            this.thumbnail_url = thumbnailUrl
            this.favorite = favorite
            this.last_update = lastUpdate ?: 0L
            this.initialized = initialized
            this.viewer_flags = viewerFlags.toInt()
            this.hide_title = hideTitle
            this.chapter_flags = chapterFlags.toInt()
            this.date_added = dateAdded ?: 0L
            this.filtered_scanlators = filteredScanlators
            this.update_strategy = updateStrategy.let(updateStrategyAdapter::decode)
            this.cover_last_modified = coverLastModified
            this.read = readCount.roundToInt()
            this.unread = maxOf((total - readCount).roundToInt(), 0)
            this.totalChapters = total.toInt()
            this.bookmarkCount = bookmarkCount.roundToInt()
            this.category = categoryId
            this.latestUpdate = latestUpdate
            this.lastRead = lastRead
            this.lastFetch = lastFetch
        }
    }
}
