package eu.kanade.tachiyomi.ui.library

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import co.touchlab.kermit.Logger
import com.github.florent37.viewtooltip.ViewTooltip
import eu.davidea.flexibleadapter.SelectableAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.LibraryCategoryHeaderItemBinding
import eu.kanade.tachiyomi.source.icon
import eu.kanade.tachiyomi.ui.base.MaterialMenuSheet
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.system.contextCompatDrawable
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.compatToolTipText
import eu.kanade.tachiyomi.util.view.setText
import eu.kanade.tachiyomi.util.view.text
import kotlin.random.Random
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.library.LibraryPreferences
import yokai.i18n.MR
import yokai.util.lang.getString

class LibraryHeaderHolder(val view: View, val adapter: LibraryCategoryAdapter) :
    BaseFlexibleViewHolder(view, adapter, true) {

    private val libraryPreferences: LibraryPreferences = Injekt.get()
    private val binding = LibraryCategoryHeaderItemBinding.bind(view)
    val progressDrawableStart = CircularProgressDrawable(itemView.context)
    val progressDrawableEnd = CircularProgressDrawable(itemView.context)
    private val runningDrawable = CircularProgressDrawable(itemView.context)
    private val refreshDrawable = itemView.context.contextCompatDrawable(R.drawable.ic_refresh_24dp)
    var locked = false
    private val headerGestureDetector = LibraryHeaderGestureDetector(this, binding)
    val category: Category?
        get() = (adapter.getItem(flexibleAdapterPosition) as? LibraryHeaderItem)?.category

    init {
        binding.categoryHeaderLayout.setOnClickListener {
            if (!locked) toggleCategory()
            locked = false
        }
        binding.updateButton.setOnClickListener { addCategoryToUpdate() }
        binding.categoryTitle.setOnLongClickListener { manageCategory() }
        binding.categoryTitle.setOnClickListener {
            if (category?.isHidden == false && adapter.mode == SelectableAdapter.Mode.MULTI) {
                selectAll()
            } else {
                toggleCategory()
            }
        }
        binding.categorySort.setOnClickListener { it.post { showCatSortOptions() } }
        binding.categorySort.compatToolTipText = view.context.getString(MR.strings.sort)
        binding.checkbox.setOnClickListener { selectAll() }

        runningDrawable.setStyle(CircularProgressDrawable.DEFAULT)
        runningDrawable.centerRadius = 6f.dpToPx
        runningDrawable.strokeWidth = 2f.dpToPx
        runningDrawable.setColorSchemeColors(itemView.context.getResourceColor(R.attr.colorSecondary))

        binding.endRefresh.setImageDrawable(progressDrawableEnd)
        binding.startRefresh.setImageDrawable(progressDrawableStart)
        binding.startRefresh.scaleX = -1f
        listOf(progressDrawableStart, progressDrawableEnd).forEach {
            it.setColorSchemeColors(itemView.context.getResourceColor(R.attr.colorOnSecondary))
            it.centerRadius = 1f
            it.arrowEnabled = true
            it.setStyle(CircularProgressDrawable.DEFAULT)
        }
        binding.setTouchEvents()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun LibraryCategoryHeaderItemBinding.setTouchEvents() {
        val gestureDetector = GestureDetector(root.context, headerGestureDetector)
        listOf(categoryHeaderLayout, categorySort, categoryTitle, updateButton).forEach {
            var isCancelling = false
            it.setOnTouchListener { _, event ->
                if (event?.action == MotionEvent.ACTION_DOWN) {
                    locked = false
                }
                when (event?.action) {
                    MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                        try {
                            itemView.parent.requestDisallowInterceptTouchEvent(false)
                        } catch (e: NullPointerException) {
                            Logger.e(e) { "Failed to request disallow intercept touch event" }
                            return@setOnTouchListener false
                        }

                        if (isCancelling) {
                            isCancelling = false
                            return@setOnTouchListener false
                        }

                        val result = gestureDetector.onTouchEvent(event)
                        if (!result) {
                            val anim = binding.categoryHeaderLayout.animate().setDuration(150L)
                                .translationX(0f)
                            anim.withEndAction { rearView.isVisible = true }
                            anim.start()
                            if (headerGestureDetector.vibrated) {
                                addCategoryToUpdate()
                            }
                        } else {
                            isCancelling = true
                            val ev2 = MotionEvent.obtain(event)
                            ev2.action = MotionEvent.ACTION_CANCEL
                            it.dispatchTouchEvent(ev2)
                            ev2.recycle()
                        }
                        result
                    }
                    else -> {
                        gestureDetector.onTouchEvent(event)
                    }
                }
            }
        }
    }

    private fun toggleCategory() {
        adapter.libraryListener?.toggleCategoryVisibility(flexibleAdapterPosition)
        val tutorial = Injekt.get<PreferencesHelper>().shownLongPressCategoryTutorial()
        if (!tutorial.get()) {
            ViewTooltip.on(itemView.context as? Activity, binding.categoryTitle).autoHide(true, 5000L)
                .align(ViewTooltip.ALIGN.START).position(ViewTooltip.Position.TOP)
                .text(MR.strings.long_press_category)
                .color(itemView.context.getResourceColor(R.attr.colorSecondary))
                .textSize(TypedValue.COMPLEX_UNIT_SP, 15f).textColor(Color.WHITE)
                .withShadow(false).corner(30).arrowWidth(15).arrowHeight(15).distanceWithView(0)
                .show()
            tutorial.set(true)
        }
    }

    @SuppressLint("SetTextI18n")
    fun bind(item: LibraryHeaderItem) {
        val index = adapter.headerItems.indexOf(item)
        val previousIsCollapsed =
            if (index > 0) {
                (adapter.headerItems[index - 1] as? LibraryHeaderItem)?.category?.isHidden
                    ?: false
            } else {
                false
            }
        val shorterMargin = adapter.headerItems.firstOrNull() == item
        binding.categoryTitle.updateLayoutParams<ConstraintLayout.LayoutParams> {
            topMargin = (
                when {
                    shorterMargin -> 2
                    previousIsCollapsed -> 5
                    else -> 32
                }
                ).dpToPx
        }
        binding.rearView.updatePadding(top = binding.categoryTitle.marginTop - 6)
        val category = item.category

        val isFilteredList = (adapter.libraryListener as? FilteredLibraryController)?.let {
            it.filterCategories.size == 1 && it.getTitle() == category.name
        } ?: false
        val categoryName = if ((category.isAlone || isFilteredList) && !category.isDynamic) {
            ""
        } else {
            category.name
        }

        binding.categoryTitle.text = categoryName +
            if (adapter.showNumber) {
                val filteredCount = adapter.currentItems.count {
                    it is LibraryMangaItem && it.header?.catId == item.catId
                }
                val totalCount = adapter.itemsPerCategory[item.catId] ?: 0
                val searchText = adapter.getFilter(String::class.java)
                var countText = if (searchText.isNullOrBlank()) {
                    " ($totalCount)"
                } else {
                    " ($filteredCount/$totalCount)"
                }
                countText
            } else { "" }
        if (category.sourceId != null) {
            val icon = adapter.sourceManager.get(category.sourceId!!)?.icon()
            icon?.setBounds(0, 0, 32.dpToPx, 32.dpToPx)
            binding.categoryTitle.setCompoundDrawablesRelative(icon, null, null, null)
        } else if (category.langId != null) {
            val icon = getFlagIcon(category.langId!!) ?: 0
            binding.categoryTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, icon, 0)
        } else {
            binding.categoryTitle.setCompoundDrawablesRelative(null, null, null, null)
        }

        val isAscending = category.isAscending()
        val sortingMode = category.sortingMode()
        val sortDrawable = getSortRes(sortingMode, isAscending, category.isDynamic, false)

        binding.categorySort.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, sortDrawable, 0)
        binding.categorySort.setText(category.sortRes())
        binding.collapseArrow.setImageResource(
            if (category.isHidden) {
                R.drawable.ic_expand_more_24dp
            } else {
                R.drawable.ic_expand_less_24dp
            },
        )
        notifyStatus(LibraryUpdateJob.categoryInQueue(category.id), category)
    }

    @SuppressLint("DiscouragedApi")
    fun getFlagIcon(lang: String): Int? {
        val flagId = itemView.resources.getIdentifier(
            "ic_flag_${lang.replace("-", "_")}",
            "drawable",
            itemView.context.packageName,
        ).takeIf { it != 0 } ?: (
            if (lang.contains("-")) {
                itemView.resources.getIdentifier(
                    "ic_flag_${lang.split("-").first()}",
                    "drawable",
                    itemView.context.packageName,
                ).takeIf { it != 0 }
            } else {
                null
            }
            )
        return flagId
    }

    fun setRefreshing(refreshing: Boolean) {
        binding.updateButton.isClickable = !refreshing
        if (refreshing) {
            if (!runningDrawable.isRunning) {
                binding.updateButton.setImageDrawable(runningDrawable)
                runningDrawable.arrowEnabled = false
                runningDrawable.start()
            }
        } else {
            runningDrawable.stop()
            runningDrawable.setStartEndTrim(0f, 0.8f)
            runningDrawable.arrowEnabled = true
            binding.updateButton.setImageDrawable(refreshDrawable)
        }
    }

    fun addCategoryToUpdate() {
        if (adapter.libraryListener?.updateCategory(flexibleAdapterPosition) == true) {
            setRefreshing(true)
        }
    }

    fun manageCategory(): Boolean {
        adapter.libraryListener?.manageCategory(flexibleAdapterPosition)
        return category?.isDynamic == false
    }

    private fun showCatSortOptions() {
        val cat = category ?: return
        adapter.controller?.activity?.let { activity ->
            val items = LibrarySort.entries.map { it.menuSheetItem(cat.isDynamic) }
            val sortingMode = cat.sortingMode() ?: if (!cat.isDynamic) LibrarySort.DragAndDrop else null
            val sheet = MaterialMenuSheet(
                activity,
                items,
                activity.getString(MR.strings.sort_by),
                sortingMode?.mainValue,
            ) { sheet, item ->
                onCatSortClicked(cat, item)
                val nCategory = (adapter.getItem(flexibleAdapterPosition) as? LibraryHeaderItem)?.category
                sheet.updateSortIcon(nCategory, LibrarySort.valueOf(item))
                false
            }
            sheet.updateSortIcon(cat, sortingMode)
            sheet.show()
        }
    }

    private fun MaterialMenuSheet.updateSortIcon(category: Category?, sortingMode: LibrarySort?) {
        val isAscending = category?.isAscending() ?: false
        val drawableRes = getSortRes(sortingMode, isAscending, category?.isDynamic ?: false, true)
        this.setDrawable(sortingMode?.mainValue ?: -1, drawableRes)
    }

    private fun getSortRes(
        sortMode: LibrarySort?,
        isAscending: Boolean,
        isDynamic: Boolean,
        onSelection: Boolean,
        @DrawableRes defaultDrawableRes: Int = R.drawable.ic_sort_24dp,
        @DrawableRes defaultSelectedDrawableRes: Int = R.drawable.ic_check_24dp,
    ): Int {
        sortMode ?: return if (onSelection) defaultSelectedDrawableRes else defaultDrawableRes

        if (sortMode.isDirectional) {
            return if (if (sortMode.hasInvertedSort) !isAscending else isAscending) {
                R.drawable.ic_arrow_downward_24dp
            } else {
                R.drawable.ic_arrow_upward_24dp
            }
        }

        if (onSelection) {
            return when(sortMode) {
                LibrarySort.DragAndDrop -> R.drawable.ic_check_24dp
                LibrarySort.Random -> R.drawable.ic_refresh_24dp
                else -> defaultSelectedDrawableRes
            }
        }

        return sortMode.iconRes(isDynamic)
    }

    private fun onCatSortClicked(category: Category, menuId: Int?) {
        val (mode, modType) = if (menuId == null) {
            val sortingMode = category.sortingMode() ?: LibrarySort.Title
            sortingMode to
                if (sortingMode != LibrarySort.Random && category.isAscending()) {
                    sortingMode.categoryValueDescending
                } else {
                    sortingMode.categoryValue
                }
        } else {
            val sortingMode = LibrarySort.valueOf(menuId) ?: LibrarySort.Title
            if (sortingMode != LibrarySort.DragAndDrop && sortingMode == category.sortingMode()) {
                onCatSortClicked(category, null)
                return
            }
            sortingMode to sortingMode.categoryValue
        }
        if (mode == LibrarySort.Random) {
            libraryPreferences.randomSortSeed().set(Random.nextInt())
        }
        adapter.libraryListener?.sortCategory(category.id!!, modType)
    }

    private fun selectAll() {
        adapter.libraryListener?.selectAll(flexibleAdapterPosition)
    }

    fun setSelection() {
        val allSelected = adapter.libraryListener?.allSelected(flexibleAdapterPosition) == true
        val drawable = ContextCompat.getDrawable(
            contentView.context,
            if (allSelected) R.drawable.ic_check_circle_24dp else R.drawable.ic_radio_button_unchecked_24dp,
        )
        val tintedDrawable = drawable?.mutate()
        tintedDrawable?.setTint(
            if (allSelected) {
                contentView.context.getResourceColor(R.attr.colorSecondary)
            } else {
                ContextCompat.getColor(contentView.context, R.color.gray_button)
            },
        )
        binding.checkbox.setImageDrawable(tintedDrawable)
    }

    override fun onLongClick(view: View?): Boolean {
        super.onLongClick(view)
        return false
    }

    fun notifyStatus(isReloading: Boolean, category: Category) {
        when {
            adapter.mode == SelectableAdapter.Mode.MULTI -> {
                binding.checkbox.isVisible = !category.isHidden
                binding.collapseArrow.isVisible = category.isHidden && !adapter.isSingleCategory
                binding.updateButton.isVisible = false
                setSelection()
            }
            (category.id ?: -1) < 0 || adapter.libraryListener is FilteredLibraryController -> {
                binding.collapseArrow.isVisible = false
                binding.checkbox.isVisible = false
                setRefreshing(false)
                binding.updateButton.isVisible = false
            }
            isReloading -> {
                binding.collapseArrow.isVisible = !adapter.isSingleCategory
                binding.checkbox.isVisible = false
                binding.updateButton.isVisible = true
                setRefreshing(true)
            }
            else -> {
                binding.collapseArrow.isVisible = !adapter.isSingleCategory
                binding.checkbox.isVisible = false
                setRefreshing(false)
                binding.updateButton.isVisible = !adapter.isSingleCategory
            }
        }
    }
}