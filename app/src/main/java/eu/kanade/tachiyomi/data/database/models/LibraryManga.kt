package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.domain.manga.models.Manga
import kotlin.math.roundToInt

data class LibraryManga(
    val manga: Manga,
    var unread: Int = 0,
    var read: Int = 0,
    var category: Int = 0,
    var bookmarkCount: Int = 0,
    var totalChapters: Int = 0,
    var latestUpdate: Long = 0,
    var lastRead: Long = 0,
    var lastFetch: Long = 0,
) {
    val hasRead
        get() = read > 0

    companion object {
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
            manga = Manga.mapper(
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
