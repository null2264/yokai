package eu.kanade.tachiyomi.ui.category

import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.ui.library.LibrarySort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.injectLazy
import yokai.domain.category.interactor.DeleteCategories
import yokai.domain.category.interactor.GetCategories
import yokai.domain.category.interactor.InsertCategories
import yokai.domain.category.interactor.UpdateCategories
import yokai.domain.category.models.CategoryUpdate
import yokai.i18n.MR
import yokai.util.lang.getString

/**
 * Presenter of [CategoryController]. Used to manage the categories of the library.
 */
class CategoryPresenter(
    private val controller: CategoryController,
) {
    private val deleteCategories: DeleteCategories by injectLazy()
    private val getCategories: GetCategories by injectLazy()
    private val insertCategories: InsertCategories by injectLazy()
    private val updateCategories: UpdateCategories by injectLazy()

    private var scope = CoroutineScope(Job() + Dispatchers.Default)

    /**
     * List containing categories.
     */
    private var categories: MutableList<Category> = mutableListOf()

    /**
     * Called when the presenter is created.
     */
    fun getCategories() {
        if (categories.isNotEmpty()) {
            controller.setCategories(categories.map(::CategoryItem))
        }
        scope.launch(Dispatchers.IO) {
            categories.clear()
            categories.add(newCategory())
            categories.addAll(getCategories.await())
            val catItems = categories.map(::CategoryItem)
            withContext(Dispatchers.Main) {
                controller.setCategories(catItems)
            }
        }
    }

    private fun newCategory(): Category {
        val default =
            Category.create(controller.view?.context?.getString(MR.strings.create_new_category) ?: "")
        default.order = CREATE_CATEGORY_ORDER
        default.id = Long.MIN_VALUE
        return default
    }

    /**
     * Creates and adds a new category to the database.
     *
     * @param name The name of the category to create.
     */
    fun createCategory(name: String): Boolean {
        // Do not allow duplicate categories.
        if (categoryExists(name, null)) {
            controller.onCategoryExistsError()
            return false
        }

        // Create category.
        val cat = Category.create(name)

        // Set the new item in the last position.
        cat.order = (categories.maxOfOrNull { it.order } ?: 0) + 1

        // Insert into database.
        cat.mangaSort = LibrarySort.Title.categoryValue
        // FIXME: Don't do blocking
        runBlocking { insertCategories.awaitOne(cat) }
        val cats = runBlocking { getCategories.await() }
        val newCat = cats.find { it.name == name } ?: return false
        categories.add(1, newCat)
        reorderCategories(categories)
        return true
    }

    /**
     * Deletes the given categories from the database.
     *
     * @param category The category to delete.
     */
    fun deleteCategory(category: Category?) {
        val safeCategory = category?.id ?: return
        scope.launch {
            deleteCategories.awaitOne(safeCategory.toLong())
            categories.remove(category)
            withContext(Dispatchers.Main) {
                controller.setCategories(categories.map(::CategoryItem))
            }
        }
    }

    /**
     * Reorders the given categories in the database.
     *
     * @param categories The list of categories to reorder.
     */
    fun reorderCategories(categories: List<Category>) {
        scope.launch {
            val updates: MutableList<CategoryUpdate> = mutableListOf()
            categories
                .filter { it.order != CREATE_CATEGORY_ORDER }
                .forEachIndexed { i, category ->
                    category.order = i - 1
                    updates.add(
                        CategoryUpdate(
                            id = category.id!!.toLong(),
                            order = category.order.toLong(),
                        )
                    )
                }
            updateCategories.await(updates)
            this@CategoryPresenter.categories = categories.sortedBy { it.order }.toMutableList()
            withContext(Dispatchers.Main) {
                controller.setCategories(this@CategoryPresenter.categories.map(::CategoryItem))
            }
        }
    }

    /**
     * Renames a category.
     *
     * @param category The category to rename.
     * @param name The new name of the category.
     */
    fun renameCategory(category: Category, name: String): Boolean {
        // Do not allow duplicate categories.
        if (categoryExists(name, category.id)) {
            controller.onCategoryExistsError()
            return false
        }
        if (name.isBlank()) {
            return false
        }

        category.name = name
        runBlocking {
            updateCategories.awaitOne(
                CategoryUpdate(
                    id = category.id!!.toLong(),
                    name = category.name,
                )
            )
        }
        categories.find { it.id == category.id }?.name = name
        controller.setCategories(categories.map(::CategoryItem))
        return true
    }

    /**
     * Returns true if a category with the given name already exists.
     */
    private fun categoryExists(name: String, id: Long?): Boolean {
        return categories.any { it.name.equals(name, true) && id != it.id }
    }

    companion object {
        const val CREATE_CATEGORY_ORDER = -2
    }
}
