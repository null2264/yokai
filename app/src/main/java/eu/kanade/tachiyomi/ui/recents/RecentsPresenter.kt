package eu.kanade.tachiyomi.ui.recents

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.ChapterHistory
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.HistoryImpl
import eu.kanade.tachiyomi.data.database.models.MangaChapterHistory
import eu.kanade.tachiyomi.data.download.DownloadJob
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.download.model.DownloadQueue
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import eu.kanade.tachiyomi.util.chapter.ChapterFilter
import eu.kanade.tachiyomi.util.chapter.ChapterSort
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchNonCancellableIO
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withUIContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TreeMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.data.DatabaseHandler
import yokai.domain.chapter.interactor.GetChapter
import yokai.domain.chapter.interactor.UpdateChapter
import yokai.domain.history.interactor.GetHistory
import yokai.domain.history.interactor.UpsertHistory
import yokai.domain.recents.RecentsPreferences
import yokai.domain.recents.interactor.GetRecents
import yokai.domain.ui.UiPreferences
import yokai.i18n.MR

class RecentsPresenter(
    val uiPreferences: UiPreferences = Injekt.get(),
    val recentsPreferences: RecentsPreferences = Injekt.get(),
    val preferences: PreferencesHelper = Injekt.get(),
    val downloadManager: DownloadManager = Injekt.get(),
    val db: DatabaseHelper = Injekt.get(),
    private val chapterFilter: ChapterFilter = Injekt.get(),
) : BaseCoroutinePresenter<RecentsController>(), DownloadQueue.DownloadListener {
    private val handler: DatabaseHandler by injectLazy()

    private val getChapter: GetChapter by injectLazy()
    private val getRecents: GetRecents by injectLazy()
    private val updateChapter: UpdateChapter by injectLazy()
    private val getHistory: GetHistory by injectLazy()
    private val upsertHistory: UpsertHistory by injectLazy()

    private var recentsJob: Job? = null
    var recentItems = listOf<RecentMangaItem>()
        private set
    var query = ""
        set(value) {
            field = value
            resetOffsets()
        }
    private val newAdditionsHeader = RecentMangaHeaderItem(RecentMangaHeaderItem.NEWLY_ADDED)
    private val newChaptersHeader = RecentMangaHeaderItem(RecentMangaHeaderItem.NEW_CHAPTERS)
    private val continueReadingHeader =
        RecentMangaHeaderItem(RecentMangaHeaderItem.CONTINUE_READING)
    var finished = false
    private var shouldMoveToTop = false
    var viewType: RecentsViewType = RecentsViewType.valueOf(uiPreferences.recentsViewType().get())
        private set
    var groupHistory: GroupType = preferences.groupChaptersHistory().get()
        private set
    val expandedSectionsMap = mutableMapOf<String, Boolean>()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private fun resetOffsets() {
        finished = false
        shouldMoveToTop = true
        pageOffset = 0
        expandedSectionsMap.clear()
    }

    private var pageOffset = 0
    var isLoading = false
        private set

    private val isOnFirstPage: Boolean
        get() = pageOffset == 0

    override fun onCreate() {
        super.onCreate()
        downloadManager.addListener(this)
        DownloadJob.downloadFlow.onEach(::downloadStatusChanged).launchIn(presenterScope)
        LibraryUpdateJob.updateFlow.onEach(::onUpdateManga).launchIn(presenterScope)
        if (lastRecents != null) {
            if (recentItems.isEmpty()) {
                recentItems = lastRecents ?: emptyList()
            }
            lastRecents = null
        }
        getRecents()
        listOf(
            preferences.groupChaptersHistory(),
            recentsPreferences.showReadInAllRecents(),
            preferences.sortFetchedTime(),
        ).forEach {
            it.changes()
                .drop(1)
                .onEach {
                    resetOffsets()
                    getRecents()
                }
                .launchIn(presenterScope)
        }
    }

    fun getRecents(updatePageCount: Boolean = false) {
        val oldQuery = query
        recentsJob?.cancel()
        recentsJob = presenterScope.launch {
            runRecents(oldQuery, updatePageCount)
        }
    }

    /**
     * Gets a set of recent entries based on preferred view type, unless changed by [customViewType]
     *
     * @param oldQuery used to determine while running this method the query has changed, and to cancel this
     * @param updatePageCount make true when fetching for more pages in the pagination scroll, otherwise make false to restart the list
     * @param retryCount used to not burden the db with infinite calls, should not be set as its a recursive param
     * @param itemCount also used in recursion to know how many items have been collected so far
     * @param limit used by the companion method to not recursively call this method, since the first batch is good enough
     * @param customViewType used to decide to use another view type instead of the one saved by preferences
     * @param includeReadAnyway also used by companion method to include the read manga, by default only unread manga is used
     */
    private suspend fun runRecents(
        oldQuery: String = "",
        updatePageCount: Boolean = false,
        retryCount: Int = 0,
        itemCount: Int = 0,
        limit: Int = -1,
        customViewType: RecentsViewType? = null,
        includeReadAnyway: Boolean = false,
    ) {
        if (retryCount > 5) {
            finished = true
            setDownloadedChapters(recentItems)
            if (customViewType == null) {
                withContext(Dispatchers.Main) {
                    view?.showLists(recentItems, false)
                    isLoading = false
                }
            }
            return
        }
        val viewType = customViewType ?: viewType

        val showRead = ((recentsPreferences.showReadInAllRecents().get() || query.isNotEmpty()) && limit != 0) || includeReadAnyway
        val isUngrouped = viewType != RecentsViewType.GroupedAll || query.isNotEmpty()
        val groupChaptersHistory = preferences.groupChaptersHistory().get()
        groupHistory = groupChaptersHistory

        val isCustom = customViewType != null
        val isEndless = isUngrouped && limit != 0
        var extraCount = 0
        val cReading: List<MangaChapterHistory> = when (viewType) {
            RecentsViewType.GroupedAll, RecentsViewType.UngroupedAll -> {
                getRecents.awaitAll(
                    showRead,
                    true,
                    isEndless,
                    !updatePageCount && !isOnFirstPage,
                    query,
                    (if (isCustom) ENDLESS_LIMIT else pageOffset).toLong(),
                )
            }
            RecentsViewType.History -> {
                val items = if (groupChaptersHistory == GroupType.BySeries) {
                    getRecents.awaitBySeries(
                        true,
                        !updatePageCount && !isOnFirstPage,
                        query,
                        (if (isCustom) ENDLESS_LIMIT else pageOffset).toLong(),
                    )
                } else {
                    getRecents.awaitUngrouped(
                        true,
                        !updatePageCount && !isOnFirstPage,
                        query,
                        (if (isCustom) ENDLESS_LIMIT else pageOffset).toLong(),
                    )
                }
                if (groupChaptersHistory.isByTime) {
                    dateFormat.applyPattern(
                        when (groupChaptersHistory) {
                            GroupType.ByWeek -> "yyyy-w"
                            else -> "yyyy-MM-dd"
                        },
                    )
                    val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) % 7 + 1
                    dateFormat.calendar.firstDayOfWeek = dayOfWeek
                    items.groupBy {
                        val date = it.history.last_read
                        it.manga.id to if (date <= 0L) "-1" else dateFormat.format(Date(date))
                    }
                        .mapNotNull { (key, mchs) ->
                            val manga = mchs.first().manga
                            val chapters = mchs.map { mch ->
                                ChapterHistory(mch.chapter, mch.history)
                            }
                            extraCount += mchs.size - chapters.size
                            if (chapters.isEmpty()) return@mapNotNull null
                            val lastAmount = if (groupChaptersHistory == GroupType.ByDay) {
                                ENDLESS_LIMIT
                            } else {
                                recentItems.size
                            }
                            val existingItem = recentItems.takeLast(lastAmount).find {
                                val date = Date(it.mch.history.last_read)
                                key == it.manga_id to dateFormat.format(date)
                            }?.takeIf { updatePageCount }
                            val sort = Comparator<ChapterHistory> { c1, c2 ->
                                c2.history!!.last_read.compareTo(c1.history!!.last_read)
                            }
                            val (sortedChapters, firstChapter, subCount) =
                                setupExtraChapters(existingItem, chapters, sort)
                            extraCount += subCount
                            if (firstChapter == null) return@mapNotNull null
                            mchs.find { firstChapter.id == it.chapter.id }?.also {
                                it.extraChapters = sortedChapters
                            }
                        }
                } else {
                    items
                }
            }
            RecentsViewType.Updates -> {
                dateFormat.applyPattern("yyyy-MM-dd")
                dateFormat.calendar.firstDayOfWeek =
                    Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
                getRecents.awaitUpdates(
                    true,
                    !updatePageCount && !isOnFirstPage,
                    query,
                    (if (isCustom) ENDLESS_LIMIT else pageOffset).toLong(),
                ).groupBy {
                    val date = it.chapter.date_fetch
                    it.manga.id to if (date <= 0L) "-1" else dateFormat.format(Date(date))
                }
                    .mapNotNull { (key, mcs) ->
                        val manga = mcs.first().manga
                        val chapters = mcs.map { ChapterHistory(it.chapter) }
                        extraCount += mcs.size - chapters.size
                        if (chapters.isEmpty()) return@mapNotNull null
                        val existingItem = recentItems.takeLast(ENDLESS_LIMIT).find {
                            val date = Date(it.chapter.date_fetch)
                            key == it.manga_id to dateFormat.format(date)
                        }?.takeIf { updatePageCount }
                        val sort: Comparator<ChapterHistory> =
                            ChapterSort(manga, chapterFilter, preferences)
                                .sortComparator(true)
                        val (sortedChapters, firstChapter, subCount) =
                            setupExtraChapters(existingItem, chapters, sort)
                        extraCount += subCount
                        if (firstChapter == null) return@mapNotNull null
                        MangaChapterHistory(
                            manga,
                            firstChapter,
                            HistoryImpl().apply { last_read = firstChapter.date_fetch },
                            sortedChapters,
                        )
                    }
            }
        }
        val extraChapterCount = cReading.sumOf { it.extraChapters.size }
        if (cReading.size + extraChapterCount + extraCount < ENDLESS_LIMIT) {
            finished = true
        }

        if (!isCustom && (pageOffset == 0 || updatePageCount)) {
            pageOffset += cReading.size + extraChapterCount + extraCount
        }

        if (query != oldQuery) return
        val mangaList = cReading.distinctBy {
            if (query.isEmpty() && viewType.isAll) it.manga.id else it.chapter.id
        }.filter { mch ->
            if (updatePageCount && !isOnFirstPage && query.isEmpty()) {
                if (viewType.isAll) {
                    recentItems.none { mch.manga.id == it.mch.manga.id }
                } else {
                    recentItems.none { mch.chapter.id == it.mch.chapter.id }
                }
            } else {
                true
            }
        }
        val pairs: List<Pair<MangaChapterHistory, Chapter>> = mangaList.mapNotNull {
            val chapter: Chapter? = when {
                // If the chapter is read in history/all or this mch is for a newly added manga
                (it.chapter.read && !viewType.isUpdates) || it.chapter.id == null -> {
                    val unreadChapterIsAlreadyInList by lazy {
                        val fIndex = mangaList.indexOfFirst { item -> item.manga.id == it.manga.id }
                        (
                            updatePageCount && recentItems.any { item -> item.mch.manga.id == it.manga.id }
                            ) || fIndex < mangaList.indexOf(it)
                    }
                    if (viewType.isHistory && unreadChapterIsAlreadyInList) {
                        it.chapter
                    } else {
                        val nextChapter = getNextChapter(it.manga)
                            ?: if (showRead && it.chapter.id != null) it.chapter else null
                        if (viewType.isHistory && nextChapter?.id != null &&
                            nextChapter.id != it.chapter.id
                        ) {
                            it.extraChapters = listOf(ChapterHistory(it.chapter, it.history)) +
                                it.extraChapters
                        }
                        nextChapter
                    }
                }
                // if in all view type and mch is a newly updated item
                it.history.id == null && !viewType.isUpdates -> {
                    getFirstUpdatedChapter(it.manga, it.chapter)
                        ?: if ((showRead && it.chapter.id != null)) it.chapter else null
                }
                else -> it.chapter
            }
            if (chapter == null) {
                if ((query.isNotEmpty() || !viewType.isAll) && it.chapter.id != null) {
                    Pair(it, it.chapter)
                } else {
                    null
                }
            } else {
                Pair(it, chapter)
            }
        }
        val newItems = if (query.isEmpty() && !isUngrouped) {
            val nChaptersItems =
                pairs.asSequence()
                    .filter { it.first.history.id == null && it.first.chapter.id != null }
                    .sortedWith { f1, f2 ->
                        if (abs(f1.second.date_fetch - f2.second.date_fetch) <=
                            TimeUnit.HOURS.toMillis(12)
                        ) {
                            f2.second.date_upload.compareTo(f1.second.date_upload)
                        } else {
                            f2.second.date_fetch.compareTo(f1.second.date_fetch)
                        }
                    }
                    .take(4).map {
                        RecentMangaItem(it.first, it.second, newChaptersHeader)
                    }.toMutableList()
            val cReadingItems =
                pairs.filter { it.first.history.id != null }.take(9 - nChaptersItems.size).map {
                    RecentMangaItem(it.first, it.second, continueReadingHeader)
                }.toMutableList()
            if (nChaptersItems.isNotEmpty()) {
                nChaptersItems.add(RecentMangaItem(header = newChaptersHeader))
            }
            if (cReadingItems.isNotEmpty()) {
                cReadingItems.add(RecentMangaItem(header = continueReadingHeader))
            }
            val nAdditionsItems = pairs.filter { it.first.chapter.id == null }.take(4)
                .map { RecentMangaItem(it.first, it.second, newAdditionsHeader) }
            listOf(nChaptersItems, cReadingItems, nAdditionsItems).sortedByDescending {
                it.firstOrNull()?.mch?.history?.last_read ?: 0L
            }.flatten()
        } else {
            if (viewType.isUpdates) {
                val map =
                    TreeMap<Date, MutableList<Pair<MangaChapterHistory, Chapter>>> { d1, d2 ->
                        d2.compareTo(d1)
                    }
                val byDay = pairs.groupByTo(map) { getMapKey(it.first.history.last_read) }
                byDay.flatMap {
                    val dateItem = DateItem(it.key, true)
                    val sortByFetched = preferences.sortFetchedTime().get()
                    it.value
                        .map { item -> RecentMangaItem(item.first, item.second, dateItem) }
                        .sortedByDescending { item ->
                            if (sortByFetched) item.date_fetch else item.date_upload
                        }
                }
            } else {
                pairs.map { RecentMangaItem(it.first, it.second, null) }
            }
        }
        if (customViewType == null) {
            recentItems = if (isOnFirstPage || !updatePageCount) {
                newItems
            } else {
                recentItems + newItems
            }
        }
        val newCount = itemCount + newItems.size + newItems.sumOf { it.mch.extraChapters.size } + extraCount
        val hasNewItems = newItems.isNotEmpty()
        if (updatePageCount && (newCount < if (limit > 0) limit else 25) &&
            (viewType != RecentsViewType.GroupedAll || query.isNotEmpty()) && limit != 0
        ) {
            runRecents(oldQuery, true, retryCount + (if (hasNewItems) 0 else 1), newCount)
            return
        }
        if (limit == -1) {
            setDownloadedChapters(recentItems)
            if (customViewType == null) {
                withContext(Dispatchers.Main) {
                    view?.showLists(recentItems, hasNewItems, shouldMoveToTop)
                    isLoading = false
                    shouldMoveToTop = false
                }
            }
        }
    }

    private fun setupExtraChapters(
        existingItem: RecentMangaItem?,
        chapters: List<ChapterHistory>,
        sort: Comparator<ChapterHistory>,
    ): Triple<MutableList<ChapterHistory>, Chapter?, Int> {
        var extraCount = 0
        val firstChapter: Chapter
        var sortedChapters: MutableList<ChapterHistory>
        val reverseRead = !viewType.isHistory
        if (existingItem != null) {
            extraCount += chapters.size
            val newChapters = existingItem.mch.extraChapters + chapters
            sortedChapters = newChapters.sortedWith(sort).toMutableList()
            sortedChapters = (
                sortedChapters.filter { !it.read } +
                    sortedChapters.filter { it.read }
                        .run { if (reverseRead) reversed() else this }
                ).toMutableList()
            existingItem.mch.extraChapters = sortedChapters
            return Triple(mutableListOf(), null, extraCount)
        }
        if (chapters.size == 1) {
            firstChapter = chapters.first()
            sortedChapters = mutableListOf()
        } else {
            sortedChapters = chapters.sortedWith(sort).toMutableList()
            firstChapter = sortedChapters.firstOrNull { !it.read }
                ?: sortedChapters.run { if (reverseRead) last() else first() }
            sortedChapters.last()
            sortedChapters.remove(firstChapter)
            sortedChapters = (
                sortedChapters.filter { !it.read } +
                    sortedChapters.filter { it.read }
                        .run { if (reverseRead) reversed() else this }
                ).toMutableList()
        }
        return Triple(sortedChapters, firstChapter, extraCount)
    }

    private suspend fun getNextChapter(manga: Manga): Chapter? {
        val chapters = getChapter.awaitAll(manga)
        return ChapterSort(manga, chapterFilter, preferences).getNextUnreadChapter(chapters, false)
    }

    private suspend fun getFirstUpdatedChapter(manga: Manga, chapter: Chapter): Chapter? {
        val chapters = getChapter.awaitAll(manga)
        return chapters
            .sortedWith(ChapterSort(manga, chapterFilter, preferences).sortComparator(true)).find {
                !it.read && abs(it.date_fetch - chapter.date_fetch) <= TimeUnit.HOURS.toMillis(12)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadManager.removeListener(this)
        lastRecents = recentItems
    }

    fun toggleGroupRecents(pref: RecentsViewType, updatePref: Boolean = true) {
        if (updatePref) {
            uiPreferences.recentsViewType().set(pref.mainValue)
        }
        viewType = pref
        resetOffsets()
        getRecents()
    }

    /**
     * Finds and assigns the list of downloaded chapters.
     *
     * @param chapters the list of chapter from the database.
     */
    private fun setDownloadedChapters(chapters: List<RecentMangaItem>) {
        for (item in chapters.filter { it.chapter.id != null }) {
            if (downloadManager.isChapterDownloaded(item.chapter, item.mch.manga)) {
                item.status = Download.State.DOWNLOADED
            } else if (downloadManager.hasQueue()) {
                item.download = downloadManager.queue.find { it.chapter.id == item.chapter.id }
                item.status = item.download?.status ?: Download.State.default
            }

            item.downloadInfo = item.mch.extraChapters.map { chapter ->
                val downloadInfo = RecentMangaItem.DownloadInfo()
                downloadInfo.chapterId = chapter.id
                if (downloadManager.isChapterDownloaded(chapter, item.mch.manga)) {
                    downloadInfo.status = Download.State.DOWNLOADED
                } else if (downloadManager.hasQueue()) {
                    downloadInfo.download = downloadManager.queue.find { it.chapter.id == chapter.id }
                    downloadInfo.status = downloadInfo.download?.status ?: Download.State.default
                }
                downloadInfo
            }
        }
    }

    override fun updateDownload(download: Download) {
        recentItems.find {
            download.chapter.id == it.chapter.id ||
                download.chapter.id in it.mch.extraChapters.map { ch -> ch.id }
        }?.apply {
            if (chapter.id != download.chapter.id) {
                val downloadInfo = downloadInfo.find { it.chapterId == download.chapter.id }
                    ?: return@apply
                downloadInfo.download = download
            } else {
                this.download = download
            }
        }
        presenterScope.launchUI { view?.updateChapterDownload(download) }
    }

    override fun updateDownloads() {
        presenterScope.launch {
            setDownloadedChapters(recentItems)
            withContext(Dispatchers.Main) {
                view?.showLists(recentItems, true)
                view?.updateDownloadStatus(!downloadManager.isPaused())
            }
        }
    }

    private fun downloadStatusChanged(downloading: Boolean) {
        presenterScope.launchUI {
            view?.updateDownloadStatus(downloading)
        }
    }

    private fun onUpdateManga(mangaId: Long?) {
        when (mangaId) {
            null -> {
                presenterScope.launchUI { view?.setRefreshing(false) }
            }
            LibraryUpdateJob.STARTING_UPDATE_SOURCE -> {
                presenterScope.launchUI { view?.setRefreshing(true) }
            }
            else -> {
                getRecents()
            }
        }
    }

    /**
     * Deletes the given list of chapter.
     * @param chapter the chapter to delete.
     */
    fun deleteChapter(chapter: Chapter, manga: Manga, update: Boolean = true) {
        val source = Injekt.get<SourceManager>().getOrStub(manga.source)
        launchIO {
            downloadManager.deleteChapters(listOf(chapter), manga, source, true)
        }
        if (update) {
            val item = recentItems.find {
                chapter.id == it.chapter.id ||
                    chapter.id in it.mch.extraChapters.map { ch -> ch.id }
            } ?: return
            item.apply {
                if (chapter.id != item.chapter.id) {
                    val extraChapter = mch.extraChapters.find { it.id == chapter.id } ?: return@apply
                    val downloadInfo = downloadInfo.find { it.chapterId == chapter.id } ?: return@apply
                    if (extraChapter.bookmark && !preferences.removeBookmarkedChapters().get()) {
                        return@apply
                    }
                    downloadInfo.status = Download.State.NOT_DOWNLOADED
                    downloadInfo.download = null
                } else {
                    if (chapter.bookmark && !preferences.removeBookmarkedChapters().get()) {
                        return@apply
                    }
                    status = Download.State.NOT_DOWNLOADED
                    download = null
                }
            }

            view?.showLists(recentItems, true)
        }
    }

    /**
     * Get date as time key
     *
     * @param date desired date
     * @return date as time key
     */
    private fun getMapKey(date: Long): Date {
        val cal = Calendar.getInstance()
        cal.time = Date(date)
        cal[Calendar.HOUR_OF_DAY] = 0
        cal[Calendar.MINUTE] = 0
        cal[Calendar.SECOND] = 0
        cal[Calendar.MILLISECOND] = 0
        return cal.time
    }

    /**
     * Downloads the given list of chapters with the manager.
     * @param chapter the chapter to download.
     */
    fun downloadChapter(manga: Manga, chapter: Chapter) {
        downloadManager.downloadChapters(manga, listOf(chapter))
    }

    fun startDownloadChapterNow(chapter: Chapter) {
        downloadManager.startDownloadNow(chapter)
    }

    /**
     * Mark the selected chapter list as read/unread.
     * @param read whether to mark chapters as read or unread.
     */
    fun markChapterRead(
        chapter: Chapter,
        read: Boolean,
        lastRead: Int? = null,
        pagesLeft: Int? = null,
    ) {
        presenterScope.launchNonCancellableIO {
            chapter.apply {
                this.read = read
                if (!read) {
                    last_page_read = lastRead ?: 0
                    pages_left = pagesLeft ?: 0
                }
            }
            updateChapter.await(chapter.toProgressUpdate())
            getRecents()
        }
    }

    // History
    /**
     * Reset last read of chapter to 0L
     * @param history history belonging to chapter
     */
    fun removeFromHistory(history: History) {
        presenterScope.launchNonCancellableIO {
            history.last_read = 0L
            history.time_read = 0L
            upsertHistory.await(history)
            getRecents()
        }
    }

    /**
     * Removes all chapters belonging to manga from history.
     * @param mangaId id of manga
     */
    fun removeAllFromHistory(mangaId: Long) {
        presenterScope.launchNonCancellableIO {
            val histories = getHistory.awaitAllByMangaId(mangaId).map {
                it.apply {
                    last_read = 0L
                    time_read = 0L
                }
            }
            upsertHistory.awaitBulk(histories)
            getRecents()
        }
    }

    fun requestNext() {
        if (!isLoading) {
            isLoading = true
            getRecents(true)
        }
    }

    fun deleteAllHistory() {
        presenterScope.launchNonCancellableIO {
            handler.await { historyQueries.deleteAll() }
            withUIContext {
                view?.activity?.toast(MR.strings.clear_history_completed)
                getRecents()
            }
        }
    }

    enum class GroupType {
        BySeries,
        ByWeek,
        ByDay,
        Never,
        ;

        val isByTime get() = this == ByWeek || this == ByDay
    }

    companion object {
        private var lastRecents: List<RecentMangaItem>? = null

        fun onLowMemory() {
            lastRecents = null
        }

        const val ENDLESS_LIMIT = 50
        var SHORT_LIMIT = 25
            private set

        suspend fun getRecentManga(includeRead: Boolean = false, customAmount: Int = 0): List<Pair<Manga, Long>> {
            val presenter = RecentsPresenter()
            presenter.viewType = RecentsViewType.UngroupedAll
            SHORT_LIMIT = when {
                customAmount > 0 -> (customAmount * 1.5).roundToInt()
                includeRead -> 50
                else -> 25
            }
            presenter.runRecents(limit = customAmount, includeReadAnyway = includeRead)
            SHORT_LIMIT = 25
            return presenter.recentItems
                .filter { it.mch.manga.id != null }
                .map { it.mch.manga to it.mch.history.last_read }
        }
    }
}
