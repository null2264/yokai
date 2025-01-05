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
import eu.davidea.flexibleadapter.items.AbstractSectionableItem
import eu.davidea.flexibleadapter.items.IFilterable
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.seriesType
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.MangaGridItemBinding
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.view.compatToolTipText
import eu.kanade.tachiyomi.widget.AutofitRecyclerView
import uy.kohesive.injekt.injectLazy
import yokai.domain.ui.UiPreferences

class LibraryItem(
    val manga: LibraryManga,
    header: LibraryHeaderItem,
    private val context: Context?,
) : AbstractSectionableItem<LibraryHolder, LibraryHeaderItem>(header), IFilterable<String> {

    var downloadCount = -1
    var unreadType = 2
    var sourceLanguage: String? = null
    var filter = ""

    private val sourceManager: SourceManager by injectLazy()
    private val uiPreferences: UiPreferences by injectLazy()
    private val preferences: PreferencesHelper by injectLazy()

    private val uniformSize: Boolean
        get() = uiPreferences.uniformGrid().get()

    private val libraryLayout: Int
        get() = preferences.libraryLayout().get()

    val hideReadingButton: Boolean
        get() = preferences.hideStartReadingButton().get()

    override fun getLayoutRes(): Int {
        return if (libraryLayout == LAYOUT_LIST || manga.isPlaceholder()) {
            R.layout.manga_list_item
        } else {
            R.layout.manga_grid_item
        }
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>): LibraryHolder {
        val parent = adapter.recyclerView
        return if (parent is AutofitRecyclerView) {
            val libraryLayout = libraryLayout
            val isFixedSize = uniformSize
            if (libraryLayout == LAYOUT_LIST || manga.isPlaceholder()) {
                LibraryListHolder(view, adapter as LibraryCategoryAdapter)
            } else {
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
                gridHolder
            }
        } else {
            LibraryListHolder(view, adapter as LibraryCategoryAdapter)
        }
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
        holder.onSetValues(this)
        (holder as? LibraryGridHolder)?.setSelected(adapter.isSelected(position))
        val layoutParams = holder.itemView.layoutParams as? StaggeredGridLayoutManager.LayoutParams
        layoutParams?.isFullSpan = manga.isPlaceholder()
        if (libraryLayout == LAYOUT_COVER_ONLY_GRID) {
            holder.itemView.compatToolTipText = manga.title
        }
    }

    /**
     * Returns true if this item is draggable.
     */
    override fun isDraggable(): Boolean {
        return !manga.isPlaceholder() && header.category.isDragAndDrop
    }

    override fun isEnabled(): Boolean {
        return !manga.isPlaceholder()
    }

    override fun isSelectable(): Boolean {
        return !manga.isPlaceholder()
    }

    /**
     * Filters a manga depending on a query.
     *
     * @param constraint the query to apply.
     * @return true if the manga should be included, false otherwise.
     */
    override fun filter(constraint: String): Boolean {
        filter = constraint
        if (manga.isPlaceholder() && manga.title.isBlank()) {
            return constraint.isEmpty()
        }
        val titleCheck =  manga.title.contains(constraint, true)
        if (manga.isPlaceholder())
            return titleCheck

        val sourceName by lazy { sourceManager.getOrStub(manga.manga.source).name }
        return manga.title.contains(constraint, true) ||
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
        if (manga.isPlaceholder()) return false

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
        if (other is LibraryItem) {
            return manga.requireId() == other.manga.requireId() && manga.category == other.manga.category
        }
        return false
    }

    override fun hashCode(): Int {
        return 31 * manga.requireId().hashCode() + header!!.hashCode()
    }

    companion object {
        const val LAYOUT_LIST = 0
        const val LAYOUT_COMPACT_GRID = 1
        const val LAYOUT_COMFORTABLE_GRID = 2
        const val LAYOUT_COVER_ONLY_GRID = 3
    }
}
