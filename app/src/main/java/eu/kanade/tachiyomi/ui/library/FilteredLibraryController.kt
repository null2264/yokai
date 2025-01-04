package eu.kanade.tachiyomi.ui.library

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.library.filter.FilterBottomSheet
import eu.kanade.tachiyomi.ui.more.stats.details.StatsDetailsController
import eu.kanade.tachiyomi.util.system.contextCompatDrawable
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.view.hide
import eu.kanade.tachiyomi.util.view.previousController

class FilteredLibraryController(bundle: Bundle? = null) : LibraryController(bundle) {

    private var queryText: String? = null
    var filterStatus = emptyArray<Int>()
        private set
    var filterTracked: Int = 0
        private set
    var filterMangaType = emptyArray<Int>()
        private set
    var filterSources = emptyArray<Long>()
        private set
    var filterLanguages = emptyArray<String>()
        private set
    var filterTags = emptyArray<String>()
        private set
    var filterTrackingScore: Int = 0
        private set
    var filterStartYear: Int = 0
        private set
    var filterLength: IntRange? = null
        private set
    var filterCategories = emptyArray<Long>()
        private set

    private var customTitle: String? = null

    override fun getTitle(): String? {
        return customTitle ?: super.getTitle()
    }

    constructor(
        title: String,
        queryText: String? = null,
        filterStatus: Array<Int> = emptyArray(),
        filterSources: Array<Long> = emptyArray(),
        filterMangaType: Array<Int> = emptyArray(),
        filterLanguages: Array<String> = emptyArray(),
        filterCategories: Array<Long> = emptyArray(),
        filterTags: Array<String> = emptyArray(),
        filterTracked: Int = 0,
        filterTrackerName: String? = null,
        filterTrackingScore: Int = 0,
        filterStartYear: Int = 0,
        filterLength: IntRange? = null,
    ) : this() {
        customTitle = title
        this.filterStatus = filterStatus
        this.filterLanguages = filterLanguages
        this.filterSources = filterSources
        this.filterTracked = filterTracked
        this.filterMangaType = filterMangaType
        this.filterCategories = filterCategories
        this.filterTags = filterTags
        if (filterTracked != 0 && filterTrackerName != null) {
            FilterBottomSheet.FILTER_TRACKER = filterTrackerName
        }
        this.filterTrackingScore = filterTrackingScore
        this.filterStartYear = filterStartYear
        this.filterLength = filterLength
        this.queryText = queryText
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        binding.filterBottomSheet.root.sheetBehavior?.hide()
        binding.swipeRefresh.isEnabled = false
        queryText?.let { search(it) }
    }

    override fun showFloatingBar() = false

    override fun showCategories(show: Boolean, closeSearch: Boolean, category: Int) {
        super.showCategories(show, closeSearch, category)
        binding.swipeRefresh.isEnabled = false
    }

    override fun onActionStateChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onActionStateChanged(viewHolder, actionState)
        binding.swipeRefresh.isEnabled = false
    }

    override fun onItemReleased(position: Int) {
        super.onItemReleased(position)
        binding.swipeRefresh.isEnabled = false
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.filtered_library, menu)
        val groupItem = menu.findItem(R.id.action_group_by)
        val context = binding.root.context
        val iconRes = LibraryGroup.groupTypeDrawableRes(presenter.groupType)
        val icon = context.contextCompatDrawable(iconRes)
            ?.apply { setTint(context.getResourceColor(R.attr.actionBarTintColor)) }
        groupItem?.icon = icon
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_group_by -> showGroupOptions()
            R.id.display_options -> showDisplayOptions()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onNextLibraryUpdate(mangaMap: List<LibraryItem>, freshStart: Boolean) {
        super.onNextLibraryUpdate(mangaMap, freshStart)
        activity?.invalidateOptionsMenu()
    }

    override fun onChangeStarted(handler: ControllerChangeHandler, type: ControllerChangeType) {
        super.onChangeStarted(handler, type)
        if (type == ControllerChangeType.POP_ENTER) {
            updateStatsPage()
        }
        binding.filterBottomSheet.root.sheetBehavior?.hide()
    }

    override fun deleteMangasFromLibrary() {
        super.deleteMangasFromLibrary()
        updateStatsPage()
    }

    fun updateStatsPage() {
        (previousController as? StatsDetailsController)?.updateLibrary()
    }

    override fun showSheet() { }
    override fun toggleSheet() {
        closeTip()
    }
    override fun toggleCategoryVisibility(position: Int) {}
    override fun manageCategory(position: Int) {}
}
