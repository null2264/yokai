package yokai.domain.manga

import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.domain.manga.models.Manga
import kotlinx.coroutines.flow.Flow
import yokai.domain.manga.models.MangaUpdate

interface MangaRepository {
    suspend fun getMangaList(): List<Manga>
    suspend fun getMangaByUrlAndSource(url: String, source: Long): Manga?
    fun getMangaByUrlAndSourceAsFlow(url: String, source: Long): Flow<Manga?>
    suspend fun getMangaById(id: Long): Manga?
    suspend fun getRecommendationCandidates(source: Long, excludedUrl: String, limit: Long = 200): List<Manga>
    suspend fun getFavorites(): List<Manga>
    suspend fun getReadNotFavorites(): List<Manga>
    suspend fun getDuplicateFavorite(title: String, source: Long): Manga?
    fun getMangaListAsFlow(): Flow<List<Manga>>
    suspend fun getLibraryManga(): List<LibraryManga>
    fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>>
    suspend fun update(update: MangaUpdate): Boolean
    suspend fun updateAll(updates: List<MangaUpdate>): Boolean
    suspend fun insert(manga: Manga): Long?
    suspend fun setCategories(mangaId: Long, categoryIds: List<Long>)
    suspend fun setMultipleMangaCategories(mangaIds: List<Long>, mangaCategories: List<MangaCategory>)
}
