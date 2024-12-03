package eu.kanade.tachiyomi.ui.library

import eu.kanade.tachiyomi.core.preference.minusAssign
import eu.kanade.tachiyomi.core.preference.plusAssign
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Chapter.Companion.copy
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.models.removeCover
import eu.kanade.tachiyomi.data.database.models.seriesType
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.download.model.DownloadQueue
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
import eu.kanade.tachiyomi.util.system.launchUI
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

/**
 * Presenter of [LibraryController].
 */
class LibraryPresenter(
    private val preferences: PreferencesHelper = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val chapterFilter: ChapterFilter = Injekt.get(),
    private val trackManager: TrackManager = Injekt.get(),
) : BaseCoroutinePresenter<LibraryController>(), DownloadQueue.DownloadListener {
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

    private var removeArticles: Boolean = preferences.removeArticles().get()

    /** All categories of the library, in case they are hidden because of hide categories is on */
    private var allCategories: List<Category> = emptyList()

    /** List of all manga to update the */
    // TODO: Store sectioned before flattening it out for "show all categories"
    private var currentLibrary: Map<Category, List<LibraryItem>> = mapOf()
    var libraryItems: List<LibraryItem> = emptyList()
    private var sectionedLibraryItems: MutableMap<Int, List<LibraryItem>> = mutableMapOf()
    var currentCategory = -1
        private set
    var allLibraryItems: List<LibraryItem> = emptyList()
        private set
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

    fun findCurrentCategory() = allCategories.find { it.id == currentCategory }

    /** Save the current list to speed up loading later */
    override fun onDestroy() {
        val isSubController = controllerIsSubClass
        super.onDestroy()
        if (!isSubController) {
            lastLibraryItems = libraryItems
            lastCategories = categories
            lastAllLibraryItems = allLibraryItems
        }
    }

    override fun onCreate() {
        super.onCreate()
        downloadManager.addListener(this)
        if (!controllerIsSubClass) {
            lastLibraryItems?.let { libraryItems = it }
            lastCategories?.let { categories = it }
            lastAllLibraryItems?.let { allLibraryItems = it }
            lastCategories = null
            lastLibraryItems = null
            lastAllLibraryItems = null
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
        val items = sectionedLibraryItems[categoryId]
        return if (items?.firstOrNull()?.manga?.isHidden() == true || items?.firstOrNull()?.manga?.isBlank() == true) {
            items.firstOrNull()?.manga?.read ?: 0
        } else {
            sectionedLibraryItems[categoryId]?.size ?: 0
        }
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

            getLibraryFlow().collectLatest { data ->
                categories = data.categories
                allCategories = data.allCategories

                val library = data.items
                val hiddenItems = library.filter { it.manga.isHidden() }.mapNotNull { it.manga.items }.flatten()

                setDownloadCount(library)
                setUnreadBadge(library)
                setSourceLanguage(library)
                setDownloadCount(hiddenItems)
                setUnreadBadge(hiddenItems)
                setSourceLanguage(hiddenItems)

                allLibraryItems = library
                hiddenLibraryItems = hiddenItems
                val mangaMap = library
                    .applyFilters()
                    .applySort()
                val freshStart = libraryItems.isEmpty()
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
        val category = categories.find { it.order == order }?.id ?: return
        currentCategory = category
        view?.onNextLibraryUpdate(sectionedLibraryItems[currentCategory] ?: blankItem())
    }

    fun blankItem(id: Int = currentCategory, categories: List<Category>? = null): List<LibraryItem> {
        val actualCategories = categories ?: this.categories
        return listOf(
            LibraryItem(
                LibraryManga.createBlank(id),
                LibraryHeaderItem({ actualCategories.getOrDefault(id) }, id),
                viewContext,
            ),
        )
    }

    fun restoreLibrary() {
        val items = libraryItems
        val show = showAllCategories || !libraryIsGrouped || categories.size == 1
        sectionedLibraryItems = items.groupBy { it.header.category.id!! }.toMutableMap()
        if (!show && currentCategory == -1) {
            currentCategory = categories.find {
                it.order == preferences.lastUsedCategory().get()
            }?.id ?: 0
        }
        view?.onNextLibraryUpdate(
            if (!show) {
                sectionedLibraryItems[currentCategory]
                    ?: sectionedLibraryItems[categories.first().id] ?: blankItem()
            } else {
                libraryItems
            },
            true,
        )
    }

    fun getMangaInCategories(catId: Int?): List<LibraryManga>? {
        catId ?: return null
        return allLibraryItems.filter { it.header.category.id == catId }.map { it.manga }
    }

    private suspend fun sectionLibrary(items: List<LibraryItem>, freshStart: Boolean = false) {
        libraryItems = items
        val showAll = showAllCategories || !libraryIsGrouped || categories.size <= 1
        sectionedLibraryItems = items.groupBy { it.header.category.id ?: 0 }.toMutableMap()
        if (!showAll && currentCategory == -1) {
            currentCategory = categories.find {
                it.order == preferences.lastUsedCategory().get()
            }?.id ?: 0
        }
        withUIContext {
            view?.onNextLibraryUpdate(
                if (!showAll) {
                    sectionedLibraryItems[currentCategory]
                        ?: sectionedLibraryItems[categories.first().id] ?: blankItem()
                } else {
                    libraryItems
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
    private suspend fun List<LibraryItem>.applyFilters(): List<LibraryItem> {
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
        val missingCategorySet = categories.mapNotNull { it.id }.toMutableSet()
        val realCount = mutableMapOf<Int, Int>()
        val filteredItems = this.filter f@{ item ->
            if (showEmptyCategoriesWhileFiltering) {
                realCount[item.manga.category] = sectionedLibraryItems[item.manga.category]?.size ?: 0
            }

            if (!showEmptyCategoriesWhileFiltering && item.manga.isHidden()) {
                val subItems = sectionedLibraryItems[item.manga.category]?.takeUnless { it.size <= 1 }
                    ?: hiddenLibraryItems.filter { it.manga.category == item.manga.category }
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
            } else if (item.manga.isBlank() || item.manga.isHidden()) {
                missingCategorySet.remove(item.manga.category)
                return@f if (showAllCategories) {
                    filtersOff || showEmptyCategoriesWhileFiltering
                } else {
                    true
                }
            }
            val matches = matchesFilters(
                item,
                filterPrefs,
                filterTrackers,
            )
            if (matches) {
                missingCategorySet.remove(item.manga.category)
            }
            matches
        }.toMutableList()
        if (showEmptyCategoriesWhileFiltering) {
            missingCategorySet.forEach {
                filteredItems.add(
                    blankItem(it).first().apply { manga.realMangaCount = realCount[it] ?: 0 }
                )
            }
        }
        return filteredItems
    }

    private suspend fun matchesFilters(
        item: LibraryItem,
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
                item.manga.seriesType(sourceManager = sourceManager) !in arrayOf(filterPrefs.filterMangaType, Manga.TYPE_WEBTOON)
            } else {
                    filterPrefs.filterMangaType != item.manga.seriesType(sourceManager = sourceManager)
                }
            ) {
                return false
            }
        }

        // Filter for completed status of manga
        if (filterPrefs.filterCompleted == STATE_INCLUDE && item.manga.status != SManga.COMPLETED) return false
        if (filterPrefs.filterCompleted == STATE_EXCLUDE && item.manga.status == SManga.COMPLETED) return false

        if (!matchesFilterTracking(item, filterPrefs.filterTracked, filterTrackers)) return false

        // Filter for downloaded manga
        if (filterPrefs.filterDownloaded != STATE_IGNORE) {
            val isDownloaded = when {
                item.manga.isLocal() -> true
                item.downloadCount != -1 -> item.downloadCount > 0
                else -> downloadManager.getDownloadCount(item.manga) > 0
            }
            return if (filterPrefs.filterDownloaded == STATE_INCLUDE) isDownloaded else !isDownloaded
        }

        // Filter for NSFW/SFW contents
        if (filterPrefs.filterContentType == STATE_INCLUDE) return !item.manga.isLewd()
        if (filterPrefs.filterContentType == STATE_EXCLUDE) return item.manga.isLewd()
        return true
    }

    private suspend fun matchesCustomFilters(
        item: LibraryItem,
        customFilters: FilteredLibraryController,
        filterTrackers: String,
    ): Boolean {
        val statuses = customFilters.filterStatus
        if (statuses.isNotEmpty()) {
            if (item.manga.status !in statuses) return false
        }
        val seriesTypes = customFilters.filterMangaType
        if (seriesTypes.isNotEmpty()) {
            if (item.manga.seriesType(sourceManager = sourceManager) !in seriesTypes) return false
        }
        val languages = customFilters.filterLanguages
        if (languages.isNotEmpty()) {
            if (getLanguage(item.manga) !in languages) return false
        }
        val sources = customFilters.filterSources
        if (sources.isNotEmpty()) {
            if (item.manga.source !in sources) return false
        }
        val trackingScore = customFilters.filterTrackingScore
        if (trackingScore > 0 || trackingScore == -1) {
            val tracks = getTrack.awaitAllByMangaId(item.manga.id!!)

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
            val genres = item.manga.getGenres() ?: return false
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
        if (getChapter.awaitAll(id!!, false).any { it.read }) {
            val chapters = getHistory.awaitAllByMangaId(id!!).filter { it.last_read > 0 }
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
        item: LibraryItem,
        filterTracked: Int,
        filterTrackers: String,
    ): Boolean {
        // Filter for tracked (or per tracked service)
        if (filterTracked != STATE_IGNORE) {
            val tracks = getTrack.awaitAllByMangaId(item.manga.id!!)

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
                item.downloadCount = -1
            }
            return
        }

        for (item in itemList) {
            item.downloadCount = downloadManager.getDownloadCount(item.manga)
        }
    }

    private fun setUnreadBadge(itemList: List<LibraryItem>) {
        val unreadType = preferences.unreadBadgeType().get()
        for (item in itemList) {
            item.unreadType = unreadType
        }
    }

    private fun setSourceLanguage(itemList: List<LibraryItem>) {
        val showLanguageBadges = preferences.languageBadge().get()
        for (item in itemList) {
            item.sourceLanguage = if (showLanguageBadges) getLanguage(item.manga) else null
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
    private fun List<LibraryItem>.applySort(): List<LibraryItem> {
        val sortFn: (LibraryItem, LibraryItem) -> Int = { i1, i2 ->
            if (i1.header.category.id == i2.header.category.id) {
                val category = i1.header.category
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
                val compare = when {
                    category.mangaSort != null -> {
                        var sort = when (category.sortingMode() ?: LibrarySort.Title) {
                            LibrarySort.Title -> sortAlphabetical(i1, i2)
                            LibrarySort.LatestChapter -> i2.manga.last_update.compareTo(i1.manga.last_update)
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
                            LibrarySort.DateAdded -> i2.manga.date_added.compareTo(i1.manga.date_added)
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
                        val index1 = order.indexOf(i1.manga.id!!)
                        val index2 = order.indexOf(i2.manga.id!!)
                        when {
                            index1 == index2 -> 0
                            index1 == -1 -> -1
                            index2 == -1 -> 1
                            else -> index1.compareTo(index2)
                        }
                    }
                    else -> 0
                }
                if (compare == 0) {
                    sortAlphabetical(i1, i2)
                } else {
                    compare
                }
            } else {
                val category = i1.header.category.order
                val category2 = i2.header.category.order
                category.compareTo(category2)
            }
        }

        return this.sortedWith(Comparator(sortFn))
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
    private fun sortAlphabetical(i1: LibraryItem, i2: LibraryItem): Int {
        return if (removeArticles) {
            i1.manga.title.removeArticles().compareTo(i2.manga.title.removeArticles(), true)
        } else {
            i1.manga.title.compareTo(i2.manga.title, true)
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

    private fun MutableList<LibraryItem>.addRemovedManga(
        removedManga: Map<Category, List<LibraryItem>>,
    ): MutableList<LibraryItem> {
        removedManga.keys.forEach { key ->
            val manga = removedManga[key] ?: return@forEach
            val headerItem = try {
                manga.first().header
            } catch (e: NoSuchElementException) {
                return@forEach  // No hidden manga to be handled
            }
            val mergedTitle = manga.joinToString("-") {
                it.manga.title + "-" + it.manga.author
            }
            this.add(
                LibraryItem(
                    LibraryManga.createHide(
                        headerItem.catId,
                        mergedTitle,
                        manga,
                    ),
                    headerItem,
                    viewContext,
                ),
            )
        }
        return this
    }

    /**
     * Library's flow.
     *
     * If category id '-1' is not empty, it means the library not grouped by categories
     */
    private fun getLibraryFlow(): Flow<LibraryData> {
        val libraryItemFlow = combine(
            getCategories.subscribe(),
            getLibraryManga.subscribe(),
            getPreferencesFlow(),
            forceUpdateEvent.receiveAsFlow(),
        ) { allCategories, libraryMangaList, prefs, _ ->
            this.groupType = prefs.groupType
            this.allCategories = allCategories

            // FIXME: Should return Map<Int, LibraryItem> where Int is category id
            if (groupType <= BY_DEFAULT || !libraryIsGrouped) {
                getLibraryItems(
                    allCategories,  // FIXME: Don't depends on allCategories
                    libraryMangaList,
                    prefs.sortingMode,
                    prefs.sortAscending,
                    prefs.showAllCategories,
                    prefs.collapsedCategories,
                )
            } else {
                getDynamicLibraryItems(
                    libraryMangaList,
                    prefs.sortingMode,
                    prefs.sortAscending,
                    groupType,
                    prefs.collapsedDynamicCategories,
                )
            }
        }

        return combine(
            libraryItemFlow,
            preferences.removeArticles().changes(),
        ) { libraryItems, removeArticles ->
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
        allCategories: List<Category>,
        libraryManga: List<LibraryManga>,
        sortingMode: Int,
        isAscending: Boolean,
        showAll: Boolean,
        collapsedCategories: Set<String>,
    ): Triple<List<LibraryItem>, List<Category>, List<LibraryItem>> {
        val categories = allCategories.toMutableList()
        val hiddenItems = mutableListOf<LibraryItem>()

        val categoryAll = Category.createAll(
            context,
            sortingMode,
            isAscending,
        )
        val catItemAll = LibraryHeaderItem({ categoryAll }, -1)
        val categorySet = mutableSetOf<Int>()
        val headerItems = (
            categories.mapNotNull { category ->
                val id = category.id
                if (id == null) {
                    null
                } else {
                    id to LibraryHeaderItem({ categories.getOrDefault(id) }, id)
                }
            } + (-1 to catItemAll) + (0 to LibraryHeaderItem({ categories.getOrDefault(0) }, 0))
        ).toMap()

        // TODO
        val map = libraryManga.groupBy {
            categories.getOrDefault(it.category)
        }

        val items = if (libraryIsGrouped) {
            libraryManga
        } else {
            libraryManga.distinctBy { it.id }
        }.mapNotNull {
            val headerItem = (
                if (!libraryIsGrouped) {
                    catItemAll
                } else {
                    headerItems[it.category]
                }
                ) ?: return@mapNotNull null
            categorySet.add(it.category)
            LibraryItem(it, headerItem, viewContext)
        }.toMutableList()

        val categoriesHidden = if (forceShowAllCategories || controllerIsSubClass) {
            emptySet()
        } else {
            collapsedCategories.mapNotNull { it.toIntOrNull() }.toSet()
        }

        if (categorySet.contains(0)) categories.add(0, createDefaultCategory())
        if (libraryIsGrouped) {
            categories.forEach { category ->
                val catId = category.id ?: return@forEach
                if (catId > 0 && !categorySet.contains(catId) && (catId !in categoriesHidden || !showAll)) {
                    val headerItem = headerItems[catId]
                    if (headerItem != null) {
                        items.add(
                            LibraryItem(LibraryManga.createBlank(catId), headerItem, viewContext),
                        )
                    }
                } else if (catId in categoriesHidden && showAll && categories.size > 1) {
                    val mangaToRemove = items.filter { it.manga.category == catId }
                    val mergedTitle = mangaToRemove.joinToString("-") {
                        it.manga.title + "-" + it.manga.author
                    }
                    sectionedLibraryItems[catId] = mangaToRemove
                    hiddenItems.addAll(mangaToRemove)
                    items.removeAll(mangaToRemove)
                    val headerItem = headerItems[catId]
                    if (headerItem != null) {
                        items.add(
                            LibraryItem(
                                LibraryManga.createHide(
                                    catId,
                                    mergedTitle,
                                    mangaToRemove,
                                ),
                                headerItem,
                                viewContext,
                            ),
                        )
                    }
                }
            }
        }

        categories.forEach {
            it.isHidden = it.id in categoriesHidden && showAll && categories.size > 1
        }

        return Triple(
            items,
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
    ): Triple<List<LibraryItem>, List<Category>, List<LibraryItem>> {
        val tagItems: MutableMap<String, LibraryHeaderItem> = mutableMapOf()

        // internal function to make headers
        fun makeOrGetHeader(name: String, checkNameSwap: Boolean = false): LibraryHeaderItem {
            tagItems.get(name)?.let { return it }
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

        val unknown = context.getString(MR.strings.unknown)
        val items = libraryManga.distinctBy { it.id }.map { manga ->
            when (groupType) {
                BY_TAG -> {
                    val tags = if (manga.genre.isNullOrBlank()) {
                        listOf(unknown)
                    } else {
                        manga.genre?.split(",")?.mapNotNull {
                            val tag = it.trim().capitalizeWords()
                            tag.ifBlank { null }
                        } ?: listOf(unknown)
                    }
                    tags.map {
                        LibraryItem(manga, makeOrGetHeader(it), viewContext)
                    }
                }
                BY_TRACK_STATUS -> {
                    val tracks = getTrack.awaitAllByMangaId(manga.id!!)
                    val track = tracks.find { track ->
                        loggedServices.any { it.id == track?.sync_id }
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
                    listOf(LibraryItem(manga, makeOrGetHeader(status), viewContext))
                }
                BY_SOURCE -> {
                    val source = sourceManager.getOrStub(manga.source)
                    listOf(
                        LibraryItem(
                            manga,
                            makeOrGetHeader("${source.name}$sourceSplitter${source.id}"),
                            viewContext,
                        ),
                    )
                }
                BY_AUTHOR -> {
                    if (manga.artist.isNullOrBlank() && manga.author.isNullOrBlank()) {
                        listOf(LibraryItem(manga, makeOrGetHeader(unknown), viewContext))
                    } else {
                        listOfNotNull(
                            manga.author.takeUnless { it.isNullOrBlank() },
                            manga.artist.takeUnless { it.isNullOrBlank() },
                        ).map {
                            it.split(",", "/", " x ", " - ", ignoreCase = true).mapNotNull { name ->
                                val author = name.trim()
                                author.ifBlank { null }
                            }
                        }.flatten().distinct().map {
                            LibraryItem(manga, makeOrGetHeader(it, true), viewContext)
                        }
                    }
                }
                BY_LANGUAGE -> {
                    val lang = getLanguage(manga)
                    listOf(
                        LibraryItem(
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
                else -> listOf(LibraryItem(manga, makeOrGetHeader(context.mapStatus(manga.status)), viewContext)) // BY_STATUS
            }
        }.flatten().toMutableList()

        val hiddenDynamics = if (controllerIsSubClass) {
            emptySet()
        } else {
            collapsedDynamicCategories
        }
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
        headers.forEach { category ->
            val catId = category.id ?: return@forEach
            val headerItem =
                tagItems[
                    when {
                        category.sourceId != null -> "${category.name}$sourceSplitter${category.sourceId}"
                        category.langId != null -> "${category.langId}$langSplitter${category.name}"
                        else -> category.name
                    },
                ]
            if (category.isHidden) {
                val mangaToRemove = items.filter { it.header.catId == catId }
                val mergedTitle = mangaToRemove.joinToString("-") {
                    it.manga.title + "-" + it.manga.author
                }
                sectionedLibraryItems[catId] = mangaToRemove
                items.removeAll { it.header.catId == catId }
                if (headerItem != null) {
                    items.add(
                        LibraryItem(
                            LibraryManga.createHide(catId, mergedTitle, mangaToRemove),
                            headerItem,
                            viewContext,
                        ),
                    )
                }
            }
        }

        headers.forEachIndexed { index, category -> category.order = index }
        return Triple(items, headers, listOf())
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
            val mangaMap = allLibraryItems
                .applyFilters()
                .applySort()
            sectionLibrary(mangaMap)
        }
    }

    private fun requestBadgeUpdate(badgeUpdate: (List<LibraryItem>) -> Unit) {
        presenterScope.launch {
            val mangaMap = allLibraryItems
            badgeUpdate(mangaMap)
            allLibraryItems = mangaMap
            val current = libraryItems
            badgeUpdate(current)
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
            val mangaMap = libraryItems
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
                    getCategories.awaitByMangaId(manga.id!!)
                        .filter { it.id != oldCatId } + listOf(category)
                }

            for (cat in categories) {
                mc.add(cat.id!!.toLong())
            }

            setMangaCategories.await(manga.id!!, mc)

            if (category.mangaSort == null) {
                val ids = mangaIds.toMutableList()
                if (!ids.contains(manga.id!!)) ids.add(manga.id!!)
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
        val categories = runBlocking { getCategories.awaitByMangaId(manga.id!!) }.map { it.id }
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
        private var lastLibraryItems: List<LibraryItem>? = null
        private var lastCategories: List<Category>? = null
        private var lastAllLibraryItems: List<LibraryItem>? = null
        private const val sourceSplitter = "◘•◘"
        private const val langSplitter = "⨼⨦⨠"
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
            lastLibraryItems = null
            lastCategories = null
            lastAllLibraryItems = null
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
                        val distinctSources = getLibraryManga.await().distinctBy { it.source }
                        val randomSource =
                            sourceManager.get(
                                distinctSources.randomOrNull(random)?.source ?: 0L,
                            )?.name
                        randomSource?.chopByWords(30)
                    }
                    randomTitle -> {
                        getLibraryManga.await().randomOrNull(random)?.title?.chopByWords(30)
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
                if (manga.id == null) return@forEach
                if (manga.date_added == 0L) {
                    val chapters = getChapter.awaitAll(manga)
                    manga.date_added = chapters.minByOrNull { it.date_fetch }?.date_fetch ?: 0L
                    updateManga.await(MangaUpdate(manga.id!!, dateAdded = manga.date_added))
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
                if (manga.id == null) return@forEach
                if (manga.thumbnail_url?.startsWith("custom", ignoreCase = true) == true) {
                    val file = cc.getCoverFile(manga.thumbnail_url, !manga.favorite)
                    if (file != null && file.exists()) {
                        file.renameTo(cc.getCustomCoverFile(manga))
                    }
                    manga.thumbnail_url =
                        manga.thumbnail_url!!.lowercase(Locale.ROOT).substringAfter("custom-")
                    updateManga.await(MangaUpdate(manga.id!!, thumbnailUrl = manga.thumbnail_url))
                }
            }
        }
    }

    override fun updateDownload(download: Download) = updateDownloads()
    override fun updateDownloads() {
        presenterScope.launchUI {
            view?.updateDownloadStatus(!downloadManager.isPaused())
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
        val items: List<LibraryItem>,
        val hiddenItems: List<LibraryItem>,
        val removeArticles: Boolean,
    )
}
