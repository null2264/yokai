package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.ui.library.LibraryItem
import kotlin.math.roundToInt

// FIXME: Separate placeholders from LibraryManga
data class LibraryManga(
    private val _manga: Manga?,
    private val _blank: Boolean,
    private val _hidden: Boolean,
    private val _mangaCount: Int = 0,
    private val _items: List<LibraryItem>? = null,
    private val _title: String? = null,
    var unread: Int = 0,
    var read: Int = 0,
    var category: Int = 0,
    var bookmarkCount: Int = 0,
    var totalChapters: Int = 0,
    var latestUpdate: Long = 0,
    var lastRead: Long = 0,
    var lastFetch: Long = 0,
) {
    val manga
        get() = if (!_blank && !_hidden) _manga!! else throw IllegalStateException("manga is not accessible by placeholders")

    fun isPlaceholder() = _blank || _hidden
    fun isBlank() = _blank
    fun isHidden() = _hidden

    val realMangaCount
        get() = if (_blank) _mangaCount else throw IllegalStateException("realMangaCount is only accessible by placeholders")

    val hasRead
        get() = read > 0

    val items: List<LibraryItem>
        get() = if (_hidden) _items ?: emptyList() else throw IllegalStateException("items only accessible by placeholders")

    val title: String
        get() {
            return when {
                _hidden -> _title!!
                _blank -> ""
                else -> manga.title
            }
        }

    fun requireId() = if (isPlaceholder()) Long.MIN_VALUE else manga.id!!

    companion object {
        /**
         * To show empty category state
         */
        fun createBlank(categoryId: Int, mangaCount: Int = 0): LibraryManga = LibraryManga(
            _manga = null,
            _blank = true,
            _hidden = false,
            _mangaCount = mangaCount,
            category = categoryId,
        )

        fun createHide(categoryId: Int, title: String, hiddenItems: List<LibraryItem>): LibraryManga =
            LibraryManga(
                _manga = null,
                _blank = false,
                _hidden = true,
                _items = hiddenItems,
                _title = title,
                category = categoryId,
                read = hiddenItems.size,
            )

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
        ): LibraryManga = LibraryManga(
            _manga = Manga.mapper(
                id = id,
                source = source,
                url = url,
                artist = artist,
                author = author,
                description = description,
                genre = genre,
                title = title,
                status = status,
                thumbnailUrl = thumbnailUrl,
                favorite = favorite,
                lastUpdate = lastUpdate,
                initialized = initialized,
                viewerFlags = viewerFlags,
                hideTitle = hideTitle,
                chapterFlags = chapterFlags,
                dateAdded = dateAdded,
                filteredScanlators = filteredScanlators,
                updateStrategy = updateStrategy,
                coverLastModified = coverLastModified,
            ),
            _blank = false,
            _hidden = false,
            read = readCount.roundToInt(),
            unread = maxOf((total - readCount).roundToInt(), 0),
            totalChapters = total.toInt(),
            bookmarkCount = bookmarkCount.roundToInt(),
            category = categoryId.toInt(),
            latestUpdate = latestUpdate,
            lastRead = lastRead,
            lastFetch = lastFetch,
        )
    }
}
