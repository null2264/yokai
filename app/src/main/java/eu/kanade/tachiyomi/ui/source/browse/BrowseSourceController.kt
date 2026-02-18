package eu.kanade.tachiyomi.ui.source.browse

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExploreOff
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.touchlab.kermit.Logger
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.google.android.material.snackbar.Snackbar
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.BrowseSourceControllerBinding
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.pkgName
import eu.kanade.tachiyomi.ui.base.controller.BaseCoroutineController
import eu.kanade.tachiyomi.ui.extension.details.ExtensionDetailsController
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.main.SearchActivity
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.source.BrowseController
import eu.kanade.tachiyomi.ui.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.addOrRemoveToFavorites
import eu.kanade.tachiyomi.util.system.connectivityManager
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.e
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.setTextInput
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.applyBottomAnimatedInsets
import eu.kanade.tachiyomi.util.view.fullAppBarHeight
import eu.kanade.tachiyomi.util.view.inflate
import eu.kanade.tachiyomi.util.view.isControllerVisible
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.setAction
import eu.kanade.tachiyomi.util.view.setMessage
import eu.kanade.tachiyomi.util.view.setNegativeButton
import eu.kanade.tachiyomi.util.view.setOnQueryTextChangeListener
import eu.kanade.tachiyomi.util.view.setPositiveButton
import eu.kanade.tachiyomi.util.view.setTitle
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import eu.kanade.tachiyomi.widget.AutofitRecyclerView
import eu.kanade.tachiyomi.widget.EmptyView
import eu.kanade.tachiyomi.widget.LinearLayoutManagerAccurateOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy
import yokai.domain.manga.interactor.GetManga
import yokai.domain.source.browse.filter.models.SavedSearch
import yokai.i18n.MR
import yokai.presentation.core.icons.CustomIcons
import yokai.presentation.core.icons.LocalSource
import yokai.util.lang.getString

/**
 * Controller to manage the catalogues available in the app.
 */
open class BrowseSourceController(bundle: Bundle) :
    BaseCoroutineController<BrowseSourceControllerBinding, BrowseSourcePresenter>(bundle),
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener,
    FloatingSearchInterface,
    FlexibleAdapter.EndlessScrollListener {

    constructor(
        source: CatalogueSource,
        searchQuery: String? = null,
        smartSearchConfig: BrowseController.SmartSearchConfig? = null,
        useLatest: Boolean = false,
    ) : this(
        Bundle().apply {
            putLong(SOURCE_ID_KEY, source.id)

            if (searchQuery != null) {
                putString(SEARCH_QUERY_KEY, searchQuery)
            }

            if (smartSearchConfig != null) {
                putParcelable(SMART_SEARCH_CONFIG_KEY, smartSearchConfig)
            }
            putBoolean(USE_LATEST_KEY, useLatest)
        },
    )

    constructor(source: CatalogueSource) : this(
        Bundle().apply {
            putLong(SOURCE_ID_KEY, source.id)
        },
    )

    private val getManga: GetManga by injectLazy()

    /**
     * Preferences helper.
     */
    private val preferences: PreferencesHelper by injectLazy()

    /**
     * Adapter containing the list of manga from the catalogue.
     */
    private var adapter: FlexibleAdapter<IFlexible<*>>? = null

    /**
     * Snackbar containing an error message when a request fails.
     */
    private var snack: Snackbar? = null

    /**
     * Recycler view with the list of results.
     */
    private var recycler: RecyclerView? = null

    /**
     * Endless loading item.
     */
    private var progressItem: ProgressItem? = null

    /** Current filter sheet */
    private var filterSheet: SourceFilterSheet? = null
    private var lastPosition: Int = -1

    // Basically a cache just so the filter sheet is shown faster
    var savedSearches by mutableStateOf(emptyList<SavedSearch>())

    private val isBehindGlobalSearch: Boolean
        get() = router.backstackSize >= 2 && router.backstack[router.backstackSize - 2].controller is GlobalSearchController

    /** Watch for manga data changes */
    private var watchJob: Job? = null

    init {
        setHasOptionsMenu(true)
    }

    override val mainRecycler: RecyclerView?
        get() = recycler

    override fun getTitle(): String? {
        return if (presenter.sourceIsInitialized) presenter.source.name else null
    }

    override fun getSearchTitle(): String? {
        return if (presenter.sourceIsInitialized) searchTitle(presenter.source.name) else null
    }

    // disabling for now, one day maybe it will source icons will good
//    override fun getBigIcon(): Drawable? {
//        return presenter.source.icon()
//    }

    override val presenter = BrowseSourcePresenter(
        args.getLong(SOURCE_ID_KEY),
        args.getString(SEARCH_QUERY_KEY),
        args.getBoolean(USE_LATEST_KEY),
    )

    override fun createBinding(inflater: LayoutInflater) = BrowseSourceControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        // Initialize adapter, scroll listener and recycler views
        adapter = FlexibleAdapter(null, this, false)
        setupRecycler(view)

        if (presenter.sourceFilters.isEmpty() && !presenter.source.supportsLatest) {
            binding.floatingBrowseBar.isVisible = false
        } else {
            binding.btnGroupFilter.isVisible = presenter.sourceFilters.isNotEmpty()
            binding.btnGroupLatest.isVisible = presenter.source.supportsLatest
        }
        val isFiltered = { !presenter.filtersMatchDefault() || presenter.query.isNotBlank() }
        binding.btnGroupPopular.setOnClickListener {
            if (presenter.useLatest || isFiltered()) {
                presenter.useLatest = false
                resetPager()
            }
            updatePopLatestIcons()
        }
        binding.btnGroupLatest.setOnClickListener {
            if (!presenter.useLatest || isFiltered()) {
                presenter.useLatest = true
                resetPager()
            }
            updatePopLatestIcons()
        }
        binding.btnGroupFilter.setOnClickListener {
            showFilters()
            binding.btnGroupFilter.isChecked = isFiltered()
        }
        updatePopLatestIcons()

        activityBinding?.appBar?.y = 0f
        activityBinding?.appBar?.updateAppBarAfterY(recycler)
        activityBinding?.appBar?.lockYPos = true
        if (!presenter.sourceIsInitialized) {
            activity?.toast(MR.strings.source_not_installed)
            if (activity is SearchActivity) {
                activity?.finish()
            } else {
                router.popCurrentController()
            }
            return
        }

        binding.progress.isVisible = true

        presenter.restartPager()
    }

    override fun onDestroyView(view: View) {
        adapter = null
        snack = null
        recycler = null
        super.onDestroyView(view)
    }

    private fun setupRecycler(view: View) {
        var oldPosition = RecyclerView.NO_POSITION
        var oldOffset = 0f
        val oldRecycler = binding.catalogueView.getChildAt(1)
        if (oldRecycler is RecyclerView) {
            oldPosition = (oldRecycler.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
                .takeIf { it != RecyclerView.NO_POSITION }
                ?: (oldRecycler.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            oldOffset = oldRecycler.layoutManager?.findViewByPosition(oldPosition)?.y?.minus(oldRecycler.paddingTop) ?: 0f
            oldRecycler.adapter = null

            binding.catalogueView.removeView(oldRecycler)
        }

        val recycler = if (presenter.preferences.browseAsList().get()) {
            RecyclerView(view.context).apply {
                id = R.id.recycler
                layoutManager = LinearLayoutManagerAccurateOffset(context)
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            }
        } else {
            (binding.catalogueView.inflate(R.layout.manga_recycler_autofit) as AutofitRecyclerView).apply {
                setGridSize(preferences)

                (layoutManager as androidx.recyclerview.widget.GridLayoutManager).spanSizeLookup = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return when (adapter?.getItemViewType(position)) {
                            R.layout.manga_grid_item, null -> 1
                            else -> spanCount
                        }
                    }
                }
            }
        }
        recycler.clipToPadding = false
        recycler.setHasFixedSize(true)
        recycler.adapter = adapter

        binding.catalogueView.addView(recycler, 1)
        scrollViewWith(
            recycler,
            true,
            afterInsets = { insets ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    binding.floatingBrowseBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        bottomMargin = insets.getInsets(systemBars() or ime()).bottom + 8.dpToPx
                    }
                }
                val bigToolbarHeight = fullAppBarHeight ?: 0
                binding.progress.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = (bigToolbarHeight + insets.getInsets(systemBars()).top) / 2
                }
                binding.emptyView.updatePadding(
                    top = (bigToolbarHeight + insets.getInsets(systemBars()).top),
                    bottom = insets.getInsets(systemBars()).bottom,
                )
            },
        )
        binding.floatingBrowseBar.applyBottomAnimatedInsets(8.dpToPx)

        if (oldPosition != RecyclerView.NO_POSITION) {
            (recycler.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(oldPosition, oldOffset.roundToInt())
            if (oldPosition > 0 && (activity as? MainActivity)?.currentToolbar != activityBinding?.searchToolbar) {
                activityBinding?.appBar?.useSearchToolbarForMenu(true)
            }
        }
        this.recycler = recycler
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.browse_source, menu)

        // Initialize search menu
        val searchItem = activityBinding?.searchToolbar?.searchItem
        val searchView = activityBinding?.searchToolbar?.searchView

        activityBinding?.searchToolbar?.setQueryHint("", !isBehindGlobalSearch && presenter.query.isBlank())
        if (presenter.query.isNotBlank()) {
            searchItem?.expandActionView()
            searchView?.setQuery(presenter.query, true)
            searchView?.clearFocus()
        } else if (activityBinding?.searchToolbar?.isSearchExpanded == true) {
            searchItem?.collapseActionView()
            searchView?.setQuery("", true)
        }

        setOnQueryTextChangeListener(searchView, onlyOnSubmit = true, hideKbOnSubmit = true) {
            searchWithQuery(it ?: "")
            true
        }
        // Show next display mode
        updateDisplayMenuItem(menu)
    }

    private fun updateDisplayMenuItem(menu: Menu?, isListMode: Boolean? = null) {
        menu?.findItem(R.id.action_display_mode)?.apply {
            val icon = if (isListMode ?: presenter.preferences.browseAsList().get()) {
                R.drawable.ic_view_module_24dp
            } else {
                R.drawable.ic_view_list_24dp
            }
            setIcon(icon)
        }
    }

    override fun onActionViewCollapse(item: MenuItem?) {
        if (isBehindGlobalSearch) {
            router.popController(this)
        } else {
            searchWithQuery("")
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        val isHttpSource = presenter.source is HttpSource
        menu.findItem(R.id.action_open_in_web_view).isVisible = isHttpSource
        val isConfigurableSource = presenter.source is ConfigurableSource
        menu.findItem(R.id.action_source_settings).isVisible = isConfigurableSource

        val isLocalSource = presenter.source is LocalSource
        menu.findItem(R.id.action_local_source_help).isVisible = isLocalSource
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search -> expandActionViewFromInteraction = true
            R.id.action_display_mode -> swapDisplayMode()
            R.id.action_open_in_web_view -> openInWebView()
            R.id.action_local_source_help -> openLocalSourceHelpGuide()
            R.id.action_source_settings -> openSourceSettings()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun applyFilters() {
        val allDefault = presenter.filtersMatchDefault()
        showProgressBar()
        adapter?.clear()
        presenter.setSourceFilter(if (allDefault) FilterList() else presenter.sourceFilters)
        updatePopLatestIcons()
    }

    private fun showFilters() {
        if (filterSheet != null) return
        val oldFilters = mutableListOf<Any?>()
        for (i in presenter.sourceFilters) {
            if (i is Filter.Group<*>) {
                val subFilters = mutableListOf<Any?>()
                for (j in i.state) {
                    subFilters.add((j as Filter<*>).state)
                }
                oldFilters.add(subFilters)
            } else {
                oldFilters.add(i.state)
            }
        }

        filterSheet = SourceFilterSheet(
            activity = activity!!,
            searches = { savedSearches },
            onSearchClicked = {
                var matches = true
                for (i in presenter.sourceFilters.indices) {
                    val filter = oldFilters.getOrNull(i)
                    if (filter is List<*>) {
                        for (j in filter.indices) {
                            if (filter[j] !=
                                (
                                    (presenter.sourceFilters[i] as Filter.Group<*>).state[j] as
                                        Filter<*>
                                    ).state
                            ) {
                                matches = false
                                break
                            }
                        }
                    } else if (filter != presenter.sourceFilters[i].state) {
                        matches = false
                        break
                    }
                    if (!matches) break
                }
                if (!matches) {
                    applyFilters()
                }
            },
            onResetClicked = {
                presenter.appliedFilters = FilterList()
                val newFilters = presenter.source.getFilterList()
                presenter.sourceFilters = newFilters
                filterSheet?.setFilters(presenter.filterItems)
            },
            onSaveClicked = {
                viewScope.launchIO {
                    val names = presenter.loadSearches().map { it.name }
                    var searchName = ""
                    withUIContext {
                        activity!!.materialAlertDialog()
                            .setTitle(activity!!.getString(MR.strings.save_search))
                            .setTextInput(hint = activity!!.getString(MR.strings.save_search_hint)) { input ->
                                searchName = input
                            }
                            .setPositiveButton(MR.strings.save) { _, _ ->
                                if (searchName.isNotBlank() && searchName !in names) {
                                    presenter.saveSearch(searchName.trim(), presenter.query, presenter.sourceFilters)
                                    filterSheet?.scrollToTop()
                                } else {
                                    activity!!.toast(MR.strings.save_search_invalid_name)
                                }
                            }
                            .setNegativeButton(MR.strings.cancel, null)
                            .show()
                    }
                }
            },
            onSavedSearchClicked = ss@{ searchId ->
                viewScope.launchIO {
                    val search = presenter.loadSearch(searchId)  // Grab the latest data from database
                    if (search?.filters == null) return@launchIO

                    withUIContext {
                        presenter.sourceFilters = search.filters
                        filterSheet?.setFilters(presenter.filterItems)
                        // This will call onSaveClicked()
                        filterSheet?.dismiss()
                    }
                }
            },
            onDeleteSavedSearchClicked = { searchId ->
                activity!!.materialAlertDialog()
                    .setTitle(MR.strings.save_search_delete)
                    .setMessage(MR.strings.save_search_delete)
                    .setPositiveButton(MR.strings.cancel, null)
                    .setNegativeButton(android.R.string.ok) { _, _ -> presenter.deleteSearch(searchId) }
                    .show()
            }
        )
        filterSheet?.setFilters(presenter.filterItems)
        presenter.filtersChanged = false

        filterSheet?.setOnCancelListener { filterSheet = null }
        filterSheet?.setOnDismissListener { filterSheet = null }

        filterSheet?.show()
    }

    /**
     * Attempts to restart the request with a new genre-filtered query.
     * If the genre name can't be found the filters,
     * the standard searchWithQuery search method is used instead.
     *
     * @param genreName the name of the genre
     */
    fun searchWithGenre(genreName: String, useContains: Boolean = false) {
        presenter.sourceFilters = presenter.source.getFilterList()

        var filterList: FilterList? = null

        filter@ for (sourceFilter in presenter.sourceFilters) {
            if (sourceFilter is Filter.Group<*>) {
                for (filter in sourceFilter.state) {
                    if (filter is Filter<*> &&
                        if (useContains) {
                            filter.name.contains(genreName, true)
                        } else {
                            filter.name.equals(genreName, true)
                        }
                    ) {
                        when (filter) {
                            is Filter.TriState -> filter.state = 1
                            is Filter.CheckBox -> filter.state = true
                            else -> break
                        }
                        filterList = presenter.sourceFilters
                        break@filter
                    }
                }
            } else if (sourceFilter is Filter.Select<*>) {
                val index = sourceFilter.values.filterIsInstance<String>()
                    .indexOfFirst {
                        if (useContains) {
                            it.contains(genreName, true)
                        } else {
                            it.equals(genreName, true)
                        }
                    }

                if (index != -1) {
                    sourceFilter.state = index
                    filterList = presenter.sourceFilters
                    break
                }
            }
        }

        if (filterList != null) {
            filterSheet?.setFilters(presenter.filterItems)

            showProgressBar()

            adapter?.clear()
            presenter.restartPager("", filterList)
        } else {
            if (!useContains) {
                searchWithGenre(genreName, true)
                return
            }
            searchWithQuery(genreName)
        }
        updatePopLatestIcons()
    }

    private fun openInWebView() {
        val source = presenter.source as? HttpSource ?: return
        val activity = activity ?: return
        val intent = WebViewActivity.newIntent(
            activity,
            source.baseUrl,
            source.id,
            source.name,
        )
        startActivity(intent)
    }

    private fun openLocalSourceHelpGuide() {
        activity?.openInBrowser(LocalSource.HELP_URL)
    }

    private fun openSourceSettings() {
        presenter.source.pkgName()?.let {
            router.pushController(
                ExtensionDetailsController(
                    it,
                ).withFadeTransaction(),
            )
        }
    }

    override fun onChangeStarted(handler: ControllerChangeHandler, type: ControllerChangeType) {
        super.onChangeStarted(handler, type)
        if (type == ControllerChangeType.POP_ENTER && lastPosition > -1) {
            adapter?.notifyItemChanged(lastPosition, false)
            lastPosition = -1
        }
    }

    /**
     * Restarts the request with a new query.
     *
     * @param newQuery the new query.
     */
    private fun searchWithQuery(newQuery: String) {
        // If text didn't change, do nothing
        if (presenter.query == newQuery) {
            return
        }

        showProgressBar()
        adapter?.clear()

        presenter.restartPager(newQuery)
        updatePopLatestIcons()
    }

    /**
     * Called from the presenter when the network request is received.
     *
     * @param page the current page.
     * @param mangas the list of manga of the page.
     */
    fun onAddPage(page: Int, mangas: List<BrowseSourceItem>) {
        val adapter = adapter ?: return
        hideProgressBar()
        if (page == 1) {
            adapter.clear()
            resetProgressItem()
        }
        adapter.onLoadMoreComplete(mangas)
        if (isControllerVisible) {
            activityBinding?.appBar?.lockYPos = false
        }
    }

    /**
     * Called from the presenter when the network request fails.
     *
     * @param error the error received.
     */
    fun onAddPageError(error: Throwable) {
        Logger.e(error)
        val adapter = adapter ?: return
        adapter.onLoadMoreComplete(null)
        hideProgressBar()

        snack?.dismiss()

        val message = getErrorMessage(error)
        val retryAction = {
            // If not the first page, show bottom binding.progress bar.
            if (adapter.mainItemCount > 0 && progressItem != null) {
                adapter.addScrollableFooterWithDelay(progressItem!!, 0, true)
            } else {
                showProgressBar()
            }
            presenter.requestNext()
        }

        if (adapter.isEmpty) {
            val actions = emptyList<EmptyView.Action>().toMutableList()

            actions += if (presenter.source is LocalSource) {
                EmptyView.Action(
                    MR.strings.local_source_help_guide,
                ) { openLocalSourceHelpGuide() }
            } else {
                EmptyView.Action(MR.strings.retry, retryAction)
            }

            if (presenter.source is HttpSource) {
                actions += EmptyView.Action(
                    MR.strings.open_in_webview,
                ) { openInWebView() }
            }

            binding.emptyView.show(
                if (presenter.source is HttpSource) {
                    Icons.Filled.ExploreOff
                } else {
                    CustomIcons.LocalSource
                },
                message,
                actions,
            )
        } else {
            snack = binding.sourceLayout.snack(message, Snackbar.LENGTH_INDEFINITE) {
                setAction(MR.strings.retry) { retryAction() }
            }
        }
        if (isControllerVisible) {
            activityBinding?.appBar?.lockYPos = false
        }
    }

    private fun getErrorMessage(error: Throwable): String {
        if (error is NoResultsException) {
            return activity!!.getString(MR.strings.no_results_found)
        }

        return when {
            error.message == null -> ""
            error.message!!.startsWith("HTTP error") -> "${error.message}: ${activity!!.getString(MR.strings.check_site_in_web)}"
            else -> error.message!!
        }
    }

    /**
     * Sets a new binding.progress item and reenables the scroll listener.
     */
    private fun resetProgressItem() {
        progressItem = ProgressItem()
        adapter?.endlessTargetCount = 0
        adapter?.setEndlessScrollListener(this, progressItem!!)
    }

    /**
     * Called by the adapter when scrolled near the bottom.
     */
    override fun onLoadMore(lastPosition: Int, currentPage: Int) {
        if (presenter.hasNextPage()) {
            presenter.requestNext()
        } else {
            adapter?.onLoadMoreComplete(null)
            adapter?.endlessTargetCount = 1
        }
    }

    override fun noMoreLoad(newItemsSize: Int) {
    }

    /**
     * Called from the presenter when a manga is initialized.
     *
     * @param manga the manga initialized
     */
    fun onMangaInitialized(manga: Manga) {
        getHolder(manga.id!!)?.setImage(manga)
    }

    /**
     * Swaps the current display mode.
     */
    private fun swapDisplayMode() {
        val view = view ?: return
        val adapter = adapter ?: return

        val isListMode = !presenter.preferences.browseAsList().get()
        presenter.preferences.browseAsList().set(isListMode)
        listOf(activityBinding?.toolbar?.menu, activityBinding?.searchToolbar?.menu).forEach {
            updateDisplayMenuItem(it, isListMode)
        }
        setupRecycler(view)
        // Initialize mangas if not on a metered connection
        if (!view.context.connectivityManager.isActiveNetworkMetered) {
            val mangas = (0 until adapter.itemCount).mapNotNull {
                (adapter.getItem(it) as? BrowseSourceItem)?.manga
            }
            presenter.initializeMangas(mangas)
        }
    }

    private fun resetPager() {
        val adapter = adapter ?: return

        showProgressBar()
        adapter.clear()
        updatePopLatestIcons()

        val searchItem = activityBinding?.searchToolbar?.searchItem
        searchItem?.collapseActionView()

        presenter.appliedFilters = FilterList()
        val newFilters = presenter.source.getFilterList()
        presenter.sourceFilters = newFilters
        presenter.filtersChanged = false

        presenter.restartPager("")
    }

    private fun updatePopLatestIcons() {
        val isNotFiltered = presenter.filtersMatchDefault() && presenter.query.isBlank()
        binding.btnGroupPopular.isChecked = !presenter.useLatest && isNotFiltered
        binding.btnGroupLatest.isChecked = presenter.useLatest && isNotFiltered
        binding.btnGroupFilter.isChecked = !isNotFiltered
    }

    /**
     * Returns the view holder for the given manga.
     *
     * @param manga the manga to find.
     * @return the holder of the manga or null if it's not bound.
     */
    private fun getHolder(mangaId: Long): BrowseSourceHolder? {
        val adapter = adapter ?: return null

        adapter.allBoundViewHolders.forEach { holder ->
            val item = adapter.getItem(holder.flexibleAdapterPosition) as? BrowseSourceItem
            if (item != null && item.mangaId == mangaId) {
                return holder as BrowseSourceHolder
            }
        }

        return null
    }

    /**
     * Shows the binding.progress bar.
     */
    private fun showProgressBar() {
        binding.emptyView.isVisible = false
        binding.progress.isVisible = true
        snack?.dismiss()
        snack = null
    }

    /**
     * Hides active binding.progress bars.
     */
    private fun hideProgressBar() {
        binding.emptyView.isVisible = false
        binding.progress.isVisible = false
    }

    fun unsubscribe() {
        watchJob?.cancel()
        watchJob = null
    }

    /**
     * Workaround to fix data state de-sync issues when controller detached,
     * and attaching flow directly into Item caused some flickering issues.
     *
     * FIXME: Could easily be fixed by migrating to Compose.
     */
    private fun BrowseSourceItem.subscribe() {
        watchJob?.cancel()
        watchJob = viewScope.launch {
            getManga.subscribeByUrlAndSource(manga.url, manga.source).collectLatest {
                if (it == null) return@collectLatest
                val holder = getHolder(mangaId) ?: return@collectLatest
                updateManga(holder, it)
            }
        }
    }

    /**
     * Called when a manga is clicked.
     *
     * @param position the position of the element clicked.
     * @return true if the item should be selected, false otherwise.
     */
    override fun onItemClick(view: View?, position: Int): Boolean {
        val item = adapter?.getItem(position) as? BrowseSourceItem ?: return false
        item.subscribe()
        router.pushController(MangaDetailsController(item.manga, true).withFadeTransaction())
        lastPosition = position
        return false
    }

    /**
     * Called when a manga is long clicked.
     *
     * Adds the manga to the default category if none is set it shows a list of categories for the user to put the manga
     * in, the list consists of the default category plus the user's categories. The default category is preselected on
     * new manga, and on already favorited manga the manga's categories are preselected.
     *
     * @param position the position of the element clicked.
     */
    override fun onItemLongClick(position: Int) {
        val manga = (adapter?.getItem(position) as? BrowseSourceItem?)?.manga ?: return
        val view = view ?: return
        val activity = activity ?: return
        viewScope.launchIO {
            withUIContext { snack?.dismiss() }
            snack = manga.addOrRemoveToFavorites(
                preferences,
                view,
                activity,
                presenter.sourceManager,
                this@BrowseSourceController,
                onMangaAdded = {
                    adapter?.notifyItemChanged(position)
                    snack = view.snack(MR.strings.added_to_library)
                },
                onMangaMoved = { adapter?.notifyItemChanged(position) },
                onMangaDeleted = { presenter.confirmDeletion(manga) },
                scope = viewScope,
            )
            if (snack?.duration == Snackbar.LENGTH_INDEFINITE) {
                withUIContext {
                    (activity as? MainActivity)?.setUndoSnackBar(snack)
                }
            }
        }
    }

    companion object {
        const val SOURCE_ID_KEY = "sourceId"

        const val SEARCH_QUERY_KEY = "searchQuery"
        const val USE_LATEST_KEY = "useLatest"
        const val SMART_SEARCH_CONFIG_KEY = "smartSearchConfig"
    }
}
