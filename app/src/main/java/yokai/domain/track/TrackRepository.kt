package yokai.domain.track

import eu.kanade.tachiyomi.data.database.models.Track

interface TrackRepository {
    suspend fun getAllByMangaId(mangaId: Long): List<Track>
}
