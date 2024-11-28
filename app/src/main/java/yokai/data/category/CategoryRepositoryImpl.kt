package yokai.data.category

import eu.kanade.tachiyomi.data.database.models.Category
import kotlinx.coroutines.flow.Flow
import yokai.data.DatabaseHandler
import yokai.domain.category.CategoryRepository

class CategoryRepositoryImpl(private val handler: DatabaseHandler) : CategoryRepository {
    override suspend fun getAll(): List<Category> =
        handler.awaitList { categoriesQueries.findAll(Category::mapper) }

    override suspend fun getAllByMangaId(mangaId: Long): List<Category> =
        handler.awaitList { categoriesQueries.findAllByMangaId(mangaId, Category::mapper) }

    override fun getAllAsFlow(): Flow<List<Category>> =
        handler.subscribeToList { categoriesQueries.findAll(Category::mapper) }
}
