package eu.kanade.tachiyomi.ui.reader

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.ColorInt
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.create
import eu.kanade.tachiyomi.data.database.models.defaultReaderType
import eu.kanade.tachiyomi.data.database.models.orientationType
import eu.kanade.tachiyomi.data.database.models.readingModeType
import eu.kanade.tachiyomi.data.database.models.updateCoverLastModified
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterItem
import eu.kanade.tachiyomi.ui.reader.chapter.ReaderChapterItem
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.loader.DownloadPageLoader
import eu.kanade.tachiyomi.ui.reader.loader.HttpPageLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.settings.OrientationType
import eu.kanade.tachiyomi.ui.reader.settings.ReadingModeType
import eu.kanade.tachiyomi.util.chapter.ChapterFilter
import eu.kanade.tachiyomi.util.chapter.ChapterSort
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.preferredChapterName
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import eu.kanade.tachiyomi.util.chapter.updateTrackChapterRead
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchNonCancellableIO
import eu.kanade.tachiyomi.util.system.localeContext
import eu.kanade.tachiyomi.util.system.withIOContext
import eu.kanade.tachiyomi.util.system.withUIContext
import java.util.Date
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.category.interactor.GetCategories
import yokai.domain.chapter.interactor.GetChapter
import yokai.domain.chapter.interactor.InsertChapter
import yokai.domain.chapter.interactor.UpdateChapter
import yokai.domain.chapter.models.ChapterUpdate
import yokai.domain.download.DownloadPreferences
import yokai.domain.history.interactor.GetHistory
import yokai.domain.history.interactor.UpsertHistory
import yokai.domain.manga.interactor.GetManga
import yokai.domain.manga.interactor.InsertManga
import yokai.domain.manga.interactor.UpdateManga
import yokai.domain.manga.models.MangaUpdate
import yokai.domain.storage.StorageManager
import yokai.domain.track.interactor.GetTrack
import yokai.i18n.MR
import yokai.util.lang.getString

/**
 * Presenter used by the activity to perform background operations.
 */
class ReaderViewModel(
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get(),
    private val chapterFilter: ChapterFilter = Injekt.get(),
    private val storageManager: StorageManager = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
) : ViewModel() {
    private val getCategories: GetCategories by injectLazy()
    private val getChapter: GetChapter by injectLazy()
    private val insertChapter: InsertChapter by injectLazy()
    private val updateChapter: UpdateChapter by injectLazy()
    private val getManga: GetManga by injectLazy()
    private val insertManga: InsertManga by injectLazy()
    private val updateManga: UpdateManga by injectLazy()
    private val getHistory: GetHistory by injectLazy()
    private val upsertHistory: UpsertHistory by injectLazy()
    private val getTrack: GetTrack by injectLazy()

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    private val downloadProvider = DownloadProvider(preferences.context)

    private val eventChannel = Channel<Event>()
    val eventFlow = eventChannel.receiveAsFlow()

    /**
     * The manga loaded in the reader. It can be null when instantiated for a short time.
     */
    val manga: Manga?
        get() = state.value.manga

    val source: Source?
        get() = manga?.source?.let { sourceManager.getOrStub(it) }

    /**
     * The chapter id of the currently loaded chapter. Used to restore from process kill.
     */
    private var chapterId = savedState.get<Long>("chapter_id") ?: -1L
        set(value) {
            savedState["chapter_id"] = value
            field = value
        }

    /**
     * The chapter loader for the loaded manga. It'll be null until [manga] is set.
     */
    private var loader: ChapterLoader? = null

    /**
     * The time the chapter was started reading
     */
    private var chapterReadStartTime: Long? = null

    /**
     * Relay used when loading prev/next chapter needed to lock the UI (with a dialog).
     */
    private var finished = false
    private var chapterToDownload: Download? = null

    private lateinit var chapterList: List<ReaderChapter>

    private var chapterItems = emptyList<ReaderChapterItem>()

    private var scope = CoroutineScope(Job() + Dispatchers.Default)

    private var hasTrackers: Boolean = false
    private suspend fun checkTrackers(manga: Manga) = getTrack.awaitAllByMangaId(manga.id).isNotEmpty()

    init {
        var secondRun = false
        // To save state
        state.map { it.viewerChapters?.currChapter }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach { currentChapter ->
                chapterId = currentChapter.chapter.id!!
                if (secondRun || !currentChapter.chapter.read) {
                    currentChapter.requestedPage = currentChapter.chapter.last_page_read
                }
                secondRun = true
            }
            .launchIn(viewModelScope)
    }

    /**
     * Called when the user pressed the back button and is going to leave the reader. Used to
     * trigger deletion of the downloaded chapters.
     */
    fun onBackPressed() {
        if (finished) return
        finished = true
        deletePendingChapters()
        val currentChapters = state.value.viewerChapters
        if (currentChapters != null) {
            currentChapters.unref()
            saveReadingProgress(currentChapters.currChapter)
            chapterToDownload?.let {
                downloadManager.addDownloadsToStartOfQueue(listOf(it))
            }
        }
    }

    /**
     * Called when the activity is saved and not changing configurations. It updates the database
     * to persist the current progress of the active chapter.
     */
    fun onSaveInstanceState() {
        val currentChapter = getCurrentChapter() ?: return
        viewModelScope.launchNonCancellableIO {
            saveChapterProgress(currentChapter)
        }
    }

    /**
     * Whether this presenter is initialized yet.
     */
    fun needsInit(): Boolean {
        return manga == null || !this::chapterList.isInitialized
    }

    /**
     * Initializes this presenter with the given [mangaId] and [initialChapterId]. This method will
     * fetch the manga from the database and initialize the initial chapter.
     */
    suspend fun init(mangaId: Long, initialChapterId: Long): Result<Boolean> {
        if (!needsInit()) return Result.success(true)
        return withIOContext {
            try {
                val manga = getManga.awaitById(mangaId)
                if (manga != null) {
                    mutableState.update { it.copy(manga = manga) }
                    if (chapterId == -1L) {
                        chapterId = initialChapterId
                    }

                    hasTrackers = checkTrackers(manga)

                    NotificationReceiver.dismissNotification(
                        preferences.context,
                        manga.id!!.hashCode(),
                        Notifications.ID_NEW_CHAPTERS,
                    )

                    val source = sourceManager.getOrStub(manga.source)
                    val context = Injekt.get<Application>()
                    loader = ChapterLoader(context, downloadManager, downloadProvider, manga, source)

                    chapterList = getChapterList()
                    loadChapter(loader!!, chapterList!!.first { chapterId == it.chapter.id })
                    Result.success(true)
                } else {
                    // Unlikely but okay
                    Result.success(false)
                }
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                Result.failure(e)
            }
        }
    }

    private suspend fun getChapterList(): List<ReaderChapter> {
        val manga = manga!!
        val dbChapters = getChapter.awaitAll(manga.id!!, true)

        val selectedChapter = dbChapters.find { it.id == chapterId }
            ?: error("Requested chapter of id $chapterId not found in chapter list")

        val chaptersForReader =
            chapterFilter.filterChaptersForReader(dbChapters, manga, selectedChapter)
        val chapterSort = ChapterSort(manga, chapterFilter, preferences)
        return chaptersForReader.sortedWith(chapterSort.sortComparator(true)).map(::ReaderChapter)
    }

    suspend fun getChapters(): List<ReaderChapterItem> {
        val manga = manga ?: return emptyList()
        chapterItems = withContext(Dispatchers.IO) {
            val chapterSort = ChapterSort(manga, chapterFilter, preferences)
            val dbChapters = getChapter.awaitAll(manga)
            chapterSort.getChaptersSorted(
                dbChapters,
                filterForReader = true,
                currentChapter = getCurrentChapter()?.chapter,
            ).map {
                ReaderChapterItem(
                    it,
                    manga,
                    it.id == (getCurrentChapter()?.chapter?.id ?: chapterId),
                )
            }
        }

        return chapterItems
    }

    fun canLoadUrl(uri: Uri): Boolean {
        val host = uri.host ?: return false
        val delegatedSource = sourceManager.getDelegatedSource(host) ?: return false
        return delegatedSource.canOpenUrl(uri)
    }

    fun intentPageNumber(url: Uri): Int? {
        val host = url.host ?: return null
        val delegatedSource = sourceManager.getDelegatedSource(host) ?: error(
            preferences.context.getString(MR.strings.source_not_installed),
        )
        return delegatedSource.pageNumber(url)?.minus(1)
    }

    // FIXME: Unused at the moment, handles J2K's delegated deep link, refactor or remove later
    suspend fun loadChapterURL(url: Uri) {
        val host = url.host ?: return
        val context = Injekt.get<Application>()
        val delegatedSource = sourceManager.getDelegatedSource(host) ?: error(
            context.getString(MR.strings.source_not_installed),
        )
        val chapterUrl = delegatedSource.chapterUrl(url)
        val sourceId = delegatedSource.delegate.id
        if (chapterUrl != null) {
            val dbChapter = getChapter.awaitAllByUrl(chapterUrl, false).find {
                val source = getManga.awaitById(it.manga_id!!)?.source ?: return@find false
                if (source == sourceId) {
                    true
                } else {
                    val httpSource = sourceManager.getOrStub(source) as? HttpSource
                    val domainName = delegatedSource.domainName
                    httpSource?.baseUrl?.contains(domainName) == true
                }
            }
            if (dbChapter?.manga_id?.let { init(it, dbChapter.id!!).isSuccess } == true) {
                return
            }
        }
        val info = delegatedSource.fetchMangaFromChapterUrl(url)
        if (info != null) {
            val (sChapter, sManga, chapters) = info
            val manga = Manga.create(sManga.url, sManga.title, sourceId).apply { copyFrom(sManga) }
            val chapter = Chapter.create().apply { copyFrom(sChapter) }
            val id = insertManga.await(manga)
            manga.id = id ?: manga.id
            chapter.manga_id = manga.id
            val matchingChapterId =
                getChapter.awaitAll(manga.id!!, false).find { it.url == chapter.url }?.id
            if (matchingChapterId != null) {
                withContext(Dispatchers.Main) {
                    this@ReaderViewModel.init(manga.id!!, matchingChapterId)
                }
            } else {
                val chapterId: Long
                if (chapters.isNotEmpty()) {
                    val newChapters = syncChaptersWithSource(
                        chapters,
                        manga,
                        delegatedSource.delegate!!,
                    ).first
                    chapterId = newChapters.find { it.url == chapter.url }?.id
                        ?: error(context.getString(MR.strings.chapter_not_found))
                } else {
                    chapter.date_fetch = Date().time
                    chapterId = insertChapter.await(chapter) ?: error(
                        context.getString(MR.strings.unknown_error),
                    )
                }
                withContext(Dispatchers.Main) {
                    init(manga.id!!, chapterId)
                }
            }
        } else {
            error(context.getString(MR.strings.unknown_error))
        }
    }

    /**
     * Called when the user changed to the given [chapter] when changing pages from the viewer.
     * It's used only to set this chapter as active.
     */
    private suspend fun loadNewChapter(chapter: ReaderChapter) {
        val loader = loader ?: return

        Logger.d { "Loading ${chapter.chapter.url}" }

        withIOContext {
            try {
                loadChapter(loader, chapter)
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                Logger.e(e) { "Unable to load new chapter" }
            }
        }
    }

    /**
     * Loads the given [chapter] with this [loader] and updates the currently active chapters.
     * Callers must handle errors.
     */
    private suspend fun loadChapter(
        loader: ChapterLoader,
        chapter: ReaderChapter,
    ): ViewerChapters {
        loader.loadChapter(chapter)

        val chapterPos = chapterList.indexOf(chapter) ?: -1
        val newChapters = ViewerChapters(
            chapter,
            chapterList.getOrNull(chapterPos - 1),
            chapterList.getOrNull(chapterPos + 1),
        )

        withUIContext {
            mutableState.update {
                // Add new references first to avoid unnecessary recycling
                newChapters.ref()
                it.viewerChapters?.unref()

                chapterToDownload = deleteChapterFromDownloadQueue(newChapters.currChapter)
                it.copy(viewerChapters = newChapters)
            }
        }
        return newChapters
    }

    /**
     * Called when the user is going to load the prev/next chapter through the menu button.
     */
    suspend fun loadChapter(chapter: ReaderChapter): Int? {
        val loader = loader ?: return -1

        Logger.d { "Loading adjacent ${chapter.chapter.url}" }
        var lastPage: Int? = if (chapter.chapter.pages_left <= 1) 0 else chapter.chapter.last_page_read
        mutableState.update { it.copy(isLoadingAdjacentChapter = true) }
        try {
            withIOContext {
                loadChapter(loader, chapter)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            Logger.e(e) { "Unable to load adjacent chapter" }
            lastPage = null
        } finally {
            mutableState.update { it.copy(isLoadingAdjacentChapter = false) }
        }
        return lastPage
    }

    fun toggleBookmark(chapter: Chapter) {
        chapter.bookmark = !chapter.bookmark
        viewModelScope.launchNonCancellableIO {
            updateChapter.await(
                ChapterUpdate(
                    id = chapter.id!!,
                    read = chapter.read,
                    bookmark = chapter.bookmark,
                    lastPageRead = chapter.last_page_read.toLong(),
                    pagesLeft = chapter.pages_left.toLong(),
                )
            )
        }
    }

    /**
     * Called when the viewers decide it's a good time to preload a [chapter] and improve the UX so
     * that the user doesn't have to wait too long to continue reading.
     */
    private suspend fun preload(chapter: ReaderChapter) {
        if (chapter.pageLoader is HttpPageLoader) {
            val manga = manga ?: return
            val isDownloaded = downloadManager.isChapterDownloaded(chapter.chapter, manga)
            if (isDownloaded) {
                chapter.state = ReaderChapter.State.Wait
            }
        }

        if (chapter.state != ReaderChapter.State.Wait && chapter.state !is ReaderChapter.State.Error) {
            return
        }

        Logger.d { "Preloading ${chapter.chapter.url}" }

        val loader = loader ?: return
        withIOContext {
            try {
                loader.loadChapter(chapter)
            } catch (e: Throwable) {
                if (e is CancellationException) {
                    throw e
                }
                return@withIOContext
            }
            eventChannel.trySend(Event.ReloadViewerChapters)
        }
    }

    fun adjacentChapter(next: Boolean): ReaderChapter? {
        val chapters = state.value.viewerChapters
        return if (next) chapters?.nextChapter else chapters?.prevChapter
    }

    /**
     * Called every time a page changes on the reader. Used to mark the flag of chapters being
     * read, update tracking services, enqueue downloaded chapter deletion, and updating the active chapter if this
     * [page]'s chapter is different from the currently active.
     */
    fun onPageSelected(page: ReaderPage, hasExtraPage: Boolean) {
        val currentChapters = state.value.viewerChapters ?: return

        val selectedChapter = page.chapter

        // Save last page read and mark as read if needed
        selectedChapter.chapter.last_page_read = page.index
        selectedChapter.chapter.pages_left =
            (selectedChapter.pages?.size ?: page.index) - page.index
        val shouldTrack = !preferences.incognitoMode().get() || hasTrackers
        if (shouldTrack &&
            // For double pages, check if the second to last page is doubled up
            (
                (selectedChapter.pages?.lastIndex == page.index && page.firstHalf != true) ||
                    (hasExtraPage && selectedChapter.pages?.lastIndex?.minus(1) == page.index)
                )
        ) {
            selectedChapter.chapter.read = true
            updateTrackChapterAfterReading(selectedChapter)
            deleteChapterIfNeeded(selectedChapter)
        }

        if (selectedChapter != currentChapters.currChapter) {
            Logger.d { "Setting ${selectedChapter.chapter.url} as active" }
            saveReadingProgress(currentChapters.currChapter)
            setReadStartTime()
            scope.launch { loadNewChapter(selectedChapter) }
        }
        val pages = page.chapter.pages ?: return
        val inDownloadRange = page.number.toDouble() / pages.size > 0.2
        if (inDownloadRange) {
            downloadNextChapters()
        }
    }

    private fun downloadNextChapters() {
        val manga = manga ?: return
        viewModelScope.launchNonCancellableIO {
            if (getCurrentChapter()?.pageLoader !is DownloadPageLoader) return@launchNonCancellableIO
            val nextChapter = state.value.viewerChapters?.nextChapter?.chapter ?: return@launchNonCancellableIO
            val chaptersNumberToDownload = preferences.autoDownloadWhileReading().get()
            if (chaptersNumberToDownload == 0 || !manga.favorite) return@launchNonCancellableIO
            val isNextChapterDownloaded = downloadManager.isChapterDownloaded(nextChapter, manga)
            if (isNextChapterDownloaded) {
                downloadAutoNextChapters(chaptersNumberToDownload, nextChapter.id)
            }
        }
    }

    private suspend fun downloadAutoNextChapters(choice: Int, nextChapterId: Long?) {
        val chaptersToDownload = getNextUnreadChaptersSorted(nextChapterId).take(choice - 1)
        if (chaptersToDownload.isNotEmpty()) {
            downloadChapters(chaptersToDownload)
        }
    }

    private suspend fun getNextUnreadChaptersSorted(nextChapterId: Long?): List<ChapterItem> {
        val chapterSort = ChapterSort(manga!!, chapterFilter, preferences)
        return chapterList.map { ChapterItem(it.chapter, manga!!) }
            .filter { !it.read || it.id == nextChapterId }
            .sortedWith(chapterSort.sortComparator(true))
            .takeLastWhile { it.id != nextChapterId }
    }

    /**
     * Downloads the given list of chapters with the manager.
     * @param chapters the list of chapters to download.
     */
    private fun downloadChapters(chapters: List<ChapterItem>) {
        downloadManager.downloadChapters(manga!!, chapters.filter { !it.isDownloaded })
    }

    /**
     * Removes [currentChapter] from download queue
     * if setting is enabled and [currentChapter] is queued for download
     */
    private fun deleteChapterFromDownloadQueue(currentChapter: ReaderChapter): Download? {
        return downloadManager.getChapterDownloadOrNull(currentChapter.chapter)?.apply {
            downloadManager.deletePendingDownloads(this)
        }
    }

    /**
     * Determines if deleting option is enabled and nth to last chapter actually exists.
     * If both conditions are satisfied enqueues chapter for delete
     * @param currentChapter current chapter, which is going to be marked as read.
     */
    private fun deleteChapterIfNeeded(currentChapter: ReaderChapter) {
        viewModelScope.launchNonCancellableIO {
            // Determine which chapter should be deleted and enqueue
            val currentChapterPosition = chapterList.indexOf(currentChapter)
            val removeAfterReadSlots = preferences.removeAfterReadSlots().get()
            val chapterToDelete = chapterList.getOrNull(currentChapterPosition - removeAfterReadSlots)

            if (removeAfterReadSlots != 0 && chapterToDownload != null) {
                downloadManager.addDownloadsToStartOfQueue(listOf(chapterToDownload!!))
            } else {
                chapterToDownload = null
            }
            // Check if deleting option is enabled and chapter exists
            if (removeAfterReadSlots != -1 && chapterToDelete != null) {
                val excludedCategories = preferences.removeExcludeCategories().get().map(String::toInt)
                if (excludedCategories.any()) {
                    val categories = getCategories.awaitByMangaId(manga!!.id!!)
                        .mapNotNull { it.id }
                        .ifEmpty { listOf(0) }

                    if (categories.any { it in excludedCategories }) return@launchNonCancellableIO
                }

                enqueueDeleteReadChapters(chapterToDelete)
            }
        }
    }

    /**
     * Called when reader chapter is changed in reader or when activity is paused.
     */
    private fun saveReadingProgress(readerChapter: ReaderChapter) {
        viewModelScope.launchNonCancellableIO {
            saveChapterProgress(readerChapter)
            saveChapterHistory(readerChapter)
        }
    }

    fun saveCurrentChapterReadingProgress() = getCurrentChapter()?.let { saveReadingProgress(it) }

    /**
     * Saves this [readerChapter]'s progress (last read page and whether it's read).
     * If incognito mode isn't on or has at least 1 tracker
     */
    private suspend fun saveChapterProgress(readerChapter: ReaderChapter) {
        readerChapter.requestedPage = readerChapter.chapter.last_page_read
        getChapter.awaitById(readerChapter.chapter.id!!)?.let { dbChapter ->
            readerChapter.chapter.bookmark = dbChapter.bookmark
        }
        if (!preferences.incognitoMode().get() || hasTrackers) {
            updateChapter.await(
                ChapterUpdate(
                    id = readerChapter.chapter.id!!,
                    read = readerChapter.chapter.read,
                    bookmark = readerChapter.chapter.bookmark,
                    lastPageRead = readerChapter.chapter.last_page_read.toLong(),
                    pagesLeft = readerChapter.chapter.pages_left.toLong(),
                )
            )
        }
    }

    /**
     * Saves this [readerChapter] last read history.
     */
    private suspend fun saveChapterHistory(readerChapter: ReaderChapter) {
        if (!preferences.incognitoMode().get()) {
            val readAt = Date().time
            val sessionReadDuration = chapterReadStartTime?.let { readAt - it } ?: 0
            val history = History.create(readerChapter.chapter).apply {
                last_read = readAt
                time_read = sessionReadDuration
            }
            upsertHistory.await(history)
            chapterReadStartTime = null
        }
    }

    fun setReadStartTime() {
        chapterReadStartTime = Date().time
    }

    /**
     * Called from the activity to preload the given [chapter].
     */
    suspend fun preloadChapter(chapter: ReaderChapter) {
        preload(chapter)
    }

    /**
     * Returns the currently active chapter.
     */
    fun getCurrentChapter(): ReaderChapter? {
        return state.value.viewerChapters?.currChapter
    }

    fun getChapterUrl(mainChapter: Chapter? = null): String? {
        val manga = manga ?: return null
        val source = getSource() ?: return null
        val chapter = mainChapter ?: getCurrentChapter()?.chapter ?: return null
        val chapterUrl = try { source.getChapterUrl(chapter) } catch (_: Exception) { null }
        return chapterUrl.takeIf { !it.isNullOrBlank() }
            ?: try { source.getChapterUrl(manga, chapter) } catch (_: Exception) { null }
    }

    fun getSource() = manga?.source?.let { sourceManager.getOrStub(it) } as? HttpSource

    /**
     * Returns the viewer position used by this manga or the default one.
     */
    fun getMangaReadingMode(): Int {
        val default = preferences.defaultReadingMode().get()
        val manga = manga ?: return default
        val readerType = manga.defaultReaderType()
        if (manga.viewer_flags == -1) {
            val cantSwitchToLTR =
                (
                    readerType == ReadingModeType.LEFT_TO_RIGHT.flagValue &&
                        default != ReadingModeType.RIGHT_TO_LEFT.flagValue
                    )
            if (manga.viewer_flags == -1) {
                manga.viewer_flags = 0
            }
            manga.readingModeType = if (cantSwitchToLTR) 0 else readerType
            viewModelScope.launchIO { updateManga.await(MangaUpdate(manga.id!!, viewerFlags = manga.viewer_flags)) }
        }
        return if (manga.readingModeType == 0) default else manga.readingModeType
    }

    /**
     * Updates the viewer position for the open manga.
     */
    fun setMangaReadingMode(readingModeType: Int) {
        val manga = manga ?: return

        viewModelScope.launchIO {
            manga.readingModeType = readingModeType
            updateManga.await(MangaUpdate(manga.id!!, viewerFlags = manga.viewer_flags))
            val currChapters = state.value.viewerChapters
            if (currChapters != null) {
                // Save current page
                val currChapter = currChapters.currChapter
                currChapter.requestedPage = currChapter.chapter.last_page_read

                mutableState.update {
                    it.copy(
                        manga = getManga.awaitById(manga.id!!),
                        viewerChapters = currChapters,
                    )
                }
                eventChannel.send(Event.ReloadMangaAndChapters)
            }
        }
    }

    /**
     * Returns the orientation type used by this manga or the default one.
     */
    fun getMangaOrientationType(): Int {
        val default = preferences.defaultOrientationType().get()
        return when (manga?.orientationType) {
            OrientationType.DEFAULT.flagValue -> default
            else -> manga?.orientationType ?: default
        }
    }

    /**
     * Updates the orientation type for the open manga.
     */
    fun setMangaOrientationType(rotationType: Int) {
        val manga = manga ?: return
        this.manga?.orientationType = rotationType

        Logger.i { "Manga orientation is ${manga.orientationType}" }

        viewModelScope.launchIO {
            updateManga.await(MangaUpdate(manga.id!!, viewerFlags = manga.viewer_flags))
            val currChapters = state.value.viewerChapters
            if (currChapters != null) {
                mutableState.update {
                    it.copy(
                        manga = getManga.awaitById(manga.id!!),
                        viewerChapters = currChapters,
                    )
                }
                eventChannel.send(Event.SetOrientation(getMangaOrientationType()))
                eventChannel.send(Event.ReloadViewerChapters)
            }
        }
    }

    /**
     * Saves the image of this [page] in the given [directory] and returns the file location.
     */
    private fun saveImage(page: ReaderPage, directory: UniFile, manga: Manga): UniFile {
        val stream = page.stream!!
        val type = ImageUtil.findImageType(stream) ?: throw Exception("Not an image")
        val context = Injekt.get<Application>()

        val chapter = page.chapter.chapter

        // Build destination file.
        val filename = DiskUtil.buildValidFilename(
            "${manga.title} - ${chapter.preferredChapterName(context, manga, preferences)}".take(225),
        ) + (if (downloadPreferences.downloadWithId().get()) " (${chapter.id})" else "") + " - ${page.number}.${type.extension}"

        val destFile = directory.createFile(filename)!!
        stream().use { input ->
            destFile.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }

    /**
     * Saves the image of [page1] and [page2] in the given [directory] and returns the file location.
     */
    private fun saveImages(page1: ReaderPage, page2: ReaderPage, isLTR: Boolean, @ColorInt bg: Int, directory: UniFile, manga: Manga): UniFile {
        val stream1 = page1.stream!!
        ImageUtil.findImageType(stream1) ?: throw Exception("Not an image")
        val stream2 = page2.stream!!
        ImageUtil.findImageType(stream2) ?: throw Exception("Not an image")
        val imageBytes = stream1().readBytes()
        val imageBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        val imageBytes2 = stream2().readBytes()
        val imageBitmap2 = BitmapFactory.decodeByteArray(imageBytes2, 0, imageBytes2.size)

        val stream = ImageUtil.mergeBitmaps(imageBitmap, imageBitmap2, isLTR, bg).inputStream()

        val chapter = page1.chapter.chapter
        val context = Injekt.get<Application>()

        // Build destination file.
        val filename = DiskUtil.buildValidFilename(
            "${manga.title} - ${chapter.preferredChapterName(context, manga, preferences)}".take(225),
        ) + (if (downloadPreferences.downloadWithId().get()) " (${chapter.id})" else "") + " - ${page1.number}-${page2.number}.jpg"

        val destFile = directory.createFile(filename)!!
        stream.use { input ->
            destFile.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }
        stream.close()
        return destFile
    }

    /**
     * Saves the image of this [page] on the pictures directory and notifies the UI of the result.
     * There's also a notification to allow sharing the image somewhere else or deleting it.
     */
    fun saveImage(page: ReaderPage) {
        if (page.status != Page.State.READY) return
        val manga = manga ?: return
        val context = Injekt.get<Application>()

        // Pictures directory.
        val baseDir = storageManager.getPagesDirectory() ?: return
        val destDir = if (preferences.folderPerManga().get()) {
            baseDir.createDirectory(DiskUtil.buildValidFilename(manga.title))
        } else {
            baseDir
        } ?: return

        val notifier = SaveImageNotifier(context.localeContext)
        notifier.onClear()

        // Copy file in background.
        viewModelScope.launchNonCancellableIO {
            try {
                val file = saveImage(page, destDir, manga)
                DiskUtil.scanMedia(context, file)
                notifier.onComplete(file)
                eventChannel.send(Event.SavedImage(SaveImageResult.Success(file)))
            } catch (e: Exception) {
                notifier.onError(e.message)
                eventChannel.send(Event.SavedImage(SaveImageResult.Error(e)))
            }
        }
    }

    fun saveImages(firstPage: ReaderPage, secondPage: ReaderPage, isLTR: Boolean, @ColorInt bg: Int) {
        scope.launch {
            if (firstPage.status != Page.State.READY) return@launch
            if (secondPage.status != Page.State.READY) return@launch
            val manga = manga ?: return@launch
            val context = Injekt.get<Application>()

            // Pictures directory.
            val baseDir = storageManager.getPagesDirectory() ?: return@launch
            val destDir = if (preferences.folderPerManga().get()) {
                baseDir.findFile(DiskUtil.buildValidFilename(manga.title))
            } else {
                baseDir
            } ?: return@launch

            val notifier = SaveImageNotifier(context.localeContext)
            notifier.onClear()

            try {
                val file = saveImages(firstPage, secondPage, isLTR, bg, destDir, manga)
                DiskUtil.scanMedia(context, file)
                notifier.onComplete(file)
                eventChannel.send(Event.SavedImage(SaveImageResult.Success(file)))
            } catch (e: Exception) {
                eventChannel.send(Event.SavedImage(SaveImageResult.Error(e)))
            }
        }
    }

    /**
     * Shares the image of this [page] and notifies the UI with the path of the file to share.
     * The image must be first copied to the internal partition because there are many possible
     * formats it can come from, like a zipped chapter, in which case it's not possible to directly
     * get a path to the file and it has to be decompresssed somewhere first. Only the last shared
     * image will be kept so it won't be taking lots of internal disk space.
     */
    fun shareImage(page: ReaderPage) {
        if (page.status != Page.State.READY) return
        val manga = manga ?: return
        val context = Injekt.get<Application>()

        val destDir = UniFile.fromFile(context.cacheDir)!!.createDirectory("shared_image")!!

        viewModelScope.launchNonCancellableIO {
            val file = saveImage(page, destDir, manga)
            eventChannel.send(Event.ShareImage(file, page))
        }
    }

    fun shareImages(firstPage: ReaderPage, secondPage: ReaderPage, isLTR: Boolean, @ColorInt bg: Int) {
        scope.launch {
            if (firstPage.status != Page.State.READY) return@launch
            if (secondPage.status != Page.State.READY) return@launch
            val manga = manga ?: return@launch
            val context = Injekt.get<Application>()

            try {
                val destDir = UniFile.fromFile(context.cacheDir)!!.findFile("shared_image")!!
                val file = saveImages(firstPage, secondPage, isLTR, bg, destDir, manga)
                eventChannel.send(Event.ShareImage(file, firstPage, secondPage))
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Sets the image of this [page] as cover and notifies the UI of the result.
     */
    fun setAsCover(page: ReaderPage) {
        if (page.status != Page.State.READY) return
        val manga = manga ?: return
        val stream = page.stream ?: return

        viewModelScope.launchNonCancellableIO {
            val result = try {
                if (manga.isLocal()) {
                    coverCache.deleteFromCache(manga)
                    LocalSource.updateCover(manga, stream())
                    manga.updateCoverLastModified()
                    MR.strings.cover_updated
                    SetAsCoverResult.Success
                } else {
                    if (manga.favorite) {
                        coverCache.setCustomCoverToCache(manga, stream())
                        manga.updateCoverLastModified()
                        SetAsCoverResult.Success
                    } else {
                        SetAsCoverResult.AddToLibraryFirst
                    }
                }
            } catch (e: Exception) {
                SetAsCoverResult.Error
            }
            eventChannel.send(Event.SetCoverResult(result))
        }
    }

    /**
     * Results of the set as cover feature.
     */
    enum class SetAsCoverResult {
        Success, AddToLibraryFirst, Error
    }

    /**
     * Results of the save image feature.
     */
    sealed class SaveImageResult {
        class Success(val file: UniFile) : SaveImageResult()
        class Error(val error: Throwable) : SaveImageResult()
    }

    /**
     * Starts the service that updates the last chapter read in sync services. This operation
     * will run in a background thread and errors are ignored.
     */
    private fun updateTrackChapterAfterReading(readerChapter: ReaderChapter) {
        if (!preferences.autoUpdateTrack().get()) return

        launchIO {
            val newChapterRead = readerChapter.chapter.chapter_number
            val errors = updateTrackChapterRead(preferences, manga?.id, newChapterRead, true)
            if (errors.isNotEmpty()) {
                eventChannel.send(Event.ShareTrackingError(errors))
            }
        }
    }

    /**
     * Enqueues this [chapter] to be deleted when [deletePendingChapters] is called. The download
     * manager handles persisting it across process deaths.
     */
    private fun enqueueDeleteReadChapters(chapter: ReaderChapter) {
        if (!chapter.chapter.read) return
        val manga = manga ?: return

        viewModelScope.launchNonCancellableIO {
            downloadManager.enqueueDeleteChapters(listOf(chapter.chapter), manga)
        }
    }

    /**
     * Deletes all the pending chapters. This operation will run in a background thread and errors
     * are ignored.
     */
    private fun deletePendingChapters() {
        viewModelScope.launchNonCancellableIO {
            downloadManager.deletePendingChapters()
        }
    }

    data class State(
        val manga: Manga? = null,
        val viewerChapters: ViewerChapters? = null,
        val isLoadingAdjacentChapter: Boolean = false,
        val lastPage: Int? = null,
    )

    sealed class Event {
        object ReloadViewerChapters : Event()
        object ReloadMangaAndChapters : Event()
        data class SetOrientation(val orientation: Int) : Event()
        data class SetCoverResult(val result: SetAsCoverResult) : Event()

        data class SavedImage(val result: SaveImageResult) : Event()
        data class ShareImage(val file: UniFile, val page: ReaderPage, val extraPage: ReaderPage? = null) : Event()
        data class ShareTrackingError(val errors: List<Pair<TrackService, String?>>) : Event()
    }
}
