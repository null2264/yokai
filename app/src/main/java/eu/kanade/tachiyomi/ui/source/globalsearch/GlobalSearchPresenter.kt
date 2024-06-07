package eu.kanade.tachiyomi.ui.source.globalsearch

import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithSource
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.withUIContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.Date
import java.util.Locale

/**
 * Presenter of [GlobalSearchController]
 * Function calls should be done from here. UI calls should be done from the controller.
 *
 * @param sourceManager manages the different sources.
 * @param db manages the database calls.
 * @param preferences manages the preference calls.
 */
open class GlobalSearchPresenter(
    private val initialQuery: String? = "",
    private val initialExtensionFilter: String? = null,
    private val sourcesToUse: List<CatalogueSource>? = null,
    val sourceManager: SourceManager = Injekt.get(),
    val db: DatabaseHelper = Injekt.get(),
    private val preferences: PreferencesHelper = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
) : BaseCoroutinePresenter<GlobalSearchController>() {

    /**
     * Enabled sources.
     */
    val sources by lazy { getSourcesToQuery() }

    private var fetchSourcesJob: Job? = null

    private var loadTime = hashMapOf<Long, Long>()

    var query = ""

    private val fetchImageFlow = MutableSharedFlow<Pair<List<Manga>, Source>>()

    private var fetchImageJob: Job? = null

    private val extensionManager: ExtensionManager by injectLazy()

    private var extensionFilter: String? = null

    var items: List<GlobalSearchItem> = emptyList()

    private val semaphore = Semaphore(5)

    override fun onCreate() {
        super.onCreate()

        extensionFilter = initialExtensionFilter

        if (items.isEmpty()) {
            // Perform a search with previous or initial state
            search(initialQuery.orEmpty())
        }
        presenterScope.launchUI {
            view?.setItems(items)
        }
    }

    /**
     * Returns a list of enabled sources ordered by language and name.
     *
     * @return list containing enabled sources.
     */
    protected open fun getEnabledSources(): List<CatalogueSource> {
        val languages = preferences.enabledLanguages().get()
        val hiddenCatalogues = preferences.hiddenSources().get()
        val pinnedCatalogues = preferences.pinnedCatalogues().get()

        val list = sourceManager.getCatalogueSources()
            .filter { it.lang in languages }
            .filterNot { it.id.toString() in hiddenCatalogues }
            .sortedBy { "(${it.lang}) ${it.name}" }

        return if (preferences.onlySearchPinned().get()) {
            list.filter { it.id.toString() in pinnedCatalogues }
        } else {
            list.sortedBy { it.id.toString() !in pinnedCatalogues }
        }
    }

    private fun getSourcesToQuery(): List<CatalogueSource> {
        if (sourcesToUse != null) return sourcesToUse
        val filter = extensionFilter
        val enabledSources = getEnabledSources()
        if (filter.isNullOrEmpty()) {
            return enabledSources
        }

        val languages = preferences.enabledLanguages().get()
        val filterSources = extensionManager.installedExtensionsFlow.value
            .filter { it.pkgName == filter }
            .flatMap { it.sources }
            .filter { it.lang in languages }
            .filterIsInstance<CatalogueSource>()

        if (filterSources.isEmpty()) {
            return enabledSources
        }

        return filterSources
    }

    /**
     * Creates a catalogue search item
     */
    protected open fun createCatalogueSearchItem(
        source: CatalogueSource,
        results: List<GlobalSearchMangaItem>?,
    ): GlobalSearchItem {
        return GlobalSearchItem(source, results)
    }

    fun confirmDeletion(manga: Manga) {
        coverCache.deleteFromCache(manga)
        val downloadManager: DownloadManager = Injekt.get()
        sourceManager.get(manga.source)?.let { source ->
            downloadManager.deleteManga(manga, source)
        }
    }

    /**
     * Initiates a search for manga per catalogue.
     *
     * @param query query on which to search.
     */
    fun search(query: String) {
        // Return if there's nothing to do
        if (this.query == query) return

        // Update query
        this.query = query

        // Create image fetch subscription
        initializeFetchImageSubscription()

        // Create items with the initial state
        val initialItems = sources.map { createCatalogueSearchItem(it, null) }
        items = initialItems
        presenterScope.launchUI { view?.setItems(items) }
        val pinnedSourceIds = preferences.pinnedCatalogues().get()

        fetchSourcesJob?.cancel()
        fetchSourcesJob = presenterScope.launch {
            sources.map { source ->
                launch mainLaunch@{
                    semaphore.withPermit {
                        if (this@GlobalSearchPresenter.items.find { it.source == source }?.results != null) {
                            return@mainLaunch
                        }
                        val mangas = try {
                            source.getSearchManga(1, query, source.getFilterList())
                        } catch (error: Exception) {
                            MangasPage(emptyList(), false)
                        }
                            .mangas.take(10)
                            .map { networkToLocalManga(it, source.id) }
                        fetchImage(mangas, source)
                        if (mangas.isNotEmpty() && !loadTime.containsKey(source.id)) {
                            loadTime[source.id] = Date().time
                        }
                        val result = createCatalogueSearchItem(
                            source,
                            mangas.map { GlobalSearchMangaItem(it) },
                        )
                        items = items
                            .map { item -> if (item.source == result.source) result else item }
                            .sortedWith(
                                compareBy(
                                    // Bubble up sources that actually have results
                                    { it.results.isNullOrEmpty() },
                                    // Same as initial sort, i.e. pinned first then alphabetically
                                    { it.source.id.toString() !in pinnedSourceIds },
                                    { loadTime[it.source.id] ?: 0L },
                                    { "${it.source.name.lowercase(Locale.getDefault())} (${it.source.lang})" },
                                ),
                            )
                        withUIContext { view?.setItems(items) }
                    }
                }
            }
        }
    }

    /**
     * Initialize a list of manga.
     *
     * @param manga the list of manga to initialize.
     */
    private fun fetchImage(manga: List<Manga>, source: Source) {
        presenterScope.launch {
            fetchImageFlow.emit(Pair(manga, source))
        }
    }

    /**
     * Subscribes to the initializer of manga details and updates the view if needed.
     */
    private fun initializeFetchImageSubscription() {
        fetchImageJob?.cancel()
        fetchImageJob = fetchImageFlow.onEach { (mangaList, source) ->
            mangaList
                .filter { (it.thumbnail_url == null && !it.initialized) || preferences.fetchMangaChapters().get() }
                .forEach {
                    presenterScope.launchIO {
                        try {
                            val manga = getMangaDetails(it, source)
                            if (preferences.fetchMangaChapters().get()) {
                                // to let the database transaction for newly added manga chapters complete before adding the chapters count badge
                                delay(1250)
                            }
                            withUIContext {
                                view?.onMangaInitialized(source as CatalogueSource, manga)
                            }
                        } catch (_: Exception) {
                            withUIContext {
                                view?.onMangaInitialized(source as CatalogueSource, it)
                            }
                        }
                    }
                }
        }.launchIn(presenterScope)
    }

    /**
     * Initializes the given manga.
     *
     * @param manga the manga to initialize.
     * @return The initialized manga.
     */
    private suspend fun getMangaDetails(manga: Manga, source: Source): Manga {
        val networkManga = source.getMangaDetails(manga.copy())
        manga.copyFrom(networkManga)
        manga.initialized = true
        db.insertManga(manga).executeAsBlocking()
        if (preferences.fetchMangaChapters().get()) {
            fetchChaptersFromSource(manga, source)
        }
        return manga
    }

    /**
     * Returns a manga from the database for the given manga from network. It creates a new entry
     * if the manga is not yet in the database.
     *
     * @param sManga the manga from the source.
     * @return a manga from the database.
     */
    protected open fun networkToLocalManga(sManga: SManga, sourceId: Long): Manga {
        var localManga = db.getManga(sManga.url, sourceId).executeAsBlocking()
        if (localManga == null) {
            val newManga = Manga.create(sManga.url, sManga.title, sourceId)
            newManga.copyFrom(sManga)
            val result = db.insertManga(newManga).executeAsBlocking()
            newManga.id = result.insertedId()
            localManga = newManga
        } else if (!localManga.favorite) {
            // if the manga isn't a favorite, set its display title from source
            // if it later becomes a favorite, updated title will go to db
            localManga.title = sManga.title
        }
        return localManga
    }

    private suspend fun fetchChaptersFromSource(manga: Manga, source: Source) {
        try {
            val chapters = source.getChapterList(manga)
            syncChaptersWithSource(db, chapters, manga, source)
        } catch (e: Exception) {
            if (!e.message.isNullOrBlank() && !e.message!!.contains("No chapters found")) {
                Timber.e(e)
            }
        }
    }
}
