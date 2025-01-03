package eu.kanade.tachiyomi.data.database.models

import android.content.Context
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.library.LibrarySort
import java.io.Serializable
import yokai.i18n.MR
import yokai.util.lang.getString

interface Category : Serializable {

    var id: Int?

    var name: String

    var order: Int

    var flags: Int

    var mangaOrder: List<Long>

    var mangaSort: Char?

    var isAlone: Boolean

    var isHidden: Boolean

    var isDynamic: Boolean

    var sourceId: Long?

    var langId: String?

    var isSystem: Boolean

    fun isAscending(): Boolean {
        return ((mangaSort?.minus('a') ?: 0) % 2) != 1
    }

    fun sortingMode(nullAsDND: Boolean = false): LibrarySort? = LibrarySort.valueOf(mangaSort)
        ?: if (nullAsDND && !isDynamic) LibrarySort.DragAndDrop else null

    val isDragAndDrop
        get() = (
            mangaSort == null ||
                mangaSort == LibrarySort.DragAndDrop.categoryValue
            ) && !isDynamic

    fun sortRes(): StringResource =
        (LibrarySort.valueOf(mangaSort) ?: LibrarySort.DragAndDrop).stringRes(isDynamic)

    fun changeSortTo(sort: Int) {
        mangaSort = (LibrarySort.valueOf(sort) ?: LibrarySort.Title).categoryValue
    }

    fun mangaOrderToString(): String =
        if (mangaSort != null) mangaSort.toString() else mangaOrder.joinToString("/")

    // For dynamic categories
    fun dynamicHeaderKey(): String {
        if (!isDynamic) throw IllegalStateException("This category is not a dynamic category")

        return when {
            sourceId != null -> "${name}$sourceSplitter${sourceId}"
            langId != null -> "${langId}$langSplitter${name}"
            else -> name
        }
    }

    companion object {
        const val sourceSplitter = "◘•◘"
        const val langSplitter = "⨼⨦⨠"

        var lastCategoriesAddedTo = emptySet<Int>()

        fun create(name: String): Category = CategoryImpl().apply {
            this.name = name
        }

        fun createDefault(context: Context): Category =
            create(context.getString(MR.strings.default_value)).apply {
                id = 0
                isSystem = true
            }

        fun createCustom(name: String, libSort: Int, ascending: Boolean): Category =
            create(name).apply {
                val librarySort = LibrarySort.valueOf(libSort) ?: LibrarySort.DragAndDrop
                changeSortTo(librarySort.mainValue)
                if (mangaSort != LibrarySort.DragAndDrop.categoryValue && !ascending) {
                    mangaSort = mangaSort?.plus(1)
                }
                isDynamic = true
            }

        fun createAll(context: Context, libSort: Int, ascending: Boolean): Category =
            createCustom(context.getString(MR.strings.all), libSort, ascending).apply {
                id = -1
                order = -1
                isAlone = true
                isSystem = true
            }

        fun mangaOrderFromString(orderString: String?): Pair<Char?, List<Long>> {
            return when {
                orderString.isNullOrBlank() -> {
                    Pair('a', emptyList())
                }
                orderString.firstOrNull()?.isLetter() == true -> {
                    Pair(orderString.first(), emptyList())
                }
                else -> Pair(null, orderString.split("/").mapNotNull { it.toLongOrNull() })
            }
        }

        fun mapper(
            id: Long,
            name: String,
            sort: Long,
            flags: Long,
            orderString: String,
        ) = create(name).also {
            it.id = id.toInt()
            it.name = name
            it.order = sort.toInt()
            it.flags = flags.toInt()

            val (mangaSort, order) = mangaOrderFromString(orderString)
            if (mangaSort != null) it.mangaSort = mangaSort
            it.mangaOrder = order
        }
    }
}
