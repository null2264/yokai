package yokai.data.manga

import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.database.models.mapper
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.model.encodeMemo
import eu.kanade.tachiyomi.source.model.safeMemo
import kotlinx.coroutines.flow.Flow
import yokai.data.DatabaseHandler
import yokai.data.updateStrategyAdapter
import yokai.domain.manga.MangaRepository
import yokai.domain.manga.models.MangaUpdate

class MangaRepositoryImpl(private val handler: DatabaseHandler) : MangaRepository {
    override suspend fun getMangaList(): List<Manga> =
        handler.awaitList { mangasQueries.findAll(Manga::mapper) }

    override suspend fun getMangaByUrlAndSource(url: String, source: Long): Manga? =
        handler.awaitFirstOrNull { mangasQueries.findByUrlAndSource(url, source, Manga::mapper) }

    override suspend fun getMangaById(id: Long): Manga? =
        handler.awaitOneOrNull { mangasQueries.findById(id, Manga::mapper) }

    override suspend fun getFavorites(): List<Manga> =
        handler.awaitList { mangasQueries.findFavorites(Manga::mapper) }

    override suspend fun getReadNotFavorites(): List<Manga> =
        handler.awaitList { mangasQueries.findReadNotFavorites(Manga::mapper) }

    override fun getMangaListAsFlow(): Flow<List<Manga>> =
        handler.subscribeToList { mangasQueries.findAll(Manga::mapper) }

    override fun getMangaByUrlAndSourceAsFlow(url: String, source: Long): Flow<Manga?> =
        handler.subscribeToFirstOrNull { mangasQueries.findByUrlAndSource(url, source, Manga::mapper) }

    override suspend fun getLibraryManga(): List<LibraryManga> =
        handler.awaitList { library_viewQueries.findAll(LibraryManga::mapper) }

    // Uses findAllAsFlow which excludes description to avoid CursorWindow overflow
    // (2MB limit) when the flow re-emits frequently due to background downloads.
    override fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>> =
        handler.subscribeToList { library_viewQueries.findAllAsFlow(LibraryManga::flowMapper) }

    override suspend fun getDuplicateFavorite(title: String, source: Long): Manga? =
        handler.awaitFirstOrNull { mangasQueries.findDuplicateFavorite(title.lowercase(), source, Manga::mapper) }

    override suspend fun update(update: MangaUpdate): Boolean {
        return try {
            partialUpdate(update)
            true
        } catch (e: Exception) {
            Logger.e(e) { "Failed to update manga with id '${update.id}'" }
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
                    memo = update.memo,
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
                memo = manga.safeMemo().encodeMemo(),
            )
            mangasQueries.selectLastInsertedRowId()
        }

    override suspend fun setCategories(mangaId: Long, categoryIds: List<Long>) =
        handler.await(inTransaction = true) {
            mangas_categoriesQueries.delete(mangaId)
            categoryIds.forEach { id ->
                mangas_categoriesQueries.insert(mangaId, id)
            }
        }

    override suspend fun setMultipleMangaCategories(mangaIds: List<Long>, mangaCategories: List<MangaCategory>) =
        handler.await(inTransaction = true) {
            mangas_categoriesQueries.deleteBulk(mangaIds)
            mangaCategories.forEach {
                mangas_categoriesQueries.insert(it.manga_id, it.category_id.toLong())
            }
        }
}
