package yokai.data.manga

import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.mapper
import eu.kanade.tachiyomi.domain.manga.models.Manga
import kotlinx.coroutines.flow.Flow
import yokai.data.DatabaseHandler
import yokai.data.updateStrategyAdapter
import yokai.domain.manga.MangaRepository
import yokai.domain.manga.models.MangaUpdate

class MangaRepositoryImpl(private val handler: DatabaseHandler) : MangaRepository {
    override suspend fun getMangaList(): List<Manga> =
        handler.awaitList { mangasQueries.findAll(Manga::mapper) }

    override suspend fun getMangaByUrlAndSource(url: String, source: Long): Manga? =
        handler.awaitOneOrNull { mangasQueries.findByUrlAndSource(url, source, Manga::mapper) }

    override suspend fun getMangaById(id: Long): Manga? =
        handler.awaitOneOrNull { mangasQueries.findById(id, Manga::mapper) }

    override suspend fun getFavorites(): List<Manga> =
        handler.awaitList { mangasQueries.findFavorites(Manga::mapper) }

    override suspend fun getReadNotFavorites(): List<Manga> =
        handler.awaitList { mangasQueries.findReadNotFavorites(Manga::mapper) }

    override fun getMangaListAsFlow(): Flow<List<Manga>> =
        handler.subscribeToList { mangasQueries.findAll(Manga::mapper) }

    override suspend fun getLibraryManga(): List<LibraryManga> =
        handler.awaitList { library_viewQueries.findAll(LibraryManga::mapper) }

    override fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>> =
        handler.subscribeToList { library_viewQueries.findAll(LibraryManga::mapper) }

    override suspend fun getDuplicateFavorite(title: String, source: Long): Manga? =
        handler.awaitOneOrNull { mangasQueries.findDuplicateFavorite(title.lowercase(), source, Manga::mapper) }

    override suspend fun update(update: MangaUpdate): Boolean {
        return try {
            partialUpdate(update)
            true
        } catch (e: Exception) {
            Logger.e { "Failed to update manga with id '${update.id}'" }
            false
        }
    }

    override suspend fun updateAll(updates: List<MangaUpdate>): Boolean {
        return try {
            partialUpdate(*updates.toTypedArray())
            true
        } catch (e: Exception) {
            Logger.e(e) { "Failed to bulk update manga" }
            false
        }
    }

    private suspend fun partialUpdate(vararg updates: MangaUpdate) {
        handler.await(inTransaction = true) {
            updates.forEach { update ->
                mangasQueries.update(
                    source = update.source,
                    url = update.url,
                    artist = update.artist,
                    author = update.author,
                    description = update.description,
                    genre = update.genres?.joinToString(),
                    title = update.title,
                    status = update.status?.toLong(),
                    thumbnailUrl = update.thumbnailUrl,
                    favorite = update.favorite,
                    lastUpdate = update.lastUpdate,
                    initialized = update.initialized,
                    viewer = update.viewerFlags?.toLong(),
                    hideTitle = update.hideTitle,
                    chapterFlags = update.chapterFlags?.toLong(),
                    dateAdded = update.dateAdded,
                    filteredScanlators = update.filteredScanlators,
                    updateStrategy = update.updateStrategy?.let(updateStrategyAdapter::encode),
                    coverLastModified = update.coverLastModified,
                    mangaId = update.id,
                )
            }
        }
    }

    override suspend fun insert(manga: Manga) =
        handler.awaitOneOrNullExecutable(inTransaction = true) {
            mangasQueries.insert(
                source = manga.source,
                url = manga.url,
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = manga.genre,
                title = manga.title,
                status = manga.status.toLong(),
                thumbnailUrl = manga.thumbnail_url,
                favorite = manga.favorite,
                lastUpdate = manga.last_update,
                initialized = manga.initialized,
                viewer = manga.viewer_flags.toLong(),
                hideTitle = manga.hide_title,
                chapterFlags = manga.chapter_flags.toLong(),
                dateAdded = manga.date_added,
                filteredScanlators = manga.filtered_scanlators,
                updateStrategy = manga.update_strategy.let(updateStrategyAdapter::encode),
                coverLastModified = manga.cover_last_modified,
            )
            mangasQueries.selectLastInsertedRowId()
        }
}
