package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.chapter.ChapterUtil
import eu.kanade.tachiyomi.util.manga.MangaUtil
import eu.kanade.tachiyomi.util.system.launchNow
import kotlin.math.max
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.category.interactor.GetCategories
import yokai.domain.chapter.interactor.GetChapter
import yokai.domain.chapter.interactor.InsertChapter
import yokai.domain.chapter.interactor.UpdateChapter
import yokai.domain.history.interactor.GetHistory
import yokai.domain.history.interactor.UpsertHistory
import yokai.domain.library.custom.model.CustomMangaInfo
import yokai.domain.manga.interactor.GetManga
import yokai.domain.manga.interactor.InsertManga
import yokai.domain.manga.interactor.UpdateManga
import yokai.domain.track.interactor.GetTrack

class MangaBackupRestorer(
    private val db: DatabaseHelper = Injekt.get(),
    private val customMangaManager: CustomMangaManager = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getChapter: GetChapter = Injekt.get(),
    private val insertChapter: InsertChapter = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val insertManga: InsertManga = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getHistory: GetHistory = Injekt.get(),
    private val upsertHistory: UpsertHistory = Injekt.get(),
    private val getTrack: GetTrack = Injekt.get(),
) {
    suspend fun restoreManga(
        backupManga: BackupManga,
        backupCategories: List<BackupCategory>,
        onComplete: (Manga) -> Unit,
        onError: (Manga, Throwable) -> Unit,
    ) {
        val manga = backupManga.getMangaImpl()
        val chapters = backupManga.getChaptersImpl()
        val categories = backupManga.categories
        val history =
            backupManga.brokenHistory.map { BackupHistory(it.url, it.lastRead, it.readDuration) } + backupManga.history
        val tracks = backupManga.getTrackingImpl()
        val customManga = backupManga.getCustomMangaInfo()
        val filteredScanlators = backupManga.excludedScanlators

        try {
            val dbManga = getManga.awaitByUrlAndSource(manga.url, manga.source)
            if (dbManga == null) {
                // Manga not in database
                restoreNewManga(manga, chapters, categories, history, tracks, backupCategories, filteredScanlators, customManga)
            } else {
                // Manga in database
                // Copy information from manga already in database
                manga.id = dbManga.id
                manga.filtered_scanlators = dbManga.filtered_scanlators
                manga.copyFrom(dbManga)
                updateManga.await(manga.toMangaUpdate())
                // Fetch rest of manga information
                restoreExistingManga(manga, chapters, categories, history, tracks, backupCategories, filteredScanlators, customManga)
            }
        } catch (e: Exception) {
            onError(manga, e)
        }

        onComplete(manga)
        LibraryUpdateJob.updateMutableFlow.tryEmit(manga.id)
    }

    /**
     * Fetches manga information
     *
     * @param manga manga that needs updating
     * @param chapters chapters of manga that needs updating
     * @param categories categories that need updating
     */
    private suspend fun restoreNewManga(
        manga: Manga,
        chapters: List<Chapter>,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<Track>,
        backupCategories: List<BackupCategory>,
        filteredScanlators: List<String>,
        customManga: CustomMangaInfo?,
    ) {
        val fetchedManga = manga.also {
            it.initialized = it.description != null
            it.id = insertManga.await(it)
        }
        fetchedManga.id ?: return

        restoreChapters(fetchedManga, chapters)
        restoreExtras(fetchedManga, categories, history, tracks, backupCategories, filteredScanlators, customManga)
    }

    private suspend fun restoreExistingManga(
        backupManga: Manga,
        chapters: List<Chapter>,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<Track>,
        backupCategories: List<BackupCategory>,
        filteredScanlators: List<String>,
        customManga: CustomMangaInfo?,
    ) {
        restoreChapters(backupManga, chapters)
        restoreExtras(backupManga, categories, history, tracks, backupCategories, filteredScanlators, customManga)
    }

    private suspend fun restoreChapters(manga: Manga, chapters: List<Chapter>) {
        val dbChapters = getChapter.awaitAll(manga)

        chapters.forEach { chapter ->
            val dbChapter = dbChapters.find { it.url == chapter.url }
            if (dbChapter != null) {
                chapter.id = dbChapter.id
                chapter.copyFrom(dbChapter as SChapter)
                if (dbChapter.read && !chapter.read) {
                    chapter.read = dbChapter.read
                    chapter.last_page_read = dbChapter.last_page_read
                } else if (chapter.last_page_read == 0 && dbChapter.last_page_read != 0) {
                    chapter.last_page_read = dbChapter.last_page_read
                }
                if (!chapter.bookmark && dbChapter.bookmark) {
                    chapter.bookmark = dbChapter.bookmark
                }
            }

            chapter.manga_id = manga.id
        }

        val newChapters = chapters.groupBy { it.id != null }
        newChapters[true]?.let { updateChapter.awaitAll(it.map(Chapter::toProgressUpdate)) }
        newChapters[false]?.let { insertChapter.awaitBulk(it) }
    }

    private suspend fun restoreExtras(
        manga: Manga,
        categories: List<Int>,
        history: List<BackupHistory>,
        tracks: List<Track>,
        backupCategories: List<BackupCategory>,
        filteredScanlators: List<String>,
        customManga: CustomMangaInfo?,
    ) {
        restoreCategories(manga, categories, backupCategories)
        restoreHistoryForManga(history)
        restoreTrackForManga(manga, tracks)
        restoreFilteredScanlatorsForManga(manga, filteredScanlators)
        customManga?.let {
            it.mangaId = manga.id!!
            launchNow {
                customMangaManager.saveMangaInfo(it)
            }
        }
    }

    /**
     * Restores the categories a manga is in.
     *
     * @param manga the manga whose categories have to be restored.
     * @param categories the categories to restore.
     */
    private suspend fun restoreCategories(manga: Manga, categories: List<Int>, backupCategories: List<BackupCategory>) {
        val dbCategories = getCategories.await()
        val mangaCategoriesToUpdate = ArrayList<MangaCategory>(categories.size)
        categories.forEach { backupCategoryOrder ->
            backupCategories.firstOrNull {
                it.order == backupCategoryOrder
            }?.let { backupCategory ->
                dbCategories.firstOrNull { dbCategory ->
                    dbCategory.name == backupCategory.name
                }?.let { dbCategory ->
                    mangaCategoriesToUpdate += MangaCategory.create(manga, dbCategory)
                }
            }
        }

        // Update database
        if (mangaCategoriesToUpdate.isNotEmpty()) {
            db.deleteOldMangasCategories(listOf(manga)).executeAsBlocking()
            db.insertMangasCategories(mangaCategoriesToUpdate).executeAsBlocking()
        }
    }

    /**
     * Restore history from Json
     *
     * @param history list containing history to be restored
     */
    internal suspend fun restoreHistoryForManga(history: List<BackupHistory>) {
        // List containing history to be updated
        val historyToBeUpdated = ArrayList<History>(history.size)
        for ((url, lastRead, readDuration) in history) {
            val dbHistory = getHistory.awaitByChapterUrl(url)
            // Check if history already in database and update
            if (dbHistory != null) {
                dbHistory.apply {
                    last_read = max(lastRead, dbHistory.last_read)
                    time_read = max(readDuration, dbHistory.time_read)
                }
                historyToBeUpdated.add(dbHistory)
            } else {
                // If not in database create
                getChapter.awaitByUrl(url, false)?.let {
                    val historyToAdd = History.create(it).apply {
                        last_read = lastRead
                        time_read = readDuration
                    }
                    historyToBeUpdated.add(historyToAdd)
                }
            }
        }
        upsertHistory.awaitBulk(historyToBeUpdated)
    }

    /**
     * Restores the sync of a manga.
     *
     * @param manga the manga whose sync have to be restored.
     * @param tracks the track list to restore.
     */
    private suspend fun restoreTrackForManga(manga: Manga, tracks: List<Track>) {
        // Fix foreign keys with the current manga id
        tracks.map { it.manga_id = manga.id!! }

        // Get tracks from database
        val dbTracks = getTrack.awaitAllByMangaId(manga.id!!)
        val trackToUpdate = mutableListOf<Track>()

        tracks.forEach { track ->
            var isInDatabase = false
            for (dbTrack in dbTracks) {
                if (track.sync_id == dbTrack.sync_id) {
                    // The sync is already in the db, only update its fields
                    if (track.media_id != dbTrack.media_id) {
                        dbTrack.media_id = track.media_id
                    }
                    if (track.library_id != dbTrack.library_id) {
                        dbTrack.library_id = track.library_id
                    }
                    dbTrack.last_chapter_read = max(dbTrack.last_chapter_read, track.last_chapter_read)
                    isInDatabase = true
                    trackToUpdate.add(dbTrack)
                    break
                }
            }
            if (!isInDatabase) {
                // Insert new sync. Let the db assign the id
                track.id = null
                trackToUpdate.add(track)
            }
        }
        // Update database
        if (trackToUpdate.isNotEmpty()) {
            db.insertTracks(trackToUpdate).executeAsBlocking()
        }
    }

    private suspend fun restoreFilteredScanlatorsForManga(manga: Manga, filteredScanlators: List<String>) {
        val actualList = ChapterUtil.getScanlators(manga.filtered_scanlators) + filteredScanlators
        MangaUtil.setScanlatorFilter(updateManga, manga, actualList.toSet())
    }
}
