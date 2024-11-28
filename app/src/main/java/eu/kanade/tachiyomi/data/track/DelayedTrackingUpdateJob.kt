package eu.kanade.tachiyomi.data.track

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.system.e
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.manga.interactor.GetManga
import yokai.domain.track.interactor.GetTrack

class DelayedTrackingUpdateJob(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val getManga: GetManga by injectLazy()
    private val getTrack: GetTrack by injectLazy()

    override suspend fun doWork(): Result {
        val preferences = Injekt.get<PreferencesHelper>()
        val db = Injekt.get<DatabaseHelper>()
        val trackManager = Injekt.get<TrackManager>()
        val trackings = preferences.trackingsToAddOnline().get().toMutableSet().mapNotNull {
            val items = it.split(":")
            if (items.size != 3) {
                null
            } else {
                val mangaId = items[0].toLongOrNull() ?: return@mapNotNull null
                val trackId = items[1].toLongOrNull() ?: return@mapNotNull null
                val chapterNumber = items[2].toFloatOrNull() ?: return@mapNotNull null
                mangaId to (trackId to chapterNumber)
            }
        }.groupBy { it.first }
        withContext(Dispatchers.IO) {
            trackings.forEach {
                val mangaId = it.key
                val manga = getManga.awaitById(mangaId) ?: return@withContext
                val trackList = getTrack.awaitAllByMangaId(manga.id)
                it.value.map { tC ->
                    val trackChapter = tC.second
                    val service = trackManager.getService(trackChapter.first)
                    val track = trackList.find { track -> track.sync_id == trackChapter.first }
                    if (service != null && track != null) {
                        try {
                            track.last_chapter_read = trackChapter.second
                            service.update(track, true)
                            db.insertTrack(track).executeAsBlocking()
                        } catch (e: Exception) {
                            Logger.e(e) { "Unable to update tracker [tracker id ${track.sync_id}]" }
                        }
                    }
                }
            }
            preferences.trackingsToAddOnline().set(emptySet())
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "DelayedTrackingUpdate"

        fun setupTask(context: Context) {
            val constraints =
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = OneTimeWorkRequestBuilder<DelayedTrackingUpdateJob>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
