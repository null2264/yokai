package eu.kanade.tachiyomi.ui.library

import eu.kanade.tachiyomi.core.preference.minusAssign
import eu.kanade.tachiyomi.core.preference.plusAssign
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Category.Companion.langSplitter
import eu.kanade.tachiyomi.data.database.models.Category.Companion.sourceSplitter
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Chapter.Companion.copy
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.models.removeCover
import eu.kanade.tachiyomi.data.database.models.seriesType
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.DelayedLibrarySuggestionsJob
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import eu.kanade.tachiyomi.ui.library.LibraryGroup.BY_AUTHOR
import eu.kanade.tachiyomi.ui.library.LibraryGroup.BY_DEFAULT
import eu.kanade.tachiyomi.ui.library.LibraryGroup.BY_LANGUAGE
import eu.kanade.tachiyomi.ui.library.LibraryGroup.BY_SOURCE
import eu.kanade.tachiyomi.ui.library.LibraryGroup.BY_TAG
import eu.kanade.tachiyomi.ui.library.LibraryGroup.BY_TRACK_STATUS
import eu.kanade.tachiyomi.ui.library.LibraryGroup.UNGROUPED
import eu.kanade.tachiyomi.ui.library.filter.FilterBottomSheet
import eu.kanade.tachiyomi.ui.library.filter.FilterBottomSheet.Companion.STATE_EXCLUDE
import eu.kanade.tachiyomi.ui.library.filter.FilterBottomSheet.Companion.STATE_IGNORE
import eu.kanade.tachiyomi.ui.library.filter.FilterBottomSheet.Companion.STATE_INCLUDE
import eu.kanade.tachiyomi.ui.recents.RecentsPresenter
import eu.kanade.tachiyomi.util.chapter.ChapterFilter
import eu.kanade.tachiyomi.util.chapter.ChapterSort
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.lang.capitalizeWords
import eu.kanade.tachiyomi.util.lang.chopByWords
import eu.kanade.tachiyomi.util.lang.removeArticles
import eu.kanade.tachiyomi.util.manga.MangaCoverMetadata
import eu.kanade.tachiyomi.util.mapStatus
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchNonCancellableIO
import eu.kanade.tachiyomi.util.system.withIOContext
import eu.kanade.tachiyomi.util.system.withUIContext
import java.util.*
import java.util.concurrent.*
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.category.interactor.GetCategories
import yokai.domain.category.interactor.SetMangaCategories
import yokai.domain.category.interactor.UpdateCategories
import yokai.domain.category.models.CategoryUpdate
import yokai.domain.chapter.interactor.GetChapter
import yokai.domain.chapter.interactor.UpdateChapter
import yokai.domain.chapter.models.ChapterUpdate
import yokai.domain.history.interactor.GetHistory
import yokai.domain.manga.interactor.GetLibraryManga
import yokai.domain.manga.interactor.GetManga
import yokai.domain.manga.interactor.UpdateManga
import yokai.domain.manga.models.MangaUpdate
import yokai.domain.track.interactor.GetTrack
import yokai.i18n.MR
import yokai.util.isLewd
import yokai.util.lang.getString

typealias LibraryMap = Map<Category, List<LibraryItem>>
typealias LibraryMutableMap = MutableMap<Category, List<LibraryItem>>

/**
 * Presenter of [LibraryController].
 */
class LibraryPresenter(
    private val preferences: PreferencesHelper = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    val sourceManager: SourceManager = Injekt.get(),
    private val downloadCache: DownloadCache = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val chapterFilter: ChapterFilter = Injekt.get(),
    private val trackManager: TrackManager = Injekt.get(),
) : BaseCoroutinePresenter<LibraryController>() {
    private val getCategories: GetCategories by injectLazy()
    private val setMangaCategories: SetMangaCategories by injectLazy()
    private val updateCategories: UpdateCategories by injectLazy()
    private val getLibraryManga: GetLibraryManga by injectLazy()
    private val getChapter: GetChapter by injectLazy()
    private val updateChapter: UpdateChapter by injectLazy()
    private val updateManga: UpdateManga by injectLazy()
    private val getTrack: GetTrack by injectLazy()
    private val getHistory: GetHistory by injectLazy()

    private val forceUpdateEvent: Channel<Unit> = Channel(Channel.UNLIMITED)

    private val context = preferences.context
    private val viewContext
        get() = view?.view?.context

    private val loggedServices by lazy { trackManager.services.filter { it.isLogged } }

    var groupType = preferences.groupLibraryBy().get()

    val isLoggedIntoTracking
        get() = loggedServices.isNotEmpty()

    /** Current categories of the library. */
    var categories: List<Category> = emptyList()
        private set

    /** All categories of the library, in case they are hidden because of hide categories is on */
    private var allCategories: List<Category> = emptyList()

    private var removeArticles: Boolean = preferences.removeArticles().get()

    /** List of all manga */
    var currentLibrary: LibraryMap = mapOf()
        private set
    val currentLibraryItems: List<LibraryItem>
        get() = currentLibrary.values.flatten()
    /** List of all manga to be displayed */
    private var libraryToDisplay: LibraryMutableMap = mutableMapOf()
    val libraryItemsToDisplay: List<LibraryItem>
        get() = libraryToDisplay.values.flatten()

    var currentCategoryId = -1
        private set
    var currentCategory: Category?
        get() = allCategories.find { it.id == currentCategoryId }
        set(value) { currentCategoryId = value?.id ?: 0 }

    private var hiddenLibraryItems: List<LibraryItem> = emptyList()
    var forceShowAllCategories = false
    val showAllCategories
        get() = forceShowAllCategories || preferences.showAllCategories().get()

    private val libraryIsGrouped
        get() = groupType != UNGROUPED

    private val controllerIsSubClass
        get() = view?.isSubClass == true

    var hasActiveFilters: Boolean = run {
        val filterDownloaded = preferences.filterDownloaded().get()

        val filterUnread = preferences.filterUnread().get()

        val filterCompleted = preferences.filterCompleted().get()

        val filterTracked = preferences.filterTracked().get()

        val filterMangaType = preferences.filterMangaType().get()

        val filterContentType = preferences.filterContentType().get()

        !(
            filterDownloaded == 0 &&
            filterUnread == 0 &&
            filterCompleted == 0 &&
            filterTracked == 0 &&
            filterMangaType == 0 &&
            filterContentType == 0
        )
    }

    fun isCategoryMoreThanOne(): Boolean = allCategories.size > 1

    /** Save the current list to speed up loading later */
    override fun onDestroy() {
        val isSubController = controllerIsSubClass
        super.onDestroy()
        if (!isSubController) {
            lastDisplayedLibrary = libraryToDisplay
            lastCategories = categories
            lastLibrary = currentLibrary
        }
    }

    override fun onCreate() {
        super.onCreate()

        if (!controllerIsSubClass) {
            lastDisplayedLibrary?.let { libraryToDisplay = it }
            lastCategories?.let { categories = it }
            lastLibrary?.let { currentLibrary = it }
            lastCategories = null
            lastDisplayedLibrary = null
            lastLibrary = null
        }

        subscribeLibrary()
        updateLibrary()

        if (!preferences.showLibrarySearchSuggestions().isSet()) {
            DelayedLibrarySuggestionsJob.setupTask(context, true)
        } else if (preferences.showLibrarySearchSuggestions().get() &&
            Date().time >= preferences.lastLibrarySuggestion().get() + TimeUnit.HOURS.toMillis(2)
        ) {
            // Doing this instead of a job in case the app isn't used often
            presenterScope.launch {
                withIOContext { setSearchSuggestion(preferences, getLibraryManga, sourceManager) }
                withUIContext { view?.setTitle() }
            }
        }
    }

    fun getItemCountInCategories(categoryId: Int): Int {
        val category = categories.find { it.id == categoryId }
        val items = libraryToDisplay[category]
        val firstItem = items?.firstOrNull() as? LibraryPlaceholderItem?
        if (firstItem != null) {
            if (firstItem.type !is LibraryPlaceholderItem.Type.Hidden) {
                return 0
            }
            return firstItem.type.hiddenItems.size
        }
        return items?.size ?: 0
    }

    private fun subscribeLibrary() {
        presenterScope.launchIO {
            // Initial setup
            if (categories.isEmpty()) {
                val dbCategories = getCategories.await()
                if ((dbCategories + Category.createDefault(context)).distinctBy { it.order }.size != dbCategories.size + 1) {
                    reorderCategories(dbCategories)
                }
                categories = lastCategories ?: getCategories.await().toMutableList()
            }

            combine(
                getLibraryFlow(),
                downloadCache.changes,
            ) { data, _ -> data }.collectLatest { data ->
                categories = data.categories
                allCategories = data.allCategories

                val library = data.items
                val hiddenItems = data.hiddenItems

                library.forEach { (_, items) ->
                    setDownloadCount(items)
                    setUnreadBadge(items)
                    setSourceLanguage(items)
                }
                setDownloadCount(hiddenItems)
                setUnreadBadge(hiddenItems)
                setSourceLanguage(hiddenItems)

                currentLibrary = library
                hiddenLibraryItems = hiddenItems
                val mangaMap = library
                    .applyFilters()
                    .applySort()
                val freshStart = libraryToDisplay.isEmpty()
                sectionLibrary(mangaMap, freshStart)
            }
        }
    }

    private suspend fun reorderCategories(categories: List<Category>) {
        val sortedCategories = categories.sortedBy { it.order }
        sortedCategories.forEachIndexed { i, category -> category.order = i }
        updateCategories.await(
            sortedCategories.map { CategoryUpdate(id = it.id!!.toLong(), order = it.order.toLong()) }
        )
    }

    fun switchSection(order: Int) {
        preferences.lastUsedCategory().set(order)
        val category = categories.find { it.order == order } ?: return
        currentCategory = category
        view?.onNextLibraryUpdate(libraryToDisplay[category] ?: blankItem())
    }

    fun blankItem(id: Int = currentCategoryId, categories: List<Category>? = null): List<LibraryItem> {
        val actualCategories = categories ?: this.categories
        return listOf(
            LibraryPlaceholderItem.blank(
                id,
                LibraryHeaderItem({ actualCategories.getOrDefault(id) }, id),
                viewContext,
            ),
        )
    }

    fun restoreLibrary() {
        val show = showAllCategories || !libraryIsGrouped || categories.size == 1
        if (!show && currentCategoryId == -1) {
            currentCategory = categories.find { it.order == preferences.lastUsedCategory().get() }
        }
        view?.onNextLibraryUpdate(
            if (!show) {
                libraryToDisplay[currentCategory]
                    ?: libraryToDisplay[categories.first()]
                    ?: blankItem()
            } else {
                libraryItemsToDisplay
            },
            true,
        )
    }

    fun getMangaInCategories(catId: Int?): List<LibraryManga>? {
        catId ?: return null
        return currentLibraryItems
            .filterIsInstance<LibraryMangaItem>()
            .filter { it.header.category.id == catId }
            .map { it.manga }
    }

    private suspend fun sectionLibrary(items: LibraryMap, freshStart: Boolean = false) {
        val showAll = showAllCategories || !libraryIsGrouped || categories.size <= 1

        libraryToDisplay = items.toMutableMap()

        if (!showAll && currentCategoryId == -1) {
            currentCategory = categories.find { it.order == preferences.lastUsedCategory().get() }
        }

        withUIContext {
            view?.onNextLibraryUpdate(
                if (!showAll) {
                    libraryToDisplay[currentCategory]
                        ?: libraryToDisplay[categories.first()]
                        ?: blankItem()
                } else {
                    libraryItemsToDisplay
                },
                freshStart,
            )
        }
    }

    /**
     * Applies library filters to the given list of manga.
     *
     * @param items the items to filter.
     */
    private suspend fun LibraryMap.applyFilters(): LibraryMap {
        val filterPrefs = getPreferencesFlow().first()
        val showEmptyCategoriesWhileFiltering = preferences.showEmptyCategoriesWhileFiltering().get()

        val filterTrackers = FilterBottomSheet.FILTER_TRACKER

        val filtersOff = view?.isSubClass != true &&
            (
                filterPrefs.filterDownloaded == 0 &&
                filterPrefs.filterUnread == 0 &&
                filterPrefs.filterCompleted == 0 &&
                filterPrefs.filterTracked == 0 &&
                filterPrefs.filterMangaType == 0 &&
                filterPrefs.filterContentType == 0
            )
        hasActiveFilters = !filtersOff
        val realCount = mutableMapOf<Int, Int>()
        val filteredItems = this.mapValues { (key, items) ->
            if (showEmptyCategoriesWhileFiltering) {
                realCount[key.id ?: 0] = libraryToDisplay[key]?.size ?: 0
            }

            items.filter f@{ item ->
                if (item is LibraryMangaItem) {
                    return@f matchesFilters(
                        item,
                        filterPrefs,
                        filterTrackers,
                    )
                }

                if (
                    !showEmptyCategoriesWhileFiltering
                    && item is LibraryPlaceholderItem
                    && item.type is LibraryPlaceholderItem.Type.Hidden
                ) {
                    val subItems = (libraryToDisplay[key] ?: hiddenLibraryItems)
                            .filterIsInstance<LibraryMangaItem>()
                            .filter { it.manga.category == item.category }
                    if (subItems.isEmpty()) {
                        return@f filtersOff
                    } else {
                        return@f subItems.any {
                            matchesFilters(
                                it,
                                filterPrefs,
                                filterTrackers,
                            )
                        }
                    }
                }

                if (showAllCategories) {
                    filtersOff || showEmptyCategoriesWhileFiltering
                } else {
                    true
                }
            }.ifEmpty {
                if (showEmptyCategoriesWhileFiltering) {
                    val catId = key.id!!
                    listOf(
                        LibraryPlaceholderItem.blank(
                            catId,
                            LibraryHeaderItem({ this@LibraryPresenter.categories.getOrDefault(catId) }, catId),
                            viewContext,
                            realCount[catId] ?: 0,
                        ),
                    )
                } else {
                    emptyList()
                }
            }
        }.toMutableMap()
        return filteredItems
    }

    private suspend fun matchesFilters(
        item: LibraryMangaItem,
        filterPrefs: ItemPreferences,
        filterTrackers: String,
    ): Boolean {
        (view as? FilteredLibraryController)?.let {
            return matchesCustomFilters(item, it, filterTrackers)
        }

        if (filterPrefs.filterUnread == STATE_INCLUDE && item.manga.unread == 0) return false
        if (filterPrefs.filterUnread == STATE_EXCLUDE && item.manga.unread > 0) return false

        // Filter for unread chapters
        if (filterPrefs.filterUnread == 3 && !(item.manga.unread > 0 && !item.manga.hasRead)) return false
        if (filterPrefs.filterUnread == 4 && !(item.manga.unread > 0 && item.manga.hasRead)) return false

        if (filterPrefs.filterBookmarked == STATE_INCLUDE && item.manga.bookmarkCount == 0) return false
        if (filterPrefs.filterBookmarked == STATE_EXCLUDE && item.manga.bookmarkCount > 0) return false

        if (filterPrefs.filterMangaType > 0) {
            if (if (filterPrefs.filterMangaType == Manga.TYPE_MANHWA) {
                item.manga.manga.seriesType(sourceManager = sourceManager) !in arrayOf(filterPrefs.filterMangaType, Manga.TYPE_WEBTOON)
            } else {
                    filterPrefs.filterMangaType != item.manga.manga.seriesType(sourceManager = sourceManager)
                }
            ) {
                return false
            }
        }

        // Filter for completed status of manga
        if (filterPrefs.filterCompleted == STATE_INCLUDE && item.manga.manga.status != SManga.COMPLETED) return false
        if (filterPrefs.filterCompleted == STATE_EXCLUDE && item.manga.manga.status == SManga.COMPLETED) return false

        if (!matchesFilterTracking(item, filterPrefs.filterTracked, filterTrackers)) return false

        // Filter for downloaded manga
        if (filterPrefs.filterDownloaded != STATE_IGNORE) {
            val isDownloaded = when {
                item.manga.manga.isLocal() -> true
                item.downloadCount != -1 -> item.downloadCount > 0
                else -> downloadManager.getDownloadCount(item.manga.manga) > 0
            }
            return if (filterPrefs.filterDownloaded == STATE_INCLUDE) isDownloaded else !isDownloaded
        }

        // Filter for NSFW/SFW contents
        if (filterPrefs.filterContentType == STATE_INCLUDE) return !item.manga.manga.isLewd()
        if (filterPrefs.filterContentType == STATE_EXCLUDE) return item.manga.manga.isLewd()
        return true
    }

    private suspend fun matchesCustomFilters(
        item: LibraryMangaItem,
        customFilters: FilteredLibraryController,
        filterTrackers: String,
    ): Boolean {
        val statuses = customFilters.filterStatus
        if (statuses.isNotEmpty()) {
            if (item.manga.manga.status !in statuses) return false
        }
        val seriesTypes = customFilters.filterMangaType
        if (seriesTypes.isNotEmpty()) {
            if (item.manga.manga.seriesType(sourceManager = sourceManager) !in seriesTypes) return false
        }
        val languages = customFilters.filterLanguages
        if (languages.isNotEmpty()) {
            if (getLanguage(item.manga.manga) !in languages) return false
        }
        val sources = customFilters.filterSources
        if (sources.isNotEmpty()) {
            if (item.manga.manga.source !in sources) return false
        }
        val trackingScore = customFilters.filterTrackingScore
        if (trackingScore > 0 || trackingScore == -1) {
            val tracks = getTrack.awaitAllByMangaId(item.manga.manga.id!!)

            val hasTrack = loggedServices.any { service ->
                tracks.any { it.sync_id == service.id }
            }
            if (trackingScore > 0 && !hasTrack) return false

            if (getMeanScoreToInt(tracks) != trackingScore) return false
        }
        if (!matchesFilterTracking(item, customFilters.filterTracked, filterTrackers)) return false
        val startingYear = customFilters.filterStartYear
        if (startingYear > 0) {
            val mangaStartingYear = item.manga.getStartYear()
            if (mangaStartingYear != startingYear) return false
        }
        val mangaLength = customFilters.filterLength
        if (mangaLength != null) {
            if (item.manga.totalChapters !in mangaLength) return false
        }
        val categories = customFilters.filterCategories
        if (categories.isNotEmpty()) {
            if (item.manga.category !in categories) return false
        }
        val tags = customFilters.filterTags
        if (tags.isNotEmpty()) {
            val genres = item.manga.manga.getGenres() ?: return false
            if (tags.none { tag -> genres.any { it.equals(tag, true) } }) return false
        }
        return true
    }

    /**
     * Get mean score rounded to int of a single manga
     */
    private fun getMeanScoreToInt(tracks: List<Track>): Int {
        val scoresList = tracks.filter { it.score > 0 }
            .mapNotNull { it.get10PointScore() }
        return if (scoresList.isEmpty()) -1 else scoresList.average().roundToInt().coerceIn(1..10)
    }

    private suspend fun LibraryManga.getStartYear(): Int {
        if (getChapter.awaitAll(manga.id!!, false).any { it.read }) {
            val chapters = getHistory.awaitAllByMangaId(manga.id!!).filter { it.last_read > 0 }
            val date = chapters.minOfOrNull { it.last_read } ?: return -1
            val cal = Calendar.getInstance().apply { timeInMillis = date }
            return if (date <= 0L) -1 else cal.get(Calendar.YEAR)
        }
        return -1
    }

    /**
     * Convert the score to a 10 point score
     */
    private fun Track.get10PointScore(): Float? {
        val service = trackManager.getService(this.sync_id)
        return service?.get10PointScore(this.score)
    }

    private suspend fun matchesFilterTracking(
        item: LibraryMangaItem,
        filterTracked: Int,
        filterTrackers: String,
    ): Boolean {
        // Filter for tracked (or per tracked service)
        if (filterTracked != STATE_IGNORE) {
            val tracks = getTrack.awaitAllByMangaId(item.manga.manga.id!!)

            val hasTrack = loggedServices.any { service ->
                tracks.any { it.sync_id == service.id }
            }
            val service = if (filterTrackers.isNotEmpty()) {
                loggedServices.find {
                    context.getString(it.nameRes()) == filterTrackers
                }
            } else {
                null
            }
            if (filterTracked == STATE_INCLUDE) {
                if (!hasTrack) return false
                if (filterTrackers.isNotEmpty()) {
                    if (service != null) {
                        val hasServiceTrack = tracks.any { it.sync_id == service.id }
                        if (!hasServiceTrack) return false
                        if (filterTracked == STATE_EXCLUDE) return false
                    }
                }
            } else if (filterTracked == STATE_EXCLUDE) {
                if (hasTrack && filterTrackers.isEmpty()) return false
                if (filterTrackers.isNotEmpty()) {
                    if (service != null) {
                        val hasServiceTrack = tracks.any { it.sync_id == service.id }
                        if (hasServiceTrack) return false
                    }
                }
            }
        }
        return true
    }

    /**
     * Sets downloaded chapter count to each manga.
     *
     * @param itemList the map of manga.
     */
    private fun setDownloadCount(itemList: List<LibraryItem>) {
        if (!preferences.downloadBadge().get()) {
            // Unset download count if the preference is not enabled.
            for (item in itemList) {
                if (item !is LibraryMangaItem) continue
                item.downloadCount = -1
            }
            return
        }

        for (item in itemList) {
            if (item !is LibraryMangaItem) continue
            item.downloadCount = downloadManager.getDownloadCount(item.manga.manga)
        }
    }

    private fun setUnreadBadge(itemList: List<LibraryItem>) {
        val unreadType = preferences.unreadBadgeType().get()
        for (item in itemList) {
            if (item !is LibraryMangaItem) continue
            item.unreadType = unreadType
        }
    }

    private fun setSourceLanguage(itemList: List<LibraryItem>) {
        val showLanguageBadges = preferences.languageBadge().get()
        for (item in itemList) {
            if (item !is LibraryMangaItem) continue
            item.sourceLanguage = if (showLanguageBadges) getLanguage(item.manga.manga) else null
        }
    }

    private fun getLanguage(manga: Manga): String? {
        return if (manga.isLocal()) {
            LocalSource.getMangaLang(manga)
        } else {
            sourceManager.get(manga.source)?.lang
        }
    }

    private fun onCategoryUpdate(update: CategoryUpdate) {
        presenterScope.launchNonCancellableIO { updateCategories.awaitOne(update) }
    }

    /**
     * Applies library sorting to the given list of manga.
     *
     * @param itemList the map to sort.
     */
    private fun LibraryMap.applySort(): LibraryMap {
        val sortFn: (LibraryItem, LibraryItem) -> Int = { i1, i2 ->
            val category = i1.header.category
            val compare = when {
                i1 is LibraryPlaceholderItem -> -1
                i2 is LibraryPlaceholderItem -> 1
                i1 !is LibraryMangaItem || i2 !is LibraryMangaItem -> 0
                category.mangaSort != null -> {
                    var sort = when (category.sortingMode() ?: LibrarySort.Title) {
                        LibrarySort.Title -> sortAlphabetical(i1, i2)
                        LibrarySort.LatestChapter -> i2.manga.latestUpdate.compareTo(i1.manga.latestUpdate)
                        LibrarySort.Unread -> when {
                            i1.manga.unread == i2.manga.unread -> 0
                            i1.manga.unread == 0 -> if (category.isAscending()) 1 else -1
                            i2.manga.unread == 0 -> if (category.isAscending()) -1 else 1
                            else -> i1.manga.unread.compareTo(i2.manga.unread)
                        }
                        LibrarySort.LastRead -> {
                            i1.manga.lastRead.compareTo(i2.manga.lastRead)
                        }
                        LibrarySort.TotalChapters -> {
                            i1.manga.totalChapters.compareTo(i2.manga.totalChapters)
                        }
                        LibrarySort.DateFetched -> {
                            i1.manga.lastFetch.compareTo(i2.manga.lastFetch)
                        }
                        LibrarySort.DateAdded -> i2.manga.manga.date_added.compareTo(i1.manga.manga.date_added)
                        LibrarySort.DragAndDrop -> {
                            if (category.isDynamic) {
                                val category1 =
                                    allCategories.find { i1.manga.category == it.id }?.order
                                        ?: 0
                                val category2 =
                                    allCategories.find { i2.manga.category == it.id }?.order
                                        ?: 0
                                category1.compareTo(category2)
                            } else {
                                sortAlphabetical(i1, i2)
                            }
                        }
                    }
                    if (!category.isAscending()) sort *= -1
                    sort
                }
                category.mangaOrder.isNotEmpty() -> {
                    val order = category.mangaOrder
                    val index1 = order.indexOf(i1.manga.manga.id!!)
                    val index2 = order.indexOf(i2.manga.manga.id!!)
                    when {
                        index1 == index2 -> 0
                        index1 == -1 -> -1
                        index2 == -1 -> 1
                        else -> index1.compareTo(index2)
                    }
                }
                else -> 0
            }
            if (compare == 0 && i1 is LibraryMangaItem && i2 is LibraryMangaItem) {
                sortAlphabetical(i1, i2)
            } else {
                compare
            }
        }

        return this.mapValues { (category, values) ->
            // Making sure category has valid sort
            if (category.mangaOrder.isEmpty() && category.mangaSort == null) {
                category.changeSortTo(preferences.librarySortingMode().get())
                if (category.id == 0) {
                    preferences.defaultMangaOrder()
                        .set(category.mangaSort.toString())
                } else if (!category.isDynamic) {
                    onCategoryUpdate(
                        CategoryUpdate(
                            id = category.id!!.toLong(),
                            mangaOrder = category.mangaOrderToString(),
                        )
                    )
                }
            }

            values.sortedWith(Comparator(sortFn))
        }.toSortedMap { category, category2 ->
            // Force default category to already be at the top. This also for some reason fixed a bug where Default
            // category would disappear whenever a new category is added.
            if (category.id == 0) {
                -1
            } else if (category2.id == 0) {
                1
            } else {
                category.order.compareTo(category2.order)
            }
        }
    }

    /** Gets the category by id
     *
     * @param categoryId id of the category to get
     */
    private fun List<Category>.getOrDefault(categoryId: Int): Category {
        val category = this.find { categoryId == it.id } ?: createDefaultCategory()
        category.isAlone = this.size <= 1
        return category
    }

    /**
     * Sort 2 manga by the their title (and remove articles if need be)
     *
     * @param i1 the first manga
     * @param i2 the second manga to compare
     */
    private fun sortAlphabetical(i1: LibraryMangaItem, i2: LibraryMangaItem): Int {
        return if (removeArticles) {
            i1.manga.manga.title.removeArticles().compareTo(i2.manga.manga.title.removeArticles(), true)
        } else {
            i1.manga.manga.title.compareTo(i2.manga.manga.title, true)
        }
    }

    private fun getPreferencesFlow() = combine(
        preferences.filterDownloaded().changes(),
        preferences.filterUnread().changes(),
        preferences.filterCompleted().changes(),
        preferences.filterTracked().changes(),
        preferences.filterMangaType().changes(),
        preferences.filterContentType().changes(),
        preferences.filterBookmarked().changes(),

        preferences.groupLibraryBy().changes(),
        preferences.showAllCategories().changes(),
        
        preferences.librarySortingMode().changes(),
        preferences.librarySortingAscending().changes(),

        preferences.collapsedCategories().changes(),
        preferences.collapsedDynamicCategories().changes(),
    ) {
       ItemPreferences(
           filterDownloaded = it[0] as Int,
           filterUnread = it[1] as Int,
           filterCompleted = it[2] as Int,
           filterTracked = it[3] as Int,
           filterMangaType = it[4] as Int,
           filterContentType = it[5] as Int,
           filterBookmarked = it[6] as Int,
           groupType = it[7] as Int,
           showAllCategories = it[8] as Boolean,
           sortingMode = it[9] as Int,
           sortAscending = it[10] as Boolean,
           collapsedCategories = it[11] as Set<String>,
           collapsedDynamicCategories = it[12] as Set<String>,
       )
    }

//    private fun MutableList<LibraryItem>.addRemovedManga(
//        removedManga: Map<Category, List<LibraryItem>>,
//    ): MutableList<LibraryItem> {
//        removedManga.keys.forEach { key ->
//            val manga = removedManga[key] ?: return@forEach
//            val headerItem = try {
//                manga.first().header
//            } catch (e: NoSuchElementException) {
//                return@forEach  // No hidden manga to be handled
//            }
//            val mergedTitle = manga.joinToString("-") {
//                it.manga.title + "-" + it.manga.manga.author
//            }
//            this.add(
//                LibraryItem(
//                    LibraryManga.createHide(
//                        headerItem.catId,
//                        mergedTitle,
//                        manga,
//                    ),
//                    headerItem,
//                    viewContext,
//                ),
//            )
//        }
//        return this
//    }

    /**
     * Library's flow.
     *
     * If category id '-1' is not empty, it means the library not grouped by categories
     */
    private fun getLibraryFlow(): Flow<LibraryData> {
        val libraryFlow = combine(
            getCategories.subscribe(),
            // FIXME: Remove retry once a real solution is found
            getLibraryManga.subscribe().retry(1) { e -> e is NullPointerException },
            getPreferencesFlow(),
            forceUpdateEvent.receiveAsFlow(),
        ) { dbCategories, libraryMangaList, prefs, _ ->
            groupType = prefs.groupType

            val defaultCategory = createDefaultCategory()

            // FIXME: Should return Map<Int, LibraryItem> where Int is category id
            if (groupType <= BY_DEFAULT || !libraryIsGrouped) {
                getLibraryItems(
                    dbCategories,
                    libraryMangaList,
                    prefs.sortingMode,
                    prefs.sortAscending,
                    prefs.showAllCategories,
                    prefs.collapsedCategories,
                    defaultCategory,
                )
            } else {
                getDynamicLibraryItems(
                    libraryMangaList,
                    prefs.sortingMode,
                    prefs.sortAscending,
                    groupType,
                    prefs.collapsedDynamicCategories,
                )
            } to listOf(defaultCategory) + dbCategories
        }

        return combine(
            libraryFlow,
            preferences.removeArticles().changes(),
        ) { library, removeArticles ->
            val (libraryItems, allCategories) = library
            val (items, categories, hiddenItems) = libraryItems

            LibraryData(
                categories = categories,
                allCategories = allCategories,
                items = items,
                hiddenItems = hiddenItems,
                removeArticles = removeArticles,
            )
        }
    }

    private fun getLibraryItems(
        dbCategories: List<Category>,
        libraryManga: List<LibraryManga>,
        sortingMode: Int,
        isAscending: Boolean,
        showAll: Boolean,
        collapsedCategories: Set<String>,
        defaultCategory: Category,
    ): Triple<LibraryMap, List<Category>, List<LibraryItem>> {
        val categories = dbCategories.mapNotNull { if (it.id == null) null else it }.toMutableList()
        val hiddenItems = mutableListOf<LibraryItem>()

        val categoryAll = Category.createAll(
            context,
            sortingMode,
            isAscending,
        )
        val catItemAll = LibraryHeaderItem({ categoryAll }, -1)

        // NOTE: Don't call header.category, only header.catId
        val headerItems = (
            categories.map { category ->
                val id = category.id!!
                id to LibraryHeaderItem({ this@LibraryPresenter.categories.getOrDefault(id) }, id)
            } + (0 to LibraryHeaderItem({ this@LibraryPresenter.categories.getOrDefault(0) }, 0))
        ).toMap()

        val categoriesHidden = if (forceShowAllCategories || controllerIsSubClass) {
            emptySet()
        } else {
            collapsedCategories.mapNotNull { it.toIntOrNull() }.toSet()
        }

        val map = if (!libraryIsGrouped)
            libraryManga
                .asSequence()
                .distinctBy { it.manga.id }
                .map { LibraryMangaItem(it, catItemAll, viewContext) }
                .groupBy { categoryAll }
        else {
            val rt = libraryManga
                .asSequence()
                .mapNotNull {
                    val headerItem = headerItems[it.category] ?: return@mapNotNull null
                    LibraryMangaItem(it, headerItem, viewContext)
                }
                .groupBy { it.header.catId }

            // Only show default category when needed
            if (rt.containsKey(0)) categories.add(0, defaultCategory)

            // NOTE: Empty list means hide the category entirely
            categories
                .associateWith { rt[it.id].orEmpty() }
                .mapValues { (key, values) ->
                    val catId = key.id!!  // null check already handled by mapNotNull
                    val headerItem = headerItems[catId]!!  // null check already handled by mapNotNull

                    // Hide category if "Show all categories" is enabled and there's more than 1 category
                    if (catId in categoriesHidden && showAll && categories.size > 1) {
                        val mergedTitle = values.joinToString("-") {
                            it.manga.manga.title + "-" + it.manga.manga.author
                        }
                        libraryToDisplay[key] = values
                        hiddenItems.addAll(values)
                        return@mapValues listOf(
                            LibraryPlaceholderItem.hidden(
                                catId,
                                headerItem,
                                viewContext,
                                mergedTitle,
                                values,
                            ),
                        )
                    }

                    // Making sure empty category is shown properly
                    values.ifEmpty {
                        listOf(
                            LibraryPlaceholderItem.blank(
                                catId,
                                headerItem,
                                viewContext,
                            ),
                        )
                    }
                }
        }.toMutableMap()

        categories.forEach { it.isHidden = it.id in categoriesHidden && showAll && categories.size > 1 }

        return Triple(
            map,
            if (!libraryIsGrouped) {
                arrayListOf(categoryAll)
            } else {
                categories
            },
            hiddenItems,
        )
    }

    private suspend fun getDynamicLibraryItems(
        libraryManga: List<LibraryManga>,
        sortingMode: Int,
        isAscending: Boolean,
        groupType: Int,
        collapsedDynamicCategories: Set<String>,
    ): Triple<LibraryMap, List<Category>, List<LibraryItem>> {
        val tagItems: MutableMap<String, LibraryHeaderItem> = mutableMapOf()
        val hiddenItems = mutableListOf<LibraryItem>()

        // internal function to make headers
        fun makeOrGetHeader(name: String, checkNameSwap: Boolean = false): LibraryHeaderItem {
            tagItems[name]?.let { return it }
            if (checkNameSwap && name.contains(" ")) {
                val swappedName = name.split(" ").reversed().joinToString(" ")
                if (tagItems.containsKey(swappedName)) {
                    return tagItems[swappedName]!!
                }
            }
            val headerItem = LibraryHeaderItem({ categories.getOrDefault(it) }, tagItems.count())
            tagItems[name] = headerItem
            return headerItem
        }

        val hiddenDynamics = if (controllerIsSubClass) {
            emptySet()
        } else {
            collapsedDynamicCategories
        }

        val unknown = context.getString(MR.strings.unknown)
        val items = libraryManga.distinctBy { it.manga.id }.map { manga ->
            when (groupType) {
                BY_TAG -> {
                    val tags = if (manga.manga.genre.isNullOrBlank()) {
                        listOf(unknown)
                    } else {
                        manga.manga.genre?.split(",")?.mapNotNull {
                            val tag = it.trim().capitalizeWords()
                            tag.ifBlank { null }
                        } ?: listOf(unknown)
                    }
                    tags.map {
                        LibraryMangaItem(manga, makeOrGetHeader(it), viewContext)
                    }
                }
                BY_TRACK_STATUS -> {
                    val tracks = getTrack.awaitAllByMangaId(manga.manga.id!!)
                    val track = tracks.find { track ->
                        loggedServices.any { it.id == track.sync_id }
                    }
                    val service = loggedServices.find { it.id == track?.sync_id }
                    val status: String = if (track != null && service != null) {
                        if (loggedServices.size > 1) {
                            service.getGlobalStatus(track.status)
                        } else {
                            service.getStatus(track.status)
                        }
                    } else {
                        view?.view?.context?.getString(MR.strings.not_tracked) ?: ""
                    }
                    listOf(LibraryMangaItem(manga, makeOrGetHeader(status), viewContext))
                }
                BY_SOURCE -> {
                    val source = sourceManager.getOrStub(manga.manga.source)
                    listOf(
                        LibraryMangaItem(
                            manga,
                            makeOrGetHeader("${source.name}$sourceSplitter${source.id}"),
                            viewContext,
                        ),
                    )
                }
                BY_AUTHOR -> {
                    if (manga.manga.artist.isNullOrBlank() && manga.manga.author.isNullOrBlank()) {
                        listOf(LibraryMangaItem(manga, makeOrGetHeader(unknown), viewContext))
                    } else {
                        listOfNotNull(
                            manga.manga.author.takeUnless { it.isNullOrBlank() },
                            manga.manga.artist.takeUnless { it.isNullOrBlank() },
                        ).map {
                            it.split(",", "/", " x ", " - ", ignoreCase = true).mapNotNull { name ->
                                val author = name.trim()
                                author.ifBlank { null }
                            }
                        }.flatten().distinct().map {
                            LibraryMangaItem(manga, makeOrGetHeader(it, true), viewContext)
                        }
                    }
                }
                BY_LANGUAGE -> {
                    val lang = getLanguage(manga.manga)
                    listOf(
                        LibraryMangaItem(
                            manga,
                            makeOrGetHeader(
                                lang?.plus(langSplitter)?.plus(
                                    run {
                                        val locale = Locale.forLanguageTag(lang)
                                        locale.getDisplayName(locale)
                                            .replaceFirstChar { it.uppercase(locale) }
                                    },
                                ) ?: unknown,
                            ),
                            viewContext,
                        ),
                    )
                }
                // BY_STATUS
                else -> listOf(LibraryMangaItem(manga, makeOrGetHeader(context.mapStatus(manga.manga.status)), viewContext))
            }
        }.flatten().groupBy { it.header.catId }

        val headers = tagItems.map { item ->
            Category.createCustom(
                item.key,
                sortingMode,
                isAscending,
            ).apply {
                id = item.value.catId
                if (name.contains(sourceSplitter)) {
                    val split = name.split(sourceSplitter)
                    name = split.first()
                    sourceId = split.last().toLongOrNull()
                } else if (name.contains(langSplitter)) {
                    val split = name.split(langSplitter)
                    name = split.last()
                    langId = split.first()
                }
                isHidden = getDynamicCategoryName(this) in hiddenDynamics
            }
        }.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) {
                if (groupType == BY_TRACK_STATUS) {
                    mapTrackingOrder(it.name)
                } else {
                    it.name
                }
            },
        ).let { headers ->
            if (!preferences.collapsedDynamicAtBottom().get()) return@let headers
            headers.filterNot { it.isHidden } + headers.filter { it.isHidden }
        }

        val map = headers
            .associateWith { items[it.id].orEmpty() }
            .mapValues { (key, values) ->
                val catId = key.id!!  // null check already handled by mapNotNull
                val headerItem = tagItems[key.dynamicHeaderKey()]
                if (key.isHidden) {
                    val mergedTitle = values.joinToString("-") {
                        it.manga.manga.title + "-" + it.manga.manga.author
                    }
                    libraryToDisplay[key] = values
                    hiddenItems.addAll(values)
                    if (headerItem != null) {
                        return@mapValues listOf(
                            LibraryPlaceholderItem.hidden(
                                catId,
                                headerItem,
                                viewContext,
                                mergedTitle,
                                values,
                            ),
                        )
                    }
                }
                values
            }

        headers.forEachIndexed { index, category -> category.order = index }
        return Triple(map, headers, hiddenItems)
    }

    private fun mapTrackingOrder(status: String): String {
        with(context) {
            return when (status) {
                getString(MR.strings.reading), getString(MR.strings.currently_reading) -> "1"
                getString(MR.strings.rereading) -> "2"
                getString(MR.strings.plan_to_read), getString(MR.strings.want_to_read) -> "3"
                getString(MR.strings.on_hold), getString(MR.strings.paused) -> "4"
                getString(MR.strings.completed) -> "5"
                getString(MR.strings.dropped) -> "6"
                else -> "7"
            }
        }
    }

    /** Create a default category with the sort set */
    private fun createDefaultCategory(): Category {
        val default = Category.createDefault(view?.applicationContext ?: context)
        default.order = -1
        val defOrder = preferences.defaultMangaOrder().get()
        if (defOrder.firstOrNull()?.isLetter() == true) {
            default.mangaSort = defOrder.first()
        } else {
            default.mangaOrder = defOrder.split("/").mapNotNull { it.toLongOrNull() }
        }
        return default
    }

    /** Requests the library to be filtered. */
    fun requestFilterUpdate() {
        presenterScope.launch {
            val mangaMap = currentLibrary
                .applyFilters()
                .applySort()
            sectionLibrary(mangaMap)
        }
    }

    private fun requestBadgeUpdate(badgeUpdate: (List<LibraryItem>) -> Unit) {
        presenterScope.launch {
            val mangaMap = currentLibrary
            mangaMap.forEach { (_, items) -> badgeUpdate(items) }
            currentLibrary = mangaMap
            val current = libraryToDisplay
            current.forEach { (_, items) -> badgeUpdate(items) }
            sectionLibrary(current)
        }
    }

    /** Requests the library to have download badges added/removed. */
    fun requestDownloadBadgesUpdate() {
        requestBadgeUpdate { setDownloadCount(it) }
    }

    /** Requests the library to have unread badges changed. */
    fun requestUnreadBadgesUpdate() {
        requestBadgeUpdate { setUnreadBadge(it) }
    }

    /** Requests the library to have language badges changed. */
    fun requestLanguageBadgesUpdate() {
        requestBadgeUpdate { setSourceLanguage(it) }
    }

    /** Requests the library to be sorted. */
    private fun requestSortUpdate() {
        presenterScope.launch {
            val mangaMap = libraryToDisplay
                .applySort()
            sectionLibrary(mangaMap)
        }
    }

    fun getMangaUrls(mangas: List<Manga>): List<String> {
        return mangas.mapNotNull { manga ->
            val source = sourceManager.get(manga.source) as? HttpSource ?: return@mapNotNull null
            source.getMangaUrl(manga)
        }
    }

    /**
     * Remove the selected manga from the library.
     *
     * @param mangas the list of manga to delete.
     */
    fun removeMangaFromLibrary(mangas: List<Manga>) {
        presenterScope.launch {
            // Create a set of the list
            val mangaToDelete = mangas.distinctBy { it.id }
                .mapNotNull { if (it.id != null) MangaUpdate(it.id!!, favorite = false) else null }

            withIOContext { updateManga.awaitAll(mangaToDelete) }
        }
    }

    /** Remove manga from the library and delete the downloads */
    fun confirmDeletion(mangas: List<Manga>, coverCacheToo: Boolean = true) {
        presenterScope.launchNonCancellableIO {
            val mangaToDelete = mangas.distinctBy { it.id }
            mangaToDelete.forEach { manga ->
                if (coverCacheToo) {
                    manga.removeCover(coverCache)
                }
                val source = sourceManager.get(manga.source) as? HttpSource
                if (source != null) {
                    downloadManager.deleteManga(manga, source)
                }
            }
            if (!coverCacheToo) {
                requestDownloadBadgesUpdate()
            }
        }
    }

    /** Force update the library */
    fun updateLibrary() = presenterScope.launch {
        forceUpdateEvent.send(Unit)
    }


    /** Undo the removal of the manga once in library */
    fun reAddMangas(mangas: List<Manga>) {
        presenterScope.launch {
            val mangaToAdd = mangas.distinctBy { it.id }
                .mapNotNull { if (it.id != null) MangaUpdate(it.id!!, favorite = true) else null }

            withIOContext { updateManga.awaitAll(mangaToAdd) }
            (view as? FilteredLibraryController)?.updateStatsPage()
        }
    }

    /** Returns first unread chapter of a manga */
    fun getFirstUnread(manga: Manga): Chapter? {
        // FIXME: Don't do blocking
        val chapters = runBlocking { getChapter.awaitAll(manga) }
        return ChapterSort(manga, chapterFilter, preferences).getNextUnreadChapter(chapters, false)
    }

    /** Update a category's sorting */
    fun sortCategory(catId: Int, order: Char) {
        val category = categories.find { catId == it.id } ?: return
        category.mangaSort = order
        if (catId == -1 || category.isDynamic) {
            val sort = category.sortingMode() ?: LibrarySort.Title
            preferences.librarySortingMode().set(sort.mainValue)
            preferences.librarySortingAscending().set(category.isAscending())
            categories.forEach {
                it.mangaSort = category.mangaSort
            }
        } else if (catId >= 0) {
            if (category.id == 0) {
                preferences.defaultMangaOrder().set(category.mangaSort.toString())
            } else {
                onCategoryUpdate(
                    CategoryUpdate(
                        id = catId.toLong(),
                        mangaOrder = category.mangaOrderToString(),
                    ),
                )
            }
        }
        requestSortUpdate()
    }

    /** Update a category's order */
    fun rearrangeCategory(catId: Int?, mangaIds: List<Long>) {
        presenterScope.launch {
            val category = categories.find { catId == it.id } ?: return@launch
            if (category.isDynamic) return@launch
            category.mangaSort = null
            category.mangaOrder = mangaIds
            if (category.id == 0) {
                preferences.defaultMangaOrder().set(mangaIds.joinToString("/"))
            } else {
                updateCategories.awaitOne(
                    CategoryUpdate(
                        id = category.id!!.toLong(),
                        mangaOrder = category.mangaOrderToString(),
                    ),
                )
            }
            requestSortUpdate()
        }
    }

    /** Shift a manga's category via drag & drop */
    fun moveMangaToCategory(
        manga: LibraryManga,
        catId: Int?,
        mangaIds: List<Long>,
    ) {
        presenterScope.launch {
            val categoryId = catId ?: return@launch
            val category = categories.find { catId == it.id } ?: return@launch
            if (category.isDynamic) return@launch

            val oldCatId = manga.category
            manga.category = categoryId

            val mc = ArrayList<Long>()
            val categories =
                if (catId == 0) {
                    emptyList()
                } else {
                    getCategories.awaitByMangaId(manga.manga.id!!)
                        .filter { it.id != oldCatId } + listOf(category)
                }

            for (cat in categories) {
                mc.add(cat.id!!.toLong())
            }

            setMangaCategories.await(manga.manga.id!!, mc)

            if (category.mangaSort == null) {
                val ids = mangaIds.toMutableList()
                if (!ids.contains(manga.manga.id!!)) ids.add(manga.manga.id!!)
                category.mangaOrder = ids
                if (category.id == 0) {
                    preferences.defaultMangaOrder()
                        .set(mangaIds.joinToString("/"))
                } else {
                    updateCategories.awaitOne(
                        CategoryUpdate(
                            id = category.id!!.toLong(),
                            mangaOrder = category.mangaOrderToString(),
                        ),
                    )
                }
            }
            updateLibrary()
        }
    }

    /** Returns if manga is in a category by id */
    fun mangaIsInCategory(manga: LibraryManga, catId: Int?): Boolean {
        // FIXME: Don't do blocking
        val categories = runBlocking { getCategories.awaitByMangaId(manga.manga.id!!) }.map { it.id }
        return catId in categories
    }

    fun toggleCategoryVisibility(categoryId: Int) {
        // if (categories.find { it.id == categoryId }?.isDynamic == true) return
        if (groupType == BY_DEFAULT) {
            val categoriesHidden = preferences.collapsedCategories().get().mapNotNull {
                it.toIntOrNull()
            }.toMutableSet()
            if (categoryId in categoriesHidden) {
                categoriesHidden.remove(categoryId)
            } else {
                categoriesHidden.add(categoryId)
            }
            preferences.collapsedCategories()
                .set(categoriesHidden.map { it.toString() }.toMutableSet())
        } else {
            val categoriesHidden = preferences.collapsedDynamicCategories().get().toMutableSet()
            val category = categories.getOrDefault(categoryId)
            val dynamicName = getDynamicCategoryName(category)
            if (dynamicName in categoriesHidden) {
                categoriesHidden.remove(dynamicName)
            } else {
                categoriesHidden.add(dynamicName)
            }
            preferences.collapsedDynamicCategories().set(categoriesHidden)
        }
    }

    private fun getDynamicCategoryName(category: Category): String =
        groupType.toString() + dynamicCategorySplitter + (
            category.sourceId?.toString() ?: category.langId ?: category.name
            )

    fun toggleAllCategoryVisibility() {
        if (groupType == BY_DEFAULT) {
            if (allCategoriesExpanded()) {
                preferences.collapsedCategories()
                    .set(allCategories.map { it.id.toString() }.toMutableSet())
            } else {
                preferences.collapsedCategories().set(mutableSetOf())
            }
        } else {
            if (allCategoriesExpanded()) {
                preferences.collapsedDynamicCategories() += categories.map {
                    getDynamicCategoryName(
                        it,
                    )
                }
            } else {
                preferences.collapsedDynamicCategories() -= categories.map {
                    getDynamicCategoryName(
                        it,
                    )
                }
            }
        }
    }

    fun allCategoriesExpanded(): Boolean {
        return if (groupType == BY_DEFAULT) {
            preferences.collapsedCategories().get().isEmpty()
        } else {
            categories.none { it.isHidden }
        }
    }

    /** download All unread */
    fun downloadUnread(mangaList: List<Manga>) {
        presenterScope.launch {
            withContext(Dispatchers.IO) {
                mangaList.forEach { list ->
                    val chapters = getChapter.awaitAll(list).filter { !it.read }
                    downloadManager.downloadChapters(list, chapters)
                }
            }
            if (preferences.downloadBadge().get()) {
                requestDownloadBadgesUpdate()
            }
        }
    }

    fun markReadStatus(
        mangaList: List<Manga>,
        markRead: Boolean,
    ): HashMap<Manga, List<Chapter>> {
        val mapMangaChapters = HashMap<Manga, List<Chapter>>()
        presenterScope.launchNonCancellableIO {
            mangaList.forEach { manga ->
                val chapters = getChapter.awaitAll(manga)
                val updates = chapters.copy().mapNotNull {
                    if (it.id == null) return@mapNotNull null
                    ChapterUpdate(it.id!!, read = markRead, lastPageRead = 0)
                }
                updateChapter.awaitAll(updates)

                mapMangaChapters[manga] = chapters
            }
            updateLibrary()
        }
        return mapMangaChapters
    }

    fun undoMarkReadStatus(
        mangaList: HashMap<Manga, List<Chapter>>,
    ) {
        presenterScope.launchNonCancellableIO {
            val updates = mangaList.values.map { chapters ->
                chapters.mapNotNull {
                    if (it.id == null) return@mapNotNull null
                    ChapterUpdate(it.id!!, read = it.read, lastPageRead = it.last_page_read.toLong())
                }
            }.flatten()
            updateChapter.awaitAll(updates)
            updateLibrary()
        }
    }

    fun confirmMarkReadStatus(
        mangaList: HashMap<Manga, List<Chapter>>,
        markRead: Boolean,
    ) {
        if (preferences.removeAfterMarkedAsRead().get() && markRead) {
            mangaList.forEach { (manga, oldChapters) ->
                deleteChapters(manga, oldChapters)
            }
            if (preferences.downloadBadge().get()) {
                requestDownloadBadgesUpdate()
            }
        }
    }

    private fun deleteChapters(manga: Manga, chapters: List<Chapter>) {
        sourceManager.get(manga.source)?.let { source ->
            downloadManager.deleteChapters(chapters, manga, source)
        }
    }

    companion object {
        private var lastDisplayedLibrary: LibraryMutableMap? = null
        private var lastCategories: List<Category>? = null
        private var lastLibrary: LibraryMap? = null
        private const val dynamicCategorySplitter = "▄╪\t▄╪\t▄"

        private val randomTags = arrayOf(0, 1, 2)
        private const val randomSource = 4
        private const val randomTitle = 3

        @Suppress("unused")
        private const val randomTag = 0
        private val randomGroupOfTags = arrayOf(1, 2)
        private const val randomGroupOfTagsNormal = 1

        @Suppress("unused")
        private const val randomGroupOfTagsNegate = 2

        fun onLowMemory() {
            lastDisplayedLibrary = null
            lastCategories = null
            lastLibrary = null
        }

        suspend fun setSearchSuggestion(
            preferences: PreferencesHelper,
            getLibraryManga: GetLibraryManga,
            sourceManager: SourceManager,
        ) {
            val random: Random = run {
                val cal = Calendar.getInstance()
                cal.time = Date()
                cal[Calendar.MINUTE] = 0
                cal[Calendar.SECOND] = 0
                cal[Calendar.MILLISECOND] = 0
                Random(cal.time.time)
            }

            preferences.librarySearchSuggestion().set(
                when (val value = random.nextInt(0, 5)) {
                    randomSource -> {
                        val distinctSources = getLibraryManga.await().distinctBy { it.manga.source }
                        val randomSource =
                            sourceManager.get(
                                distinctSources.randomOrNull(random)?.manga?.source ?: 0L,
                            )?.name
                        randomSource?.chopByWords(30)
                    }
                    randomTitle -> {
                        getLibraryManga.await().randomOrNull(random)?.manga?.title?.chopByWords(30)
                    }
                    in randomTags -> {
                        val tags = RecentsPresenter.getRecentManga(true)
                            .map { it.first.genre.orEmpty().split(",").map(String::trim) }
                            .flatten()
                            .filter { it.isNotBlank() }
                        val distinctTags = tags.distinct()
                        if (value in randomGroupOfTags && distinctTags.size > 6) {
                            val shortestTagsSort = distinctTags.sortedBy { it.length }
                            val offset = random.nextInt(0, distinctTags.size / 2 - 2)
                            var offset2 = random.nextInt(0, distinctTags.size / 2 - 2)
                            while (offset2 == offset) {
                                offset2 = random.nextInt(0, distinctTags.size / 2 - 2)
                            }
                            if (value == randomGroupOfTagsNormal) {
                                "${shortestTagsSort[offset]}, " + shortestTagsSort[offset2]
                            } else {
                                "${shortestTagsSort[offset]}, -" + shortestTagsSort[offset2]
                            }
                        } else {
                            val group = tags.groupingBy { it }.eachCount()
                            val groupedTags = distinctTags.sortedByDescending { group[it] }
                            groupedTags.take(8).randomOrNull(random)
                        }
                    }
                    else -> ""
                } ?: "",
            )

            if (!preferences.showLibrarySearchSuggestions().isSet()) {
                preferences.showLibrarySearchSuggestions().set(true)
            }
            preferences.lastLibrarySuggestion().set(Date().time)
        }

        /** Give library manga to a date added based on min chapter fetch */
        suspend fun updateDB(
            getChapter: GetChapter = Injekt.get(),
            getLibraryManga: GetLibraryManga = Injekt.get(),
            updateManga: UpdateManga = Injekt.get(),
        ) {
            val libraryManga = getLibraryManga.await()
            libraryManga.forEach { manga ->
                if (manga.manga.id == null) return@forEach
                if (manga.manga.date_added == 0L) {
                    val chapters = getChapter.awaitAll(manga.manga.id!!, manga.manga.filtered_scanlators?.isNotBlank() == true)
                    manga.manga.date_added = chapters.minByOrNull { it.date_fetch }?.date_fetch ?: 0L
                    updateManga.await(MangaUpdate(manga.manga.id!!, dateAdded = manga.manga.date_added))
                }
            }
        }

        suspend fun updateRatiosAndColors(
            getManga: GetManga = Injekt.get(),
        ) {
            val libraryManga = getManga.awaitFavorites()
            libraryManga.forEach { manga ->
                try { withUIContext { MangaCoverMetadata.setRatioAndColors(manga.id, manga.thumbnail_url, manga.favorite) } } catch (_: Exception) { }
            }
            MangaCoverMetadata.savePrefs()
        }

        suspend fun updateCustoms(
            cc: CoverCache = Injekt.get(),
            updateManga: UpdateManga = Injekt.get(),
        ) {
            val getLibraryManga: GetLibraryManga by injectLazy()
            val libraryManga = getLibraryManga.await()
            libraryManga.forEach { manga ->
                if (manga.manga.id == null) return@forEach
                if (manga.manga.thumbnail_url?.startsWith("custom", ignoreCase = true) == true) {
                    val file = cc.getCoverFile(manga.manga.thumbnail_url, !manga.manga.favorite)
                    if (file != null && file.exists()) {
                        file.renameTo(cc.getCustomCoverFile(manga.manga))
                    }
                    manga.manga.thumbnail_url =
                        manga.manga.thumbnail_url!!.lowercase(Locale.ROOT).substringAfter("custom-")
                    updateManga.await(MangaUpdate(manga.manga.id!!, thumbnailUrl = manga.manga.thumbnail_url))
                }
            }
        }
    }

    data class ItemPreferences(
        val filterDownloaded: Int,
        val filterUnread: Int,
        val filterCompleted: Int,
        val filterTracked: Int,
        val filterMangaType: Int,
        val filterContentType: Int,
        val filterBookmarked: Int,

        val groupType: Int,
        val showAllCategories: Boolean,

        val sortingMode: Int,
        val sortAscending: Boolean,

        val collapsedCategories: Set<String>,
        val collapsedDynamicCategories: Set<String>,
    )

    data class LibraryData(
        val categories: List<Category>,
        val allCategories: List<Category>,
        val items: LibraryMap,
        val hiddenItems: List<LibraryItem>,
        val removeArticles: Boolean,
    )
}
