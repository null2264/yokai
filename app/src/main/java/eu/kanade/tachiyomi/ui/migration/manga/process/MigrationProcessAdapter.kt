package eu.kanade.tachiyomi.ui.migration.manga.process

import android.view.MenuItem
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.updateCoverLastModified
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.migration.MigrationFlags
import eu.kanade.tachiyomi.util.system.launchUI
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.category.interactor.GetCategories
import yokai.domain.category.interactor.SetMangaCategories
import yokai.domain.chapter.interactor.GetChapter
import yokai.domain.chapter.interactor.UpdateChapter
import yokai.domain.chapter.models.ChapterUpdate
import yokai.domain.history.interactor.GetHistory
import yokai.domain.history.interactor.UpsertHistory
import yokai.domain.library.custom.model.CustomMangaInfo.Companion.getMangaInfo
import yokai.domain.manga.interactor.GetManga
import yokai.domain.manga.interactor.UpdateManga
import yokai.domain.manga.models.MangaUpdate
import yokai.domain.track.interactor.GetTrack
import yokai.domain.track.interactor.InsertTrack
import yokai.domain.ui.UiPreferences

class MigrationProcessAdapter(
    val controller: MigrationListController,
) : FlexibleAdapter<MigrationProcessItem>(null, controller, true) {

    private val getCategories: GetCategories by injectLazy()
    private val getManga: GetManga by injectLazy()

    var items: List<MigrationProcessItem> = emptyList()
    val preferences: PreferencesHelper by injectLazy()
    val uiPreferences: UiPreferences by injectLazy()
    val sourceManager: SourceManager by injectLazy()
    val coverCache: CoverCache by injectLazy()
    val customMangaManager: CustomMangaManager by injectLazy()

    var showOutline = uiPreferences.outlineOnCovers().get()
    val menuItemListener: MigrationProcessInterface = controller

    private val enhancedServices by lazy {
        Injekt.get<TrackManager>().services.filterIsInstance<EnhancedTrackService>()
    }
    // NEW: batch safety (snapshot of items used in Migrate All)
    private var migrationBatch: List<MigrationProcessItem> = emptyList()

    override fun updateDataSet(items: List<MigrationProcessItem>?) {
        this.items = items ?: emptyList()
        super.updateDataSet(items)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        (holder as? MigrationProcessHolder)?.onRecycled()
    }

    interface MigrationProcessInterface {
        fun onMenuItemClick(position: Int, item: MenuItem)
        fun enableButtons()
        fun removeManga(item: MigrationProcessItem)
        fun noMigration()
        fun updateCount()
    }

    fun sourceFinished() {
        menuItemListener.updateCount()
        if (itemCount == 0) menuItemListener.noMigration()
        if (allMangasDone()) menuItemListener.enableButtons()
    }

    fun allMangasDone() = (
        items.all {
            it.manga.migrationStatus != MigrationStatus.RUNNUNG
        } && items.any { it.manga.migrationStatus == MigrationStatus.MANGA_FOUND }
        )

    fun mangasSkipped() =
        items.count { it.manga.migrationStatus == MigrationStatus.MANGA_NOT_FOUND }

    // =========================
    // FIXED MIGRATE ALL
    // =========================
    suspend fun performMigrations(copy: Boolean, onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }) {
        migrationBatch = currentItems.toList()
        val total = migrationBatch.size

        withContext(Dispatchers.IO) {
            migrationBatch.forEachIndexed { index, migratingManga ->
                val manga = migratingManga.manga
                if (manga.searchResult.initialized) {
                    val toMangaObj =
                        getManga.awaitById(manga.searchResult.get() ?: return@forEachIndexed)
                            ?: return@forEachIndexed

                    val prevManga = manga.manga() ?: return@forEachIndexed
                    val source = sourceManager.get(toMangaObj.source) ?: return@forEachIndexed
                    val prevSource = sourceManager.get(prevManga.source)
                    migrateMangaInternal(
                        prevSource,
                        source,
                        prevManga,
                        toMangaObj,
                        !copy,
                    )
                }
                withContext(Dispatchers.Main) {
                    onProgress(index + 1, total)
                }
            }
        }
        migrationBatch = emptyList()
    }

    fun migrateManga(position: Int, copy: Boolean) {
        launchUI {
            val manga = getItem(position)?.manga ?: return@launchUI
            val toMangaObj =
                getManga.awaitById(manga.searchResult.get() ?: return@launchUI)
                    ?: return@launchUI

            val prevManga = manga.manga() ?: return@launchUI
            val source = sourceManager.get(toMangaObj.source) ?: return@launchUI
            val prevSource = sourceManager.get(prevManga.source)
            migrateMangaInternal(
                prevSource,
                source,
                prevManga,
                toMangaObj,
                !copy,
            )
            removeManga(position)
        }
    }

    fun removeManga(position: Int) {
        val item = getItem(position) ?: return
        menuItemListener.removeManga(item)
        item.manga.migrationJob.cancel()
        removeItem(position)
        items = currentItems
        sourceFinished()
    }

    private suspend fun migrateMangaInternal(
        prevSource: Source?,
        source: Source,
        prevManga: Manga,
        manga: Manga,
        replace: Boolean,
    ) {
        if (controller.config == null) return
        val flags = preferences.migrateFlags().get()
        migrateMangaInternal(
            flags,
            enhancedServices,
            coverCache,
            customMangaManager,
            prevSource,
            source,
            prevManga,
            manga,
            replace,
        )
    }

    companion object {

        suspend fun migrateMangaInternal(
            flags: Int,
            enhancedServices: List<EnhancedTrackService>,
            coverCache: CoverCache,
            customMangaManager: CustomMangaManager,
            prevSource: Source?,
            source: Source,
            prevManga: Manga,
            manga: Manga,
            replace: Boolean,
        ) {
            // Update chapters read
            if (MigrationFlags.hasChapters(flags)) {
                val getChapter: GetChapter = Injekt.get()
                val updateChapter: UpdateChapter = Injekt.get()
                val getHistory: GetHistory = Injekt.get()
                val upsertHistory: UpsertHistory = Injekt.get()

                val prevMangaChapters = getChapter.awaitAll(prevManga, false)
                val maxChapterRead =
                    prevMangaChapters.filter { it.read }.maxOfOrNull { it.chapter_number } ?: 0f
                val dbChapters = getChapter.awaitAll(manga, false)
                val prevHistoryList = getHistory.awaitAllByMangaId(prevManga.id!!)
                val historyList = mutableListOf<History>()
                val chapterUpdates = mutableListOf<ChapterUpdate>()
                for (chapter in dbChapters) {
                    if (chapter.isRecognizedNumber) {
                        var update: ChapterUpdate? = null
                        val prevChapter =
                            prevMangaChapters.find {
                                it.isRecognizedNumber &&
                                    it.chapter_number == chapter.chapter_number
                            }

                        if (prevChapter != null) {
                            // copy data from prevChapter -> chapter
                            update = ChapterUpdate(
                                id = chapter.id!!,
                                bookmark = prevChapter.bookmark,
                                read = prevChapter.read,
                                dateFetch = prevChapter.date_fetch,
                            )
                            prevHistoryList.find { it.chapter_id == prevChapter.id }
                                ?.let { prevHistory ->
                                    val history = History.create(chapter).apply {
                                        last_read = prevHistory.last_read
                                        time_read = prevHistory.time_read
                                    }
                                    historyList.add(history)
                                }
                        } else if (chapter.chapter_number <= maxChapterRead) {
                            update = ChapterUpdate(
                                id = chapter.id!!,
                                read = true
                            )
                        }
                        update?.let { chapterUpdates.add(it) }
                    }
                }
                updateChapter.awaitAll(chapterUpdates)
                upsertHistory.awaitBulk(historyList)
            }
            // Update categories
            if (MigrationFlags.hasCategories(flags)) {
                val categories = Injekt.get<GetCategories>().awaitByMangaId(prevManga.id)
                Injekt.get<SetMangaCategories>().await(
                    manga.id,
                    categories.mapNotNull { it.id?.toLong() }
                )
            }
            // Update track
            if (MigrationFlags.hasTracks(flags)) {
                val tracksToUpdate =
                    Injekt.get<GetTrack>().awaitAllByMangaId(prevManga.id).mapNotNull { track ->
                        track.id = null
                        track.manga_id = manga.id!!

                        val service = enhancedServices
                            .firstOrNull { it.isTrackFrom(track, prevManga, prevSource) }
                        if (service != null) {
                            service.migrateTrack(track, manga, source)
                        } else {
                            track
                        }
                    }
                Injekt.get<InsertTrack>().awaitBulk(tracksToUpdate)
            }
            val updateManga: UpdateManga = Injekt.get()
            // Update favorite status
            if (replace) {
                prevManga.favorite = false
                updateManga.await(
                    MangaUpdate(
                        id = prevManga.id!!,
                        favorite = false,
                    )
                )
            }

            manga.favorite = true
            // Update custom cover & info
            manga.date_added =
                if (replace) prevManga.date_added else Date().time

            if (MigrationFlags.hasCustomMangaInfo(flags)) {
                if (coverCache.getCustomCoverFile(prevManga).exists()) {
                    coverCache.setCustomCoverToCache(
                        manga,
                        coverCache.getCustomCoverFile(prevManga).inputStream()
                    )
                    manga.updateCoverLastModified()
                }

                customMangaManager.getManga(prevManga)?.let { customManga ->
                    customMangaManager.updateMangaInfo(
                        prevManga.id,
                        manga.id,
                        customManga.getMangaInfo()
                    )
                }
            }

            updateManga.await(
                MangaUpdate(
                    id = manga.id!!,
                    title = manga.title,
                    favorite = manga.favorite,
                    dateAdded = manga.date_added,
                )
            )
        }
    }
}
