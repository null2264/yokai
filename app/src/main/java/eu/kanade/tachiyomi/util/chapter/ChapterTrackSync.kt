package eu.kanade.tachiyomi.util.chapter

import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.DelayedTrackingUpdateJob
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.util.system.isOnline
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.w
import eu.kanade.tachiyomi.util.system.withIOContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.chapter.interactor.UpdateChapter
import yokai.domain.track.interactor.GetTrack
import yokai.domain.track.interactor.InsertTrack
import kotlin.math.max

/**
 * Helper method for syncing a remote track with the local chapters, and back
 *
 * @param db the database.
 * @param chapters a list of chapters from the source.
 * @param remoteTrack the remote Track object.
 * @param service the tracker service.
 */
suspend fun syncChaptersWithTrackServiceTwoWay(
    chapters: List<Chapter>,
    remoteTrack: Track,
    service: TrackService,
    updateChapter: UpdateChapter = Injekt.get(),
    insertTrack: InsertTrack = Injekt.get(),
): Int? = withIOContext {
    val syncResult = calculateTwoWayTrackerSync(chapters, remoteTrack.last_chapter_read)
    val lastRead = max(remoteTrack.last_chapter_read.toDouble(), syncResult.localLastRead.toDouble()).toFloat()

    try {
        if (lastRead > remoteTrack.last_chapter_read) {
            remoteTrack.last_chapter_read = lastRead
            val updatedTrack = service.update(remoteTrack)
            insertTrack.await(updatedTrack)
        }

        if (
            syncResult.chapterUpdates.isNotEmpty() &&
            !service.hasNotStartedReading(remoteTrack.status)
        ) {
            syncResult.chapterUpdates.forEach { it.read = true }
            updateChapter.awaitAll(syncResult.chapterUpdates.map(Chapter::toProgressUpdate))
            return@withIOContext lastRead.toInt()
        }
    } catch (e: Throwable) {
        Logger.w(e)
    }

    return@withIOContext null
}

internal data class TwoWayTrackerSyncResult(
    val coveredChapterNumbers: Set<Float>,
    val chapterUpdates: List<Chapter>,
    val localLastRead: Float,
)

internal fun calculateTwoWayTrackerSync(
    chapters: List<Chapter>,
    remoteLastRead: Float,
): TwoWayTrackerSyncResult {
    val recognizedChapters = chapters
        .filter { it.isRecognizedNumber }
        .sortedByDescending { it.source_order }

    val sortedChapters = recognizedChapters.sortedBy { it.chapter_number }
    val orderedUniqueChapterNumbers = recognizedChapters
        .map { it.chapter_number }
        .distinct()

    var lastCheckChapter = 0.0f

    val coveredChapterNumbers = orderedUniqueChapterNumbers
        .takeWhile { chapterNumber ->
            val isCovered = chapterNumber >= lastCheckChapter && chapterNumber <= remoteLastRead
            if (isCovered) {
                lastCheckChapter = chapterNumber
            }
            isCovered
        }
        .toSet()

    val chapterUpdates = recognizedChapters
        .filter { it.chapter_number in coveredChapterNumbers }
        .filterNot { it.read }

    val localLastRead = sortedChapters
        .takeWhile { it.read }
        .lastOrNull()
        ?.chapter_number
        ?: 0f

    return TwoWayTrackerSyncResult(
        coveredChapterNumbers = coveredChapterNumbers,
        chapterUpdates = chapterUpdates,
        localLastRead = localLastRead,
    )
}

private var trackingJobs = HashMap<Long, Pair<Job?, Float?>>()

/**
 * Starts the service that updates the last chapter read in sync services. This operation
 * will run in a background thread and errors are ignored.
 */
fun updateTrackChapterMarkedAsRead(
    preferences: PreferencesHelper,
    newLastChapter: Chapter?,
    mangaId: Long?,
    delay: Long = 3000,
    fetchTracks: (suspend () -> Unit)? = null,
) {
    if (!preferences.trackMarkedAsRead().get()) return
    mangaId ?: return

    val newChapterRead = newLastChapter?.chapter_number ?: 0f

    // To avoid unnecessary calls if multiple marked as read for same manga
    if ((trackingJobs[mangaId]?.second ?: 0f) < newChapterRead) {
        trackingJobs[mangaId]?.first?.cancel()

        // We want these to execute even if the presenter is destroyed
        trackingJobs[mangaId] = launchIO {
            delay(delay)
            updateTrackChapterRead(preferences, mangaId, newChapterRead)
            fetchTracks?.invoke()
            trackingJobs.remove(mangaId)
        } to newChapterRead
    }
}

suspend fun updateTrackChapterRead(
    preferences: PreferencesHelper,
    mangaId: Long?,
    newChapterRead: Float,
    retryWhenOnline: Boolean = false,
    getTrack: GetTrack = Injekt.get(),
    insertTrack: InsertTrack = Injekt.get(),
): List<Pair<TrackService, String?>> {
    val trackManager = Injekt.get<TrackManager>()
    val trackList = getTrack.awaitAllByMangaId(mangaId)
    val failures = mutableListOf<Pair<TrackService, String?>>()
    trackList.map { track ->
        val service = trackManager.getService(track.sync_id)
        if (service != null && service.isLogged && newChapterRead > track.last_chapter_read) {
            if (retryWhenOnline && !preferences.context.isOnline()) {
                delayTrackingUpdate(preferences, mangaId, newChapterRead, track)
            } else if (preferences.context.isOnline()) {
                try {
                    track.last_chapter_read = newChapterRead
                    service.update(track, true)
                    insertTrack.await(track)
                } catch (e: Exception) {
                    Logger.e(e) { "Unable to update tracker [tracker id ${track.sync_id}]" }
                    failures.add(service to e.localizedMessage)
                    if (retryWhenOnline) {
                        delayTrackingUpdate(preferences, mangaId, newChapterRead, track)
                    }
                }
            }
        }
    }
    return failures
}

private fun delayTrackingUpdate(
    preferences: PreferencesHelper,
    mangaId: Long?,
    newChapterRead: Float,
    track: Track,
) {
    val trackings = preferences.trackingsToAddOnline().get().toMutableSet()
    val currentTracking = trackings.find { it.startsWith("$mangaId:${track.sync_id}:") }
    trackings.remove(currentTracking)
    trackings.add("$mangaId:${track.sync_id}:$newChapterRead")
    preferences.trackingsToAddOnline().set(trackings)
    DelayedTrackingUpdateJob.setupTask(preferences.context)
}
