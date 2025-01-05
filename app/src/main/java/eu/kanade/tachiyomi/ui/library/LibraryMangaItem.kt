package eu.kanade.tachiyomi.ui.library

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.seriesType
import eu.kanade.tachiyomi.databinding.MangaGridItemBinding
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.view.compatToolTipText
import eu.kanade.tachiyomi.widget.AutofitRecyclerView

class LibraryMangaItem(
    val manga: LibraryManga,
    header: LibraryHeaderItem,
    context: Context?,
) : LibraryItem(header, context) {

    var downloadCount = -1
    var unreadType = 2
    var sourceLanguage: String? = null

    override fun getLayoutRes(): Int {
        return if (libraryLayout == LAYOUT_LIST) {
            R.layout.manga_list_item
        } else {
            R.layout.manga_grid_item
        }
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): LibraryHolder {
        val listHolder by lazy { LibraryListHolder(view, adapter as LibraryCategoryAdapter) }
        val parent = adapter.recyclerView
        if (parent !is AutofitRecyclerView) return listHolder

        val libraryLayout = libraryLayout
        val isFixedSize = uniformSize

        if (libraryLayout == LAYOUT_LIST) { return listHolder }

        view.apply {
            val isStaggered = parent.layoutManager is StaggeredGridLayoutManager
            val binding = MangaGridItemBinding.bind(this)
            binding.behindTitle.isVisible = libraryLayout == LAYOUT_COVER_ONLY_GRID
            if (libraryLayout >= LAYOUT_COMFORTABLE_GRID) {
                binding.textLayout.isVisible = libraryLayout == LAYOUT_COMFORTABLE_GRID
                binding.card.setCardForegroundColor(
                    ContextCompat.getColorStateList(
                        context,
                        R.color.library_comfortable_grid_foreground,
                    ),
                )
            }
            if (isFixedSize) {
                binding.constraintLayout.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                binding.coverThumbnail.maxHeight = Int.MAX_VALUE
                binding.coverThumbnail.minimumHeight = 0
                binding.constraintLayout.minHeight = 0
                binding.coverThumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
                binding.coverThumbnail.adjustViewBounds = false
                binding.coverThumbnail.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    height = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
                    dimensionRatio = "2:3"
                }
            }
            if (libraryLayout != LAYOUT_COMFORTABLE_GRID) {
                binding.card.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    bottomMargin = (if (isStaggered) 2 else 6).dpToPx
                }
            }
            binding.setBGAndFG(libraryLayout)
        }
        val gridHolder = LibraryGridHolder(
            view,
            adapter as LibraryCategoryAdapter,
            libraryLayout == LAYOUT_COMPACT_GRID,
            isFixedSize,
        )
        if (!isFixedSize) {
            gridHolder.setFreeformCoverRatio(manga.manga, parent)
        }
        return gridHolder
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: LibraryHolder,
        position: Int,
        payloads: MutableList<Any?>?,
    ) {
        if (holder is LibraryGridHolder && !holder.fixedSize) {
            holder.setFreeformCoverRatio(manga.manga, adapter.recyclerView as? AutofitRecyclerView)
        }
        super.bindViewHolder(adapter, holder, position, payloads)
        if (libraryLayout == LAYOUT_COVER_ONLY_GRID) {
            holder.itemView.compatToolTipText = manga.manga.title
        }
    }

    /**
     * Returns true if this item is draggable.
     */
    override fun isDraggable(): Boolean {
        return header.category.isDragAndDrop
    }

    override fun isEnabled(): Boolean {
        return true
    }

    override fun isSelectable(): Boolean {
        return true
    }

    /**
     * Filters a manga depending on a query.
     *
     * @param constraint the query to apply.
     * @return true if the manga should be included, false otherwise.
     */
    override fun filter(constraint: String): Boolean {
        filter = constraint
        if (manga.manga.title.isBlank()) {
            return constraint.isEmpty()
        }
        val sourceName by lazy { sourceManager.getOrStub(manga.manga.source).name }
        return manga.manga.title.contains(constraint, true) ||
            (manga.manga.author?.contains(constraint, true) ?: false) ||
            (manga.manga.artist?.contains(constraint, true) ?: false) ||
            sourceName.contains(constraint, true) ||
            if (constraint.contains(",")) {
                val genres = manga.manga.genre?.split(", ")
                constraint.split(",").all { containsGenre(it.trim(), genres) }
            } else {
                containsGenre(constraint, manga.manga.genre?.split(", "))
            }
    }

    private fun containsGenre(tag: String, genres: List<String>?): Boolean {
        if (tag.trim().isEmpty()) return true
        context ?: return false

        val seriesType by lazy { manga.manga.seriesType(context, sourceManager) }
        return if (tag.startsWith("-")) {
            val realTag = tag.substringAfter("-")
            genres?.find {
                it.trim().equals(realTag, ignoreCase = true) || seriesType.equals(realTag, true)
            } == null
        } else {
            genres?.find {
                it.trim().equals(tag, ignoreCase = true) || seriesType.equals(tag, true)
            } != null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other is LibraryMangaItem) {
            return manga.manga.id == other.manga.manga.id && manga.category == other.manga.category
        }
        return false
    }

    override fun hashCode(): Int {
        return 31 * manga.manga.id.hashCode() + header!!.hashCode()
    }
}
