package eu.kanade.tachiyomi.ui.library

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R

/**
 * Placeholder item to indicate if the category is hidden or empty/filtered out.
 */
class LibraryPlaceholderItem (
    val category: Int,
    val type: Type,
    header: LibraryHeaderItem,
    context: Context?,
) : LibraryItem(header, context) {

    override fun getLayoutRes(): Int = R.layout.manga_list_item

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): LibraryHolder {
        return LibraryListHolder(view, adapter as LibraryCategoryAdapter)
    }

    override fun filter(constraint: String): Boolean {
        filter = constraint

        if (type !is Type.Hidden || type.title.isBlank()) return constraint.isEmpty()

        return type.title.contains(constraint, true)
    }

    override fun equals(other: Any?): Boolean {
        if (other is LibraryPlaceholderItem) {
            return category == other.category
        }
        return false
    }

    override fun hashCode(): Int {
        return 31 * Long.MIN_VALUE.hashCode() + header!!.hashCode()
    }

    sealed class Type {
        data class Hidden(val title: String, val hiddenItems: List<LibraryMangaItem>) : Type()
        data class Blank(val mangaCount: Int) : Type()
    }

    companion object {
        fun hidden(category: Int, header: LibraryHeaderItem, context: Context?, title: String, hiddenItems: List<LibraryMangaItem>) =
            LibraryPlaceholderItem(category, Type.Hidden(title, hiddenItems), header, context)

        fun blank(category: Int, header: LibraryHeaderItem, context: Context?, mangaCount: Int = 0) =
            LibraryPlaceholderItem(category, Type.Blank(mangaCount), header, context)
    }
}
