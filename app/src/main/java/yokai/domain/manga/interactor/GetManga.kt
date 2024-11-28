package yokai.domain.manga.interactor

import yokai.domain.manga.MangaRepository

class GetManga (
    private val mangaRepository: MangaRepository,
) {
    suspend fun awaitAll() = mangaRepository.getMangaList()
    fun subscribeAll() = mangaRepository.getMangaListAsFlow()

    suspend fun awaitByUrlAndSource(url: String, source: Long) = mangaRepository.getMangaByUrlAndSource(url, source)
    suspend fun awaitById(id: Long) = mangaRepository.getMangaById(id)
    suspend fun awaitFavorites() = mangaRepository.getFavorites()
    suspend fun awaitReadNotFavorites() = mangaRepository.getReadNotFavorites()
    suspend fun awaitDuplicateFavorite(title: String, source: Long) = mangaRepository.getDuplicateFavorite(title, source)
}
