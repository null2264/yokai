package eu.kanade.tachiyomi.util.chapter

import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.DelayedTrackingUpdateJob
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.util.system.e
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

/**
 * Helper method for syncing a remote track with the local chapters, and back
 *
 * @param db the database.
 * @param chapters a list of chapters from the source.
 * @param remoteTrack the remote Track object.
 * @param service the tracker service.
 */
suspend fun syncChaptersWithTrackServiceTwoWay(
    db: DatabaseHelper,
    chapters: List<Chapter>,
    remoteTrack: Track,
    service: TrackService,
    updateChapter: UpdateChapter = Injekt.get(),
) = withIOContext {
    if (service !is EnhancedTrackService) {
        return@withIOContext
    }

    val sortedChapters = chapters.sortedBy { it.chapter_number }
    sortedChapters
        .filter { chapter -> chapter.chapter_number <= remoteTrack.last_chapter_read && !chapter.read }
        .forEach { it.read = true }
    updateChapter.awaitAll(sortedChapters.map(Chapter::toProgressUpdate))

    // only take into account continuous reading
    val localLastRead = sortedChapters.takeWhile { it.read }.lastOrNull()?.chapter_number ?: 0F

    // update remote
    remoteTrack.last_chapter_read = localLastRead

    try {
        service.update(remoteTrack)
        db.insertTrack(remoteTrack).executeAsBlocking()
    } catch (e: Throwable) {
        Logger.w(e)
    }
}

private var trackingJobs = HashMap<Long, Pair<Job?, Float?>>()

/**
 * Starts the service that updates the last chapter read in sync services. This operation
 * will run in a background thread and errors are ignored.
 */
fun updateTrackChapterMarkedAsRead(
    db: DatabaseHelper,
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
            updateTrackChapterRead(db, preferences, mangaId, newChapterRead)
            fetchTracks?.invoke()
            trackingJobs.remove(mangaId)
        } to newChapterRead
    }
}

suspend fun updateTrackChapterRead(
    db: DatabaseHelper,
    preferences: PreferencesHelper,
    mangaId: Long?,
    newChapterRead: Float,
    retryWhenOnline: Boolean = false,
    getTrack: GetTrack = Injekt.get()
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
                    db.insertTrack(track).executeAsBlocking()
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
