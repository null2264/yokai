package yokai.data.category

import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.database.models.Category
import kotlinx.coroutines.flow.Flow
import yokai.data.DatabaseHandler
import yokai.domain.category.CategoryRepository
import yokai.domain.category.models.CategoryUpdate

class CategoryRepositoryImpl(private val handler: DatabaseHandler) : CategoryRepository {
    override suspend fun getAll(): List<Category> =
        handler.awaitList { categoriesQueries.findAll(Category::mapper) }

    override suspend fun getAllByMangaId(mangaId: Long): List<Category> =
        handler.awaitList { categoriesQueries.findAllByMangaId(mangaId, Category::mapper) }

    override fun getAllAsFlow(): Flow<List<Category>> =
        handler.subscribeToList { categoriesQueries.findAll(Category::mapper) }

    override suspend fun insert(category: Category): Long? =
        handler.awaitOneOrNullExecutable {
            categoriesQueries.insert(
                name = category.name,
                mangaOrder = category.mangaOrderToString(),
                sort = category.order.toLong(),
                flags = category.flags.toLong(),
            )
            categoriesQueries.selectLastInsertedRowId()
        }

    override suspend fun insertBulk(categories: List<Category>) =
        handler.await(true) {
            categories.forEach { category ->
                categoriesQueries.insert(
                    name = category.name,
                    mangaOrder = category.mangaOrderToString(),
                    sort = category.order.toLong(),
                    flags = category.flags.toLong(),
                )
            }
        }

    override suspend fun update(update: CategoryUpdate): Boolean {
        return try {
            partialUpdate(update)
            true
        } catch (e: Exception) {
            Logger.e { "Failed to update category with id '${update.id}'" }
            false
        }
    }

    override suspend fun updateAll(updates: List<CategoryUpdate>): Boolean {
        return try {
            partialUpdate(*updates.toTypedArray())
            true
        } catch (e: Exception) {
            Logger.e(e) { "Failed to bulk update categories" }
            false
        }
    }

    private suspend fun partialUpdate(vararg updates: CategoryUpdate) {
        handler.await(inTransaction = true) {
            updates.forEach { update ->
                categoriesQueries.update(
                    id = update.id,
                    name = update.name,
                    mangaOrder = update.mangaOrder,
                    sort = update.order,
                    flags = update.flags,
                )
            }
        }
    }

    override suspend fun delete(id: Long) {
        handler.await { categoriesQueries.delete(id) }
    }
}
