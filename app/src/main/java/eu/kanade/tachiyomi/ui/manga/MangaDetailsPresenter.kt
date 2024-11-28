package eu.kanade.tachiyomi.ui.manga

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toFile
import co.touchlab.kermit.Logger
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.hippo.unifile.UniFile
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.models.bookmarkedFilter
import eu.kanade.tachiyomi.data.database.models.chapterOrder
import eu.kanade.tachiyomi.data.database.models.downloadedFilter
import eu.kanade.tachiyomi.data.database.models.prepareCoverUpdate
import eu.kanade.tachiyomi.data.database.models.readFilter
import eu.kanade.tachiyomi.data.database.models.removeCover
import eu.kanade.tachiyomi.data.database.models.sortDescending
import eu.kanade.tachiyomi.data.database.models.updateCoverLastModified
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.download.model.DownloadQueue
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.SourceNotFoundException
import eu.kanade.tachiyomi.source.getExtension
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterItem
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.tachiyomi.ui.manga.track.TrackingBottomSheet
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate
import eu.kanade.tachiyomi.util.chapter.ChapterFilter
import eu.kanade.tachiyomi.util.chapter.ChapterSort
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithTrackServiceTwoWay
import eu.kanade.tachiyomi.util.chapter.updateTrackChapterMarkedAsRead
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.lang.trimOrNull
import eu.kanade.tachiyomi.util.manga.MangaShortcutManager
import eu.kanade.tachiyomi.util.manga.MangaUtil
import eu.kanade.tachiyomi.util.shouldDownloadNewChapters
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchNonCancellableIO
import eu.kanade.tachiyomi.util.system.launchNow
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.withIOContext
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.widget.TriStateCheckBox
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.chapter.interactor.GetAvailableScanlators
import yokai.domain.chapter.interactor.GetChapter
import yokai.domain.chapter.interactor.UpdateChapter
import yokai.domain.library.custom.model.CustomMangaInfo
import yokai.domain.manga.interactor.GetManga
import yokai.domain.manga.interactor.UpdateManga
import yokai.domain.manga.models.MangaUpdate
import yokai.domain.manga.models.cover
import yokai.domain.storage.StorageManager
import yokai.domain.track.interactor.GetTrack
import yokai.i18n.MR
import yokai.util.lang.getString

class MangaDetailsPresenter(
    val mangaId: Long,
    val sourceManager: SourceManager = Injekt.get(),
    val preferences: PreferencesHelper = Injekt.get(),
    val coverCache: CoverCache = Injekt.get(),
    val db: DatabaseHelper = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val chapterFilter: ChapterFilter = Injekt.get(),
    private val storageManager: StorageManager = Injekt.get(),
) : BaseCoroutinePresenter<MangaDetailsController>(), DownloadQueue.DownloadListener {
    private val getAvailableScanlators: GetAvailableScanlators by injectLazy()
    private val getChapter: GetChapter by injectLazy()
    private val getManga: GetManga by injectLazy()
    private val updateChapter: UpdateChapter by injectLazy()
    private val updateManga: UpdateManga by injectLazy()
    private val getTrack: GetTrack by injectLazy()

    private val networkPreferences: NetworkPreferences by injectLazy()

//    private val currentMangaInternal: MutableStateFlow<Manga?> = MutableStateFlow(null)
//    val currentManga get() = currentMangaInternal.asStateFlow()

    lateinit var manga: Manga
    fun isMangaLateInitInitialized() = ::manga.isInitialized

    private val customMangaManager: CustomMangaManager by injectLazy()
    private val mangaShortcutManager: MangaShortcutManager by injectLazy()

    val source: Source by lazy { sourceManager.getOrStub(manga.source) }

    private lateinit var chapterSort: ChapterSort
    val extension by lazy { (source as? HttpSource)?.getExtension() }

    var isLockedFromSearch = false
    var hasRequested = false
    var isLoading = false
    var scrollType = 0

    private val loggedServices by lazy { Injekt.get<TrackManager>().services.filter { it.isLogged } }
    private var tracks = emptyList<Track>()

    var trackList: List<TrackItem> = emptyList()

    var chapters: List<ChapterItem> = emptyList()
        private set

    var allChapters: List<ChapterItem> = emptyList()
        private set

    var allHistory: List<History> = emptyList()
        private set

    val headerItem: MangaHeaderItem by lazy { MangaHeaderItem(mangaId, view?.fromCatalogue == true)}
    var tabletChapterHeaderItem: MangaHeaderItem? = null
        get() {
            when (view?.isTablet) {
                true -> if (field == null) {
                    field = MangaHeaderItem(mangaId, false).apply {
                        isChapterHeader = true
                    }
                }
                else -> if (field != null) {
                    field = null
                }
            }
            return field
        }
        private set

    var allChapterScanlators: Set<String> = emptySet()

    override fun onCreate() {
        val controller = view ?: return

        isLockedFromSearch = controller.shouldLockIfNeeded && SecureActivityDelegate.shouldBeLocked()
        if (!::manga.isInitialized) runBlocking { refreshMangaFromDb() }
        syncData()

        downloadManager.addListener(this)

        runBlocking {
            tracks = getTrack.awaitAllByMangaId(manga.id!!)
        }
    }

    /**
     * onCreate but executed after UI layout is ready otherwise it'd only show blank screen
     */
    fun onCreateLate() {
        val controller = view ?: return

        LibraryUpdateJob.updateFlow
            .filter { it == mangaId }
            .onEach { onUpdateManga() }
            .launchIn(presenterScope)

        if (manga.isLocal()) {
            refreshAll()
        } else if (!manga.initialized) {
            isLoading = true
            controller.setRefresh(true)
            controller.updateHeader()
            refreshAll()
        } else {
            runBlocking { getChapters() }
            controller.updateChapters(this.chapters)
            getHistory()
        }

        presenterScope.launch {
            setTrackItems()
        }

        refreshTracking(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadManager.removeListener(this)
    }

    fun fetchChapters(andTracking: Boolean = true) {
        presenterScope.launch {
            getChapters()
            if (andTracking) fetchTracks()
            withContext(Dispatchers.Main) { view?.updateChapters(chapters) }
            getHistory()
        }
    }

    fun setCurrentManga(manga: Manga?) {
//        currentMangaInternal.update { manga }
        this.manga = manga!!
    }

    // TODO: Use flow to "sync" data instead
    fun syncData() {
        chapterSort = ChapterSort(manga, chapterFilter, preferences)
        headerItem.apply {
            isTablet = view?.isTablet == true
            isLocked = isLockedFromSearch
        }
    }

    suspend fun getChaptersNow(): List<ChapterItem> {
        getChapters()
        return chapters
    }

    private suspend fun getChapters() {
        val chapters = getChapter.awaitAll(mangaId, isScanlatorFiltered()).map { it.toModel() }
        allChapters = if (!isScanlatorFiltered()) chapters else getChapter.awaitAll(mangaId, false).map { it.toModel() }

        // Find downloaded chapters
        setDownloadedChapters(chapters)
        allChapterScanlators = allChapters.mapNotNull { it.chapter.scanlator }.toSet()

        this.chapters = applyChapterFilters(chapters)
    }

    private fun getHistory() {
        presenterScope.launchIO {
            allHistory = db.getHistoryByMangaId(mangaId).executeAsBlocking()
        }
    }

    /**
     * Finds and assigns the list of downloaded chapters.
     *
     * @param chapters the list of chapter from the database.
     */
    private fun setDownloadedChapters(chapters: List<ChapterItem>) {
        for (chapter in chapters) {
            if (downloadManager.isChapterDownloaded(chapter, manga)) {
                chapter.status = Download.State.DOWNLOADED
            } else if (downloadManager.hasQueue()) {
                chapter.status = downloadManager.queue.find { it.chapter.id == chapter.id }
                    ?.status ?: Download.State.default
            }
        }
    }

    override fun updateDownload(download: Download) {
        chapters.find { it.id == download.chapter.id }?.download = download
        presenterScope.launchUI {
            view?.updateChapterDownload(download)
        }
    }

    override fun updateDownloads() {
        presenterScope.launch(Dispatchers.Default) {
            getChapters()
            withContext(Dispatchers.Main) {
                view?.updateChapters(chapters)
            }
        }
    }

    /**
     * Converts a chapter from the database to an extended model, allowing to store new fields.
     */
    private fun Chapter.toModel(): ChapterItem {
        // Create the model object.
        val model = ChapterItem(this, manga)
        model.isLocked = isLockedFromSearch

        // Find an active download for this chapter.
        val download = downloadManager.queue.find { it.chapter.id == id }

        if (download != null) {
            // If there's an active download, assign it.
            model.download = download
        }
        return model
    }

    /**
     * Whether the sorting method is descending or ascending.
     */
    fun sortDescending() = manga.sortDescending(preferences)

    fun sortingOrder() = manga.chapterOrder(preferences)

    /**
     * Applies the view filters to the list of chapters obtained from the database.
     * @param chapterList the list of chapters from the database
     * @return an observable of the list of chapters filtered and sorted.
     */
    private fun applyChapterFilters(chapterList: List<ChapterItem>): List<ChapterItem> {
        if (isLockedFromSearch) {
            return chapterList
        }
        getScrollType(chapterList)
        return chapterSort.getChaptersSorted(chapterList)
    }

    fun getChapterUrl(chapter: Chapter): String? {
        val source = source as? HttpSource ?: return null
        val chapterUrl = try { source.getChapterUrl(chapter) } catch (_: Exception) { null }
        return chapterUrl.takeIf { !it.isNullOrBlank() }
            ?: try { source.getChapterUrl(manga, chapter) } catch (_: Exception) { null }
    }

    private fun getScrollType(chapters: List<ChapterItem>) {
        scrollType = when {
            ChapterUtil.hasMultipleVolumes(chapters) -> MULTIPLE_VOLUMES
            ChapterUtil.hasMultipleSeasons(chapters) -> MULTIPLE_SEASONS
            ChapterUtil.hasTensOfChapters(chapters) -> TENS_OF_CHAPTERS
            else -> 0
        }
    }

    /**
     * Returns the next unread chapter or null if everything is read.
     */
    fun getNextUnreadChapter(): ChapterItem? {
        return chapterSort.getNextUnreadChapter(chapters)
    }

    fun anyRead(): Boolean = allChapters.any { it.read }
    fun hasBookmark(): Boolean = allChapters.any { it.bookmark }
    fun hasDownloads(): Boolean = allChapters.any { it.isDownloaded }

    fun getUnreadChaptersSorted() =
        chapters.filter { !it.read && it.status == Download.State.NOT_DOWNLOADED }.distinctBy { it.name }
            .sortedWith(chapterSort.sortComparator(true))

    fun startDownloadingNow(chapter: Chapter) {
        downloadManager.startDownloadNow(chapter)
    }

    /**
     * Downloads the given list of chapters with the manager.
     * @param chapters the list of chapters to download.
     */
    fun downloadChapters(chapters: List<ChapterItem>) {
        downloadManager.downloadChapters(manga, chapters.filter { !it.isDownloaded })
    }

    /**
     * Deletes the given list of chapter.
     * @param chapter the chapter to delete.
     */
    fun deleteChapter(chapter: ChapterItem) {
        downloadManager.deleteChapters(listOf(chapter), manga, source, true)
        this.chapters.find { it.id == chapter.id }?.apply {
            if (chapter.chapter.bookmark && !preferences.removeBookmarkedChapters().get()) return@apply
            status = Download.State.QUEUE
            download = null
        }

        view?.updateChapters(this.chapters)
    }

    /**
     * Deletes the given list of chapter.
     * @param chapters the list of chapters to delete.
     */
    fun deleteChapters(chapters: List<ChapterItem>, update: Boolean = true, isEverything: Boolean = false) {
        launchIO {
            if (isEverything) {
                downloadManager.deleteManga(manga, source)
            } else {
                downloadManager.deleteChapters(chapters, manga, source)
            }
        }
        chapters.forEach { chapter ->
            this.chapters.find { it.id == chapter.id }?.apply {
                if (chapter.chapter.bookmark && !preferences.removeBookmarkedChapters().get() && !isEverything) return@apply
                status = Download.State.QUEUE
                download = null
            }
        }

        if (update) view?.updateChapters(this.chapters)
    }

    suspend fun refreshMangaFromDb(): Manga {
        val dbManga = getManga.awaitById(mangaId)!!
        setCurrentManga(dbManga)
        return dbManga
    }

    /** Refresh Manga Info and Chapter List (not tracking) */
    fun refreshAll() {
        if (view?.isNotOnline() == true && !manga.isLocal()) return
        presenterScope.launch {
            isLoading = true
            var mangaError: java.lang.Exception? = null
            var chapterError: java.lang.Exception? = null
            val chapters = async(Dispatchers.IO) {
                try {
                    source.getChapterList(manga.copy())
                } catch (e: Exception) {
                    chapterError = e
                    emptyList()
                }
            }
            val nManga = async(Dispatchers.IO) {
                try {
                    source.getMangaDetails(manga.copy())
                } catch (e: java.lang.Exception) {
                    mangaError = e
                    null
                }
            }

            val networkManga = nManga.await()
            if (networkManga != null) {
                manga.prepareCoverUpdate(coverCache, networkManga, false)
                manga.copyFrom(networkManga)
                manga.initialized = true

                db.insertManga(manga).executeAsBlocking()

                launchIO {
                    val request =
                        ImageRequest.Builder(preferences.context).data(manga.cover())
                            .memoryCachePolicy(CachePolicy.DISABLED)
                            .diskCachePolicy(CachePolicy.WRITE_ONLY)
                            .build()

                    if (preferences.context.imageLoader.execute(request) is SuccessResult) {
                        withContext(Dispatchers.Main) {
                            view?.setPaletteColor()
                        }
                    }
                }
            }
            val finChapters = chapters.await()
            if (finChapters.isNotEmpty()) {
                val newChapters = withIOContext { syncChaptersWithSource(finChapters, manga, source) }
                if (newChapters.first.isNotEmpty()) {
                    if (manga.shouldDownloadNewChapters(db, preferences)) {
                        downloadChapters(
                            newChapters.first.sortedBy { it.chapter_number }
                                .map { it.toModel() },
                        )
                    }
                    view?.view?.context?.let { mangaShortcutManager.updateShortcuts(it) }
                }
                if (newChapters.second.isNotEmpty()) {
                    val removedChaptersId = newChapters.second.map { it.id }
                    val removedChapters = this@MangaDetailsPresenter.chapters.filter {
                        it.id in removedChaptersId && it.isDownloaded
                    }
                    if (removedChapters.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            view?.showChaptersRemovedPopup(
                                removedChapters,
                            )
                        }
                    }
                }
                getChapters()
            }
            isLoading = false
            if (chapterError == null) {
                withContext(Dispatchers.Main) {
                    view?.updateChapters(this@MangaDetailsPresenter.chapters)
                }
            }
            if (chapterError != null) {
                withContext(Dispatchers.Main) {
                    view?.showError(
                        trimException(chapterError!!),
                    )
                }
                return@launch
            } else if (mangaError != null) {
                withContext(Dispatchers.Main) {
                    view?.showError(
                        trimException(mangaError!!),
                    )
                }
            }
            getHistory()
        }
    }

    /**
     * Requests an updated list of chapters from the source.
     */
    fun fetchChaptersFromSource() {
        hasRequested = true
        isLoading = true

        presenterScope.launch(Dispatchers.IO) {
            val chapters = try {
                source.getChapterList(manga.copy())
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { view?.showError(trimException(e)) }
                return@launch
            }
            isLoading = false
            try {
                syncChaptersWithSource(chapters, manga, source)

                getChapters()
                withContext(Dispatchers.Main) {
                    view?.updateChapters(this@MangaDetailsPresenter.chapters)
                }
                getHistory()
            } catch (e: java.lang.Exception) {
                withContext(Dispatchers.Main) {
                    view?.showError(trimException(e))
                }
            }
        }
    }

    private fun trimException(e: java.lang.Exception): String {
        return (
            if (e !is SourceNotFoundException &&
                e.message?.contains(": ") == true
            ) {
                e.message?.split(": ")?.drop(1)
                    ?.joinToString(": ")
            } else {
                e.message
            }
            ) ?: view?.view?.context?.getString(MR.strings.unknown_error) ?: ""
    }

    /**
     * Bookmarks the given list of chapters.
     * @param selectedChapters the list of chapters to bookmark.
     */
    fun bookmarkChapters(selectedChapters: List<ChapterItem>, bookmarked: Boolean) {
        presenterScope.launchNonCancellableIO {
            val updates = selectedChapters.map {
                it.bookmark = bookmarked
                it.toProgressUpdate()
            }
            updateChapter.awaitAll(updates)
            getChapters()
            withContext(Dispatchers.Main) { view?.updateChapters(chapters) }
        }
    }

    /**
     * Mark the selected chapter list as read/unread.
     * @param selectedChapters the list of selected chapters.
     * @param read whether to mark chapters as read or unread.
     */
    fun markChaptersRead(
        selectedChapters: List<ChapterItem>,
        read: Boolean,
        deleteNow: Boolean = true,
        lastRead: Int? = null,
        pagesLeft: Int? = null,
    ) {
        presenterScope.launchNonCancellableIO {
            val updates = selectedChapters.map {
                it.read = read
                if (!read) {
                    it.last_page_read = lastRead ?: 0
                    it.pages_left = pagesLeft ?: 0
                }
                it.toProgressUpdate()
            }
            updateChapter.awaitAll(updates)
            if (read && deleteNow && preferences.removeAfterMarkedAsRead().get()) {
                deleteChapters(selectedChapters, false)
            }
            getChapters()
            withContext(Dispatchers.Main) { view?.updateChapters(chapters) }
            if (read && deleteNow) {
                val latestReadChapter = selectedChapters.maxByOrNull { it.chapter_number.toInt() }?.chapter
                updateTrackChapterMarkedAsRead(db, preferences, latestReadChapter, manga.id) {
                    fetchTracks()
                }
            }
        }
    }

    /**
     * Sets the sorting order and requests an UI update.
     */
    fun setSortOrder(sort: Int, descend: Boolean) {
        manga.setChapterOrder(sort, if (descend) Manga.CHAPTER_SORT_DESC else Manga.CHAPTER_SORT_ASC)
        if (mangaSortMatchesDefault()) {
            manga.setSortToGlobal()
        }
        presenterScope.launchNonCancellableIO { asyncUpdateMangaAndChapters() }
    }

    fun setGlobalChapterSort(sort: Int, descend: Boolean) {
        preferences.sortChapterOrder().set(sort)
        preferences.chaptersDescAsDefault().set(descend)
        manga.setSortToGlobal()
        presenterScope.launchNonCancellableIO { asyncUpdateMangaAndChapters() }
    }

    fun mangaSortMatchesDefault(): Boolean {
        return (
            manga.sortDescending == preferences.chaptersDescAsDefault().get() &&
                manga.sorting == preferences.sortChapterOrder().get()
            ) || !manga.usesLocalSort
    }

    fun mangaFilterMatchesDefault(): Boolean {
        return (
            manga.readFilter == preferences.filterChapterByRead().get() &&
                manga.downloadedFilter == preferences.filterChapterByDownloaded().get() &&
                manga.bookmarkedFilter == preferences.filterChapterByBookmarked().get() &&
                manga.hideChapterTitles == preferences.hideChapterTitlesByDefault().get()
            ) || !manga.usesLocalFilter
    }

    fun resetSortingToDefault() {
        manga.setSortToGlobal()
        presenterScope.launchNonCancellableIO { asyncUpdateMangaAndChapters() }
    }

    /**
     * Removes all filters and requests an UI update.
     */
    fun setFilters(
        unread: TriStateCheckBox.State,
        downloaded: TriStateCheckBox.State,
        bookmarked: TriStateCheckBox.State,
    ) {
        manga.readFilter = when (unread) {
            TriStateCheckBox.State.CHECKED -> Manga.CHAPTER_SHOW_UNREAD
            TriStateCheckBox.State.IGNORE -> Manga.CHAPTER_SHOW_READ
            else -> Manga.SHOW_ALL
        }
        manga.downloadedFilter = when (downloaded) {
            TriStateCheckBox.State.CHECKED -> Manga.CHAPTER_SHOW_DOWNLOADED
            TriStateCheckBox.State.IGNORE -> Manga.CHAPTER_SHOW_NOT_DOWNLOADED
            else -> Manga.SHOW_ALL
        }
        manga.bookmarkedFilter = when (bookmarked) {
            TriStateCheckBox.State.CHECKED -> Manga.CHAPTER_SHOW_BOOKMARKED
            TriStateCheckBox.State.IGNORE -> Manga.CHAPTER_SHOW_NOT_BOOKMARKED
            else -> Manga.SHOW_ALL
        }
        manga.setFilterToLocal()
        if (mangaFilterMatchesDefault()) {
            manga.setFilterToGlobal()
        }
        presenterScope.launchNonCancellableIO { asyncUpdateMangaAndChapters() }
    }

    /**
     * Sets the active display mode.
     * @param hide set title to hidden
     */
    fun hideTitle(hide: Boolean) {
        manga.displayMode = if (hide) Manga.CHAPTER_DISPLAY_NUMBER else Manga.CHAPTER_DISPLAY_NAME
        manga.setFilterToLocal()
        presenterScope.launchNonCancellableIO { updateManga.await(MangaUpdate(manga.id!!, chapterFlags = manga.chapter_flags)) }
        if (mangaFilterMatchesDefault()) {
            manga.setFilterToGlobal()
        }
        view?.refreshAdapter()
    }

    fun resetFilterToDefault() {
        manga.setFilterToGlobal()
        presenterScope.launchNonCancellableIO { asyncUpdateMangaAndChapters() }
    }

    fun setGlobalChapterFilters(
        unread: TriStateCheckBox.State,
        downloaded: TriStateCheckBox.State,
        bookmarked: TriStateCheckBox.State,
    ) {
        preferences.filterChapterByRead().set(
            when (unread) {
                TriStateCheckBox.State.CHECKED -> Manga.CHAPTER_SHOW_UNREAD
                TriStateCheckBox.State.IGNORE -> Manga.CHAPTER_SHOW_READ
                else -> Manga.SHOW_ALL
            },
        )
        preferences.filterChapterByDownloaded().set(
            when (downloaded) {
                TriStateCheckBox.State.CHECKED -> Manga.CHAPTER_SHOW_DOWNLOADED
                TriStateCheckBox.State.IGNORE -> Manga.CHAPTER_SHOW_NOT_DOWNLOADED
                else -> Manga.SHOW_ALL
            },
        )
        preferences.filterChapterByBookmarked().set(
            when (bookmarked) {
                TriStateCheckBox.State.CHECKED -> Manga.CHAPTER_SHOW_BOOKMARKED
                TriStateCheckBox.State.IGNORE -> Manga.CHAPTER_SHOW_NOT_BOOKMARKED
                else -> Manga.SHOW_ALL
            },
        )
        preferences.hideChapterTitlesByDefault().set(manga.hideChapterTitles)
        manga.setFilterToGlobal()
        presenterScope.launchNonCancellableIO { asyncUpdateMangaAndChapters() }
    }

    private suspend fun asyncUpdateMangaAndChapters(justChapters: Boolean = false) {
        if (!justChapters) updateManga.await(MangaUpdate(manga.id!!, chapterFlags = manga.chapter_flags))
        getChapters()
        withUIContext { view?.updateChapters(chapters) }
    }

    private fun isScanlatorFiltered() = manga.filtered_scanlators?.isNotEmpty() == true

    fun currentFilters(): String {
        val filtersId = mutableListOf<StringResource?>()
        filtersId.add(if (manga.readFilter(preferences) == Manga.CHAPTER_SHOW_READ) MR.strings.read else null)
        filtersId.add(if (manga.readFilter(preferences) == Manga.CHAPTER_SHOW_UNREAD) MR.strings.unread else null)
        filtersId.add(if (manga.downloadedFilter(preferences) == Manga.CHAPTER_SHOW_DOWNLOADED) MR.strings.downloaded else null)
        filtersId.add(if (manga.downloadedFilter(preferences) == Manga.CHAPTER_SHOW_NOT_DOWNLOADED) MR.strings.not_downloaded else null)
        filtersId.add(if (manga.bookmarkedFilter(preferences) == Manga.CHAPTER_SHOW_BOOKMARKED) MR.strings.bookmarked else null)
        filtersId.add(if (manga.bookmarkedFilter(preferences) == Manga.CHAPTER_SHOW_NOT_BOOKMARKED) MR.strings.not_bookmarked else null)
        filtersId.add(if (isScanlatorFiltered()) MR.strings.scanlators else null)
        return filtersId.filterNotNull()
            .joinToString(", ") { view?.view?.context?.getString(it) ?: "" }
    }

    fun setScanlatorFilter(filteredScanlators: Set<String>) {
        presenterScope.launchNonCancellableIO {
            val manga = manga
            MangaUtil.setScanlatorFilter(
                updateManga,
                manga,
                if (filteredScanlators.size == allChapterScanlators.size) emptySet() else filteredScanlators
            )
            asyncUpdateMangaAndChapters(true)
        }
    }

    fun toggleFavorite(): Boolean {
        manga.favorite = !manga.favorite

        when (manga.favorite) {
            true -> {
                manga.date_added = Date().time
            }
            false -> manga.date_added = 0
        }

        db.insertManga(manga).executeAsBlocking()
        view?.updateHeader()
        return manga.favorite
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    fun getCategories(): List<Category> {
        return db.getCategories().executeAsBlocking()
    }

    fun confirmDeletion() {
        presenterScope.launchNonCancellableIO {
            manga.removeCover(coverCache)
            customMangaManager.saveMangaInfo(CustomMangaInfo(
                mangaId = manga.id!!,
                title = null,
                author = null,
                artist = null,
                description = null,
                genre = null,
                status = null,
            ))
            downloadManager.deleteManga(manga, source)
            asyncUpdateMangaAndChapters(true)
        }
    }

    private fun onUpdateManga() = fetchChapters()

    fun shareManga() {
        val context = Injekt.get<Application>()

        val destDir = UniFile.fromFile(context.cacheDir)!!.createDirectory("shared_image")!!

        presenterScope.launchIO {
            try {
                val uri = saveCover(destDir)
                withUIContext {
                    view?.shareManga(uri.uri.toFile())
                }
            } catch (_: java.lang.Exception) {
            }
        }
    }

    private fun saveImage(cover: Bitmap, directory: File, manga: Manga): File? {
        directory.mkdirs()

        // Build destination file.
        val filename = DiskUtil.buildValidFilename("${manga.title} - Cover.jpg")

        val destFile = File(directory, filename)
        val stream: OutputStream = FileOutputStream(destFile)
        cover.compress(Bitmap.CompressFormat.JPEG, 75, stream)
        stream.flush()
        stream.close()
        return destFile
    }

    fun updateManga(
        title: String?,
        author: String?,
        artist: String?,
        uri: Uri?,
        description: String?,
        tags: Array<String>?,
        status: Int?,
        seriesType: Int?,
        lang: String?,
        resetCover: Boolean = false,
    ) {
        if (manga.isLocal()) {
            manga.title = if (title.isNullOrBlank()) manga.url else title.trim()
            manga.author = author?.trimOrNull()
            manga.artist = artist?.trimOrNull()
            manga.description = description?.trimOrNull()
            val tagsString = tags?.joinToString(", ") { tag ->
                tag.replaceFirstChar {
                    it.uppercase(Locale.getDefault())
                }
            }
            manga.genre = if (tags.isNullOrEmpty()) null else tagsString?.trim()
            if (seriesType != null) {
                manga.genre = setSeriesType(seriesType, manga.genre).joinToString(", ") {
                    it.replaceFirstChar { genre ->
                        genre.titlecase(Locale.getDefault())
                    }
                }
                manga.viewer_flags = -1
                presenterScope.launchIO { updateManga.await(MangaUpdate(manga.id!!, viewerFlags = manga.viewer_flags)) }
            }
            manga.status = status ?: SManga.UNKNOWN
            LocalSource(downloadManager.context).updateMangaInfo(manga, lang)
            presenterScope.launchIO {
                updateManga.await(
                    MangaUpdate(
                        manga.id!!,
                        title = manga.ogTitle,
                        author = manga.originalAuthor,
                        artist = manga.originalArtist,
                        description = manga.originalDescription,
                        genres = manga.originalGenre?.split(", ").orEmpty(),
                        status = manga.ogStatus,
                    )
                )
            }
        } else {
            var genre = if (!tags.isNullOrEmpty() && tags.joinToString(", ") != manga.originalGenre) {
                tags.map { tag -> tag.replaceFirstChar { it.titlecase(Locale.getDefault()) } }
                    .toTypedArray()
            } else {
                null
            }
            if (seriesType != null) {
                genre = setSeriesType(seriesType, genre?.joinToString())
                manga.viewer_flags = -1
                presenterScope.launchIO { updateManga.await(MangaUpdate(manga.id!!, viewerFlags = manga.viewer_flags)) }
            }
            val manga = CustomMangaInfo(
                mangaId = manga.id!!,
                title?.trimOrNull(),
                author?.trimOrNull(),
                artist?.trimOrNull(),
                description?.trimOrNull(),
                genre?.joinToString(),
                if (status != this.manga.ogStatus) status else null,
            )
            launchNow {
                customMangaManager.saveMangaInfo(manga)
            }
        }
        if (uri != null) {
            editCoverWithStream(uri)
        } else if (resetCover) {
            coverCache.deleteCustomCover(manga)
            presenterScope.launchIO { manga.updateCoverLastModified() }
            view?.setPaletteColor()
        }
        view?.updateHeader()
    }

    private fun setSeriesType(seriesType: Int, genres: String? = null): Array<String> {
        val tags = (genres ?: manga.genre)?.split(",")?.map { it.trim() }?.toMutableList() ?: mutableListOf()
        tags.removeAll { manga.isSeriesTag(it) }
        when (seriesType) {
            Manga.TYPE_MANGA -> tags.add("Manga")
            Manga.TYPE_MANHUA -> tags.add("Manhua")
            Manga.TYPE_MANHWA -> tags.add("Manhwa")
            Manga.TYPE_COMIC -> tags.add("Comic")
            Manga.TYPE_WEBTOON -> tags.add("Webtoon")
        }
        return tags.toTypedArray()
    }

    fun editCoverWithStream(uri: Uri): Boolean {
        val inputStream =
            downloadManager.context.contentResolver.openInputStream(uri) ?: return false
        if (manga.isLocal()) {
            LocalSource.updateCover(manga, inputStream)
            presenterScope.launchNonCancellableIO { manga.updateCoverLastModified() }
            view?.setPaletteColor()
            return true
        }

        if (manga.favorite) {
            coverCache.setCustomCoverToCache(manga, inputStream)
            presenterScope.launchNonCancellableIO { manga.updateCoverLastModified() }
            view?.setPaletteColor()
            return true
        }
        return false
    }

    fun shareCover(): Uri? {
        return try {
            val destDir = UniFile.fromFile(coverCache.context.cacheDir)!!.createDirectory("shared_image")!!
            val file = saveCover(destDir)
            file.uri
        } catch (e: Exception) {
            null
        }
    }

    fun saveCover(): Boolean {
        return try {
            val directory = if (preferences.folderPerManga().get()) {
                storageManager.getCoversDirectory()!!.createDirectory(DiskUtil.buildValidFilename(manga.title))!!
            } else {
                storageManager.getCoversDirectory()!!
            }
            val file = saveCover(directory)
            DiskUtil.scanMedia(preferences.context, file)
            true
        } catch (e: Exception) {
            if (networkPreferences.verboseLogging().get()) Logger.e(e) { "Unable to save cover" }
            false
        }
    }

    private fun saveCover(directory: UniFile): UniFile {
        val cover = coverCache.getCustomCoverFile(manga).takeIf { it.exists() } ?: coverCache.getCoverFile(manga.thumbnail_url, !manga.favorite)
        val type = cover?.let { ImageUtil.findImageType(it.inputStream()) }
            ?: throw Exception("Not an image")

        // Build destination file.
        val filename = DiskUtil.buildValidFilename("${manga.title}.${type.extension}")

        val destFile = directory.createFile(filename)!!
        cover.inputStream().use { input ->
            destFile.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }

    fun isTracked(): Boolean =
        loggedServices.any { service -> tracks.any { it.sync_id == service.id } }

    fun hasTrackers(): Boolean = loggedServices.isNotEmpty()

    // Tracking
    private fun setTrackItems() {
        trackList = loggedServices.filter { service ->
            if (service !is EnhancedTrackService) return@filter true
            service.accept(source)
        }.map { service ->
            TrackItem(tracks.find { it.sync_id == service.id }, service)
        }
    }

    suspend fun fetchTracks() {
        tracks = withContext(Dispatchers.IO) { getTrack.awaitAllByMangaId(manga.id!!) }
        setTrackItems()
        withContext(Dispatchers.Main) { view?.refreshTracking(trackList) }
    }

    fun refreshTracking(showOfflineSnack: Boolean = false, trackIndex: Int? = null) {
        if (view?.isNotOnline(showOfflineSnack) == false) {
            presenterScope.launch {
                val asyncList = (trackIndex?.let { listOf(trackList[it]) } ?: trackList.filter { it.track != null })
                    .map { item ->
                        async(Dispatchers.IO) {
                            val trackItem = try {
                                item.service.refresh(item.track!!)
                            } catch (e: Exception) {
                                trackError(e)
                                null
                            }
                            if (trackItem != null) {
                                db.insertTrack(trackItem).executeAsBlocking()
                                syncChaptersWithTrackServiceTwoWay(db, chapters, trackItem, item.service)
                                trackItem
                            } else {
                                item.track
                            }
                        }
                    }
                asyncList.awaitAll()
                fetchTracks()
            }
        }
    }

    fun trackSearch(query: String, service: TrackService) {
        if (view?.isNotOnline() == false) {
            presenterScope.launch(Dispatchers.IO) {
                val results = try {
                    service.search(query)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { view?.trackSearchError(e) }
                    return@launch
                }
                withContext(Dispatchers.Main) { view?.onTrackSearchResults(results) }
            }
        }
    }

    fun registerTracking(item: Track?, service: TrackService) {
        if (item != null) {
            item.manga_id = manga.id!!

            presenterScope.launch {
                val binding = try {
                    service.bind(item)
                } catch (e: Exception) {
                    trackError(e)
                    null
                }
                withContext(Dispatchers.IO) {
                    if (binding != null) {
                        db.insertTrack(binding).executeAsBlocking()
                    }

                    syncChaptersWithTrackServiceTwoWay(db, chapters, item, service)
                }
                fetchTracks()
            }
        }
    }

    fun removeTracker(trackItem: TrackItem, removeFromService: Boolean) {
        presenterScope.launch {
            withContext(Dispatchers.IO) {
                db.deleteTrackForManga(manga, trackItem.service).executeAsBlocking()
                if (removeFromService && trackItem.service.canRemoveFromService()) {
                    trackItem.service.removeFromService(trackItem.track!!)
                }
            }
            fetchTracks()
        }
    }

    private fun updateRemote(track: Track, service: TrackService) {
        presenterScope.launch {
            val binding = try {
                service.update(track)
            } catch (e: Exception) {
                trackError(e)
                null
            }
            if (binding != null) {
                withContext(Dispatchers.IO) { db.insertTrack(binding).executeAsBlocking() }
                fetchTracks()
            } else {
                trackRefreshDone()
            }
        }
    }

    private fun trackRefreshDone() {
        presenterScope.launch(Dispatchers.Main) { view?.trackRefreshDone() }
    }

    private fun trackError(error: Exception) {
        presenterScope.launch(Dispatchers.Main) { view?.trackRefreshError(error) }
    }

    fun setStatus(item: TrackItem, index: Int) {
        val track = item.track!!
        track.status = item.service.getStatusList()[index]
        if (item.service.isCompletedStatus(index) && track.total_chapters > 0L) {
            track.last_chapter_read = track.total_chapters.toFloat()
        }
        updateRemote(track, item.service)
    }

    fun setScore(item: TrackItem, index: Int) {
        val track = item.track!!
        track.score = item.service.indexToScore(index)
        updateRemote(track, item.service)
    }

    fun setLastChapterRead(item: TrackItem, chapterNumber: Int) {
        val track = item.track!!
        track.last_chapter_read = chapterNumber.toFloat()
        updateRemote(track, item.service)
    }

    fun setTrackerStartDate(item: TrackItem, date: Long) {
        val track = item.track!!
        track.started_reading_date = date
        updateRemote(track, item.service)
    }

    fun setTrackerFinishDate(item: TrackItem, date: Long) {
        val track = item.track!!
        track.finished_reading_date = date
        updateRemote(track, item.service)
    }

    fun getSuggestedDate(readingDate: TrackingBottomSheet.ReadingDate): Long? {
        val chapters = db.getHistoryByMangaId(manga.id ?: 0L).executeAsBlocking()
        val date = when (readingDate) {
            TrackingBottomSheet.ReadingDate.Start -> chapters.minOfOrNull { it.last_read }
            TrackingBottomSheet.ReadingDate.Finish -> chapters.maxOfOrNull { it.last_read }
        } ?: return null
        return if (date <= 0L) null else date
    }

    companion object {
        const val MULTIPLE_VOLUMES = 1
        const val TENS_OF_CHAPTERS = 2
        const val MULTIPLE_SEASONS = 3
    }
}
