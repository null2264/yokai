package eu.kanade.tachiyomi.data.database.models

import java.io.Serializable

interface Track : Serializable {

    var id: Long?

    var manga_id: Long

    var sync_id: Long

    var media_id: Long

    var library_id: Long?

    var title: String

    var last_chapter_read: Float

    var total_chapters: Long

    var score: Float

    var status: Int

    var started_reading_date: Long

    var finished_reading_date: Long

    var tracking_url: String

    fun copyPersonalFrom(other: Track) {
        last_chapter_read = other.last_chapter_read
        score = other.score
        status = other.status
        started_reading_date = other.started_reading_date
        finished_reading_date = other.finished_reading_date
    }

    companion object {
        fun create(serviceId: Long): Track = TrackImpl().apply {
            sync_id = serviceId
        }

        fun mapper(
            id: Long,
            mangaId: Long,
            syncId: Long,
            remoteId: Long,
            libraryId: Long?,
            title: String,
            lastChapterRead: Double,
            totalChapters: Long,
            status: Long,
            score: Double,
            remoteUrl: String,
            startDate: Long,
            finishDate: Long,
        ) = TrackImpl().apply {
            this.id = id
            this.manga_id = mangaId
            this.sync_id = syncId
            this.media_id = remoteId
            this.library_id = libraryId
            this.title = title
            this.last_chapter_read = lastChapterRead.toFloat()
            this.total_chapters = totalChapters
            this.score = score.toFloat()
            this.status = status.toInt()
            this.started_reading_date = startDate
            this.finished_reading_date = finishDate
            this.tracking_url = remoteUrl
        }
    }
}
