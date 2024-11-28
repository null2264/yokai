package yokai.data.track

import eu.kanade.tachiyomi.data.database.models.Track
import yokai.data.DatabaseHandler
import yokai.domain.track.TrackRepository

class TrackRepositoryImpl(private val handler: DatabaseHandler) : TrackRepository {
    override suspend fun getAllByMangaId(mangaId: Long): List<Track> =
        handler.awaitList { manga_syncQueries.getAllByMangaId(mangaId, Track::mapper) }
}
