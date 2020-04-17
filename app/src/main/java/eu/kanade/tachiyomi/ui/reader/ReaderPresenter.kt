package eu.kanade.tachiyomi.ui.reader

import android.app.Application
import android.os.Bundle
import android.os.Environment
import com.jakewharton.rxrelay.BehaviorRelay
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.database.models.isWebtoon
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.utils.FollowStatus
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterItem
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.loader.DownloadPageLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.executeOnIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rx.Completable
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Presenter used by the activity to perform background operations.
 */
class ReaderPresenter(
    private val db: DatabaseHelper = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get()
) : BasePresenter<ReaderActivity>() {

    private var scope = CoroutineScope(Job() + Dispatchers.Default)

    /**
     * The manga loaded in the reader. It can be null when instantiated for a short time.
     */
    var manga: Manga? = null
        private set

    /**
     * The chapter id of the currently loaded chapter. Used to restore from process kill.
     */
    private var chapterId = -1L

    /**
     * The chapter loader for the loaded manga. It'll be null until [manga] is set.
     */
    private var loader: ChapterLoader? = null

    /**
     * Subscription to prevent setting chapters as active from multiple threads.
     */
    private var activeChapterSubscription: Subscription? = null

    /**
     * Relay for currently active viewer chapters.
     */
    private val viewerChaptersRelay = BehaviorRelay.create<ViewerChapters>()

    /**
     * Relay used when loading prev/next chapter needed to lock the UI (with a dialog).
     */
    private val isLoadingAdjacentChapterRelay = BehaviorRelay.create<Boolean>()

    /**
     * Chapter list for the active manga. It's retrieved lazily and should be accessed for the first
     * time in a background thread to avoid blocking the UI.
     */
    private val chapterList by lazy {
        val manga = manga!!
        val dbChapters = db.getChapters(manga).executeAsBlocking().map { ChapterItem(it, manga) }

        val selectedChapter = dbChapters.find { it.id == chapterId }
            ?: error("Requested chapter of id $chapterId not found in chapter list")

        val chaptersForReader =
            if (preferences.skipRead() || preferences.skipFiltered()) {
                val list = dbChapters
                    .filter {
                        if (preferences.skipRead() && it.read) {
                            return@filter false
                        } else if (preferences.skipFiltered()) {
                            if (
                                (manga.readFilter == Manga.SHOW_READ && !it.read) ||
                                (manga.readFilter == Manga.SHOW_UNREAD && it.read) ||
                                (manga.downloadedFilter == Manga.SHOW_DOWNLOADED &&
                                    !downloadManager.isChapterDownloaded(it, manga)) ||
                                (manga.bookmarkedFilter == Manga.SHOW_BOOKMARKED && !it.bookmark)
                            ) {
                                return@filter false
                            }
                        }

                        true
                    }
                    .toMutableList()
                val find = list.find { it.id == chapterId }
                if (find == null) {
                    list.add(selectedChapter)
                }
                list
            } else {
                dbChapters
            }

        when (manga.sorting) {
            Manga.SORTING_SOURCE -> ChapterLoadBySource().get(chaptersForReader)
            Manga.SORTING_NUMBER -> ChapterLoadByNumber().get(chaptersForReader, selectedChapter)
            else -> error("Unknown sorting method")
        }.map(::ReaderChapter)
    }

    var chapterItems = emptyList<ReaderChapterItem>()

    /**
     * Called when the presenter is created. It retrieves the saved active chapter if the process
     * was restored.
     */
    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        if (savedState != null) {
            chapterId = savedState.getLong(::chapterId.name, -1)
        }
    }

    /**
     * Called when the presenter instance is being saved. It saves the currently active chapter
     * id and the last page read.
     */
    override fun onSave(state: Bundle) {
        super.onSave(state)
        val currentChapter = getCurrentChapter()
        if (currentChapter != null) {
            currentChapter.requestedPage = currentChapter.chapter.last_page_read
            state.putLong(::chapterId.name, currentChapter.chapter.id!!)
        }
    }

    /**
     * Called when the user pressed the back button and is going to leave the reader. Used to
     * trigger deletion of the downloaded chapters.
     */
    fun onBackPressed() {
        deletePendingChapters()
        val currentChapters = viewerChaptersRelay.value
        if (currentChapters != null) {
            currentChapters.unref()
            saveChapterProgress(currentChapters.currChapter)
            saveChapterHistory(currentChapters.currChapter)
        }
    }

    /**
     * Called when the activity is saved and not changing configurations. It updates the database
     * to persist the current progress of the active chapter.
     */
    fun onSaveInstanceStateNonConfigurationChange() {
        val currentChapter = getCurrentChapter() ?: return
        saveChapterProgress(currentChapter)
    }

    /**
     * Whether this presenter is initialized yet.
     */
    fun needsInit(): Boolean {
        return manga == null
    }

    /**
     * Initializes this presenter with the given [mangaId] and [initialChapterId]. This method will
     * fetch the manga from the database and initialize the initial chapter.
     */
    fun init(mangaId: Long, initialChapterId: Long) {
        if (!needsInit()) return

        db.getManga(mangaId).asRxObservable()
            .first()
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { init(it, initialChapterId) }
            .subscribeFirst({ _, _ ->
                // Ignore onNext event
            }, ReaderActivity::setInitialChapterError)
    }

    suspend fun getChapters(): List<ReaderChapterItem> {
        val manga = manga ?: return emptyList()
        chapterItems = withContext(Dispatchers.IO) {
            val list = db.getChapters(manga).executeOnIO().sortedBy {
                when (manga.sorting) {
                    Manga.SORTING_NUMBER -> it.chapter_number
                    else -> it.source_order.toFloat()
                }
            }.map {
                ReaderChapterItem(
                    it, manga, it.id ==
                        getCurrentChapter()?.chapter?.id ?: chapterId
                )
            }
            if (!manga.sortDescending(preferences.chaptersDescAsDefault().getOrDefault()))
                list.reversed()
            else list
        }
        return chapterItems
    }

    fun init(mangaId: Long, chapterUrl: String) {
        if (!needsInit()) return
        val context = Injekt.get<Application>()
        val db = DatabaseHelper(context)
        val chapterId = db.getChapter(chapterUrl, mangaId).executeAsBlocking()?.id
        if (chapterId != null)
            init(mangaId, chapterId)
    }

    /**
     * Initializes this presenter with the given [manga] and [initialChapterId]. This method will
     * set the chapter loader, view subscriptions and trigger an initial load.
     */
    private fun init(manga: Manga, initialChapterId: Long) {
        if (!needsInit()) return

        this.manga = manga
        if (chapterId == -1L) chapterId = initialChapterId

        val source = sourceManager.getMangadex()
        loader = ChapterLoader(downloadManager, manga, source)

        Observable.just(manga).subscribeLatestCache(ReaderActivity::setManga)
        viewerChaptersRelay.subscribeLatestCache(ReaderActivity::setChapters)
        isLoadingAdjacentChapterRelay.subscribeLatestCache(ReaderActivity::setProgressDialog)

        // Read chapterList from an io thread because it's retrieved lazily and would block main.
        activeChapterSubscription?.unsubscribe()
        activeChapterSubscription = Observable
            .fromCallable { chapterList.first { chapterId == it.chapter.id } }
            .flatMap { getLoadObservable(loader!!, it) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeFirst({ _, _ ->
                // Ignore onNext event
            }, ReaderActivity::setInitialChapterError)
    }

    /**
     * Returns an observable that loads the given [chapter] with this [loader]. This observable
     * handles main thread synchronization and updating the currently active chapters on
     * [viewerChaptersRelay], however callers must ensure there won't be more than one
     * subscription active by unsubscribing any existing [activeChapterSubscription] before.
     * Callers must also handle the onError event.
     */
    private fun getLoadObservable(
        loader: ChapterLoader,
        chapter: ReaderChapter
    ): Observable<ViewerChapters> {
        return loader.loadChapter(chapter)
            .andThen(Observable.fromCallable {
                val chapterPos = chapterList.indexOf(chapter)

                ViewerChapters(
                    chapter,
                    chapterList.getOrNull(chapterPos - 1),
                    chapterList.getOrNull(chapterPos + 1)
                )
            })
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { newChapters ->
                val oldChapters = viewerChaptersRelay.value

                // Add new references first to avoid unnecessary recycling
                newChapters.ref()
                oldChapters?.unref()

                viewerChaptersRelay.call(newChapters)
            }
    }

    /**
     * Called when the user changed to the given [chapter] when changing pages from the viewer.
     * It's used only to set this chapter as active.
     */
    private fun loadNewChapter(chapter: ReaderChapter) {
        val loader = loader ?: return

        Timber.d("Loading ${chapter.chapter.url}")

        activeChapterSubscription?.unsubscribe()
        activeChapterSubscription = getLoadObservable(loader, chapter)
            .toCompletable()
            .onErrorComplete()
            .subscribe()
            .also(::add)
    }

    fun loadChapter(chapter: Chapter) {
        val loader = loader ?: return

        Timber.d("Loading adjacent ${chapter.url}")

        activeChapterSubscription?.unsubscribe()
        activeChapterSubscription = getLoadObservable(loader, ReaderChapter(chapter))
            .doOnSubscribe { isLoadingAdjacentChapterRelay.call(true) }
            .doOnUnsubscribe { isLoadingAdjacentChapterRelay.call(false) }
            .subscribeFirst({ view, _ ->
                view.moveToPageIndex(0)
                view.refreshChapters()
            }, { _, _ ->
                // Ignore onError event, viewers handle that state
            })
    }

    fun toggleBookmark(chapter: Chapter) {
        chapter.bookmark = !chapter.bookmark
        db.updateChapterProgress(chapter).executeAsBlocking()
    }

    /**
     * Called when the viewers decide it's a good time to preload a [chapter] and improve the UX so
     * that the user doesn't have to wait too long to continue reading.
     */
    private fun preload(chapter: ReaderChapter) {
        if (chapter.state != ReaderChapter.State.Wait && chapter.state !is ReaderChapter.State.Error) {
            return
        }

        Timber.d("Preloading ${chapter.chapter.url}")

        val loader = loader ?: return

        loader.loadChapter(chapter)
            .observeOn(AndroidSchedulers.mainThread())
            // Update current chapters whenever a chapter is preloaded
            .doOnCompleted { viewerChaptersRelay.value?.let(viewerChaptersRelay::call) }
            .onErrorComplete()
            .subscribe()
            .also(::add)
    }

    /**
     * Called every time a page changes on the reader. Used to mark the flag of chapters being
     * read, update tracking services, enqueue downloaded chapter deletion, and updating the active chapter if this
     * [page]'s chapter is different from the currently active.
     */
    fun onPageSelected(page: ReaderPage): Boolean {
        val currentChapters = viewerChaptersRelay.value ?: return false

        val selectedChapter = page.chapter

        // Save last page read and mark as read if needed
        selectedChapter.chapter.last_page_read = page.index
        selectedChapter.chapter.pages_left =
            (selectedChapter.pages?.size ?: page.index) - page.index
        if (selectedChapter.pages?.lastIndex == page.index) {
            selectedChapter.chapter.read = true
            updateTrackChapterRead(selectedChapter)
            enqueueDeleteReadChapters(selectedChapter)
        }

        if (selectedChapter != currentChapters.currChapter) {
            Timber.d("Setting ${selectedChapter.chapter.url} as active")
            onChapterChanged(currentChapters.currChapter, selectedChapter)
            loadNewChapter(selectedChapter)
            return true
        }
        return false
    }

    /**
     * Called when a chapter changed from [fromChapter] to [toChapter]. It updates [fromChapter]
     * on the database.
     */
    private fun onChapterChanged(fromChapter: ReaderChapter, toChapter: ReaderChapter) {
        saveChapterProgress(fromChapter)
        saveChapterHistory(fromChapter)
    }

    /**
     * Saves this [chapter] progress (last read page and whether it's read).
     */
    private fun saveChapterProgress(chapter: ReaderChapter) {
        val dbChapter = db.getChapter(chapter.chapter.id!!).executeAsBlocking()
        chapter.chapter.bookmark = dbChapter!!.bookmark
        db.updateChapterProgress(chapter.chapter).asRxCompletable()
            .onErrorComplete()
            .subscribeOn(Schedulers.io())
            .subscribe()
    }

    /**
     * Saves this [chapter] last read history.
     */
    private fun saveChapterHistory(chapter: ReaderChapter) {
        val history = History.create(chapter.chapter).apply { last_read = Date().time }
        db.updateHistoryLastRead(history).executeAsBlocking()
    }

    /**
     * Called from the activity to preload the given [chapter].
     */
    fun preloadChapter(chapter: ReaderChapter) {
        preload(chapter)
    }

    /**
     * Returns the currently active chapter.
     */
    fun getCurrentChapter(): ReaderChapter? {
        return viewerChaptersRelay.value?.currChapter
    }

    /**
     * Returns the viewer position used by this manga or the default one.
     */
    fun getMangaViewer(): Int {
        val manga = manga ?: return preferences.defaultViewer()

        if (manga.viewer == -1) {
            manga.viewer = manga.defaultReaderType()
            db.updateMangaViewer(manga).asRxObservable().subscribe()
        }

        val viewer = if (manga.viewer == 0) preferences.defaultViewer() else manga.viewer

        return when {
            !manga.isWebtoon() && viewer == ReaderActivity.WEBTOON -> ReaderActivity.VERTICAL_PLUS
            else -> viewer
        }
    }

    /**
     * Updates the viewer position for the open manga.
     */
    fun setMangaViewer(viewer: Int) {
        val manga = manga ?: return
        manga.viewer = viewer

        db.updateMangaViewer(manga).executeAsBlocking()

        Observable.timer(250, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
            .subscribeFirst({ view, _ ->
                val currChapters = viewerChaptersRelay.value
                if (currChapters != null) {
                    // Save current page
                    val currChapter = currChapters.currChapter
                    currChapter.requestedPage = currChapter.chapter.last_page_read

                    // Emit manga and chapters to the new viewer
                    view.setManga(manga)
                    view.setChapters(currChapters)
                }
            })
    }

    /**
     * Saves the image of this [page] in the given [directory] and returns the file location.
     */
    private fun saveImage(page: ReaderPage, directory: File, manga: Manga): File {
        val stream = page.stream!!
        val type = ImageUtil.findImageType(stream) ?: throw Exception("Not an image")

        directory.mkdirs()

        val chapter = page.chapter.chapter

        // create chapter name so its always sorted correctly  max character is 75
        val pageName = parseChapterName(chapter.name, page.number.toString(), chapter.scanlator)
        // take only 150 characters so this file maxes at 225
        val trimmedTitle = manga.title.take(150)

        // Build destination file
        val filename =
            DiskUtil.buildValidFilename("$trimmedTitle - $pageName") + ".${type.extension}"

        val destFile = File(directory, filename)
        stream().use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }

    private fun parseChapterName(
        chapterName: String,
        pageNumber: String,
        scanlator: String?
    ): String {
        val builder = StringBuilder()
        var title = ""
        var vol = ""
        val list = chapterName.split(Regex(" "), 3)

        list.forEach {
            if (it.startsWith("vol.", true)) {
                vol = " Vol." + it.substringAfter(".").padStart(4, '0')
            } else if (it.startsWith("ch.", true)) {
                builder.append(" Ch.")
                builder.append(it.substringAfter(".").padStart(4, '0'))
            } else {
                title = " $it"
            }
        }

        if (vol.isNotBlank()) {
            builder.append(vol)
        }
        builder.append(" Pg.")
        builder.append(pageNumber.padStart(4, '0'))

        if (title.isNotEmpty()) {
            builder.append(title.take(200))
        }

        return builder.toString().trim()
    }

    /**
     * Saves the image of this [page] on the pictures directory and notifies the UI of the result.
     * There's also a notification to allow sharing the image somewhere else or deleting it.
     */
    fun saveImage(page: ReaderPage) {
        if (page.status != Page.READY) return
        val manga = manga ?: return
        val context = Injekt.get<Application>()

        val notifier = SaveImageNotifier(context)
        notifier.onClear()

        // Pictures directory.
        val destDir = File(
            Environment.getExternalStorageDirectory().absolutePath +
                File.separator + Environment.DIRECTORY_PICTURES +
                File.separator + "Neko"
        )

        // Copy file in background.
        Observable.fromCallable { saveImage(page, destDir, manga) }
            .doOnNext { file ->
                DiskUtil.scanMedia(context, file)
                notifier.onComplete(file)
            }
            .doOnError { notifier.onError(it.message) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeFirst(
                { view, file -> view.onSaveImageResult(SaveImageResult.Success(file)) },
                { view, error -> view.onSaveImageResult(SaveImageResult.Error(error)) }
            )
    }

    /**
     * Shares the image of this [page] and notifies the UI with the path of the file to share.
     * The image must be first copied to the internal partition because there are many possible
     * formats it can come from, like a zipped chapter, in which case it's not possible to directly
     * get a path to the file and it has to be decompresssed somewhere first. Only the last shared
     * image will be kept so it won't be taking lots of internal disk space.
     */
    fun shareImage(page: ReaderPage) {
        if (page.status != Page.READY) return
        val manga = manga ?: return
        val context = Injekt.get<Application>()

        val destDir = File(context.cacheDir, "shared_image")

        Observable.fromCallable { destDir.deleteRecursively() } // Keep only the last shared file
            .map { saveImage(page, destDir, manga) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeFirst(
                { view, file -> view.onShareImageResult(file) },
                { _, _ -> /* Empty */ }
            )
    }

    /**
     * Results of the save image feature.
     */
    sealed class SaveImageResult {
        class Success(val file: File) : SaveImageResult()
        class Error(val error: Throwable) : SaveImageResult()
    }

    /**
     * Starts the service that updates the last chapter read in sync services. This operation
     * will run in a background thread and errors are ignored.
     */
    private fun updateTrackChapterRead(readerChapter: ReaderChapter) {
        if (!preferences.autoUpdateTrack()) return
        val manga = manga ?: return

        val chapterRead = readerChapter.chapter.chapter_number.toInt()

        val trackManager = Injekt.get<TrackManager>()

        // We wan't these to execute even if the presenter is destroyed so launch on GlobalScope
        GlobalScope.launch {
            withContext(Dispatchers.IO) {
                val trackList = db.getTracks(manga).executeAsBlocking()
                trackList.map { track ->
                    val service = trackManager.getService(track.sync_id)

                    if (shouldUpdateTracker(service, chapterRead, track)) {
                        try {
                            track.last_chapter_read = chapterRead
                            service!!.update(track)
                            db.insertTrack(track).executeAsBlocking()
                        } catch (e: Exception) {
                            Timber.e(e)
                        }
                    }
                }
            }
        }
    }

    private fun shouldUpdateTracker(
        service: TrackService?,
        chapterRead: Int,
        track: Track
    ): Boolean {
        if (service == null || !service.isLogged || chapterRead <= track.last_chapter_read) {
            return false
        }
        if (service.isMdList() && track.status == FollowStatus.UNFOLLOWED.int) {
            return false
        }
        return true
    }

    /**
     * Enqueues this [chapter] to be deleted when [deletePendingChapters] is called. The download
     * manager handles persisting it across process deaths.
     */
    private fun enqueueDeleteReadChapters(chapter: ReaderChapter) {
        if (!chapter.chapter.read || chapter.pageLoader !is DownloadPageLoader) return
        val manga = manga ?: return

        // Return if the setting is disabled
        val removeAfterReadSlots = preferences.removeAfterReadSlots()
        if (removeAfterReadSlots == -1) return

        Completable
            .fromCallable {
                // Position of the read chapter
                val position = chapterList.indexOf(chapter)

                // Retrieve chapter to delete according to preference
                val chapterToDelete = chapterList.getOrNull(position - removeAfterReadSlots)
                if (chapterToDelete != null) {
                    downloadManager.enqueueDeleteChapters(listOf(chapterToDelete.chapter), manga)
                }
            }
            .onErrorComplete()
            .subscribeOn(Schedulers.io())
            .subscribe()
    }

    /**
     * Deletes all the pending chapters. This operation will run in a background thread and errors
     * are ignored.
     */
    private fun deletePendingChapters() {
        Completable.fromCallable { downloadManager.deletePendingChapters() }
            .onErrorComplete()
            .subscribeOn(Schedulers.io())
            .subscribe()
    }
}
