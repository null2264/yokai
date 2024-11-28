package yokai.domain.history

import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.MangaChapterHistory

interface HistoryRepository {
    suspend fun upsert(chapterId: Long, lastRead: Long, timeRead: Long): Long?
    suspend fun bulkUpsert(histories: List<History>)
    suspend fun getByChapterUrl(chapterUrl: String): History?
    suspend fun getByMangaId(mangaId: Long): History?
    suspend fun getAllByMangaId(mangaId: Long): List<History>

    suspend fun getRecentsUngrouped(filterScanlators: Boolean, search: String = "", limit: Long = 25L, offset: Long = 0L): List<MangaChapterHistory>
    suspend fun getRecentsBySeries(filterScanlators: Boolean, search: String = "", limit: Long = 25L, offset: Long = 0L): List<MangaChapterHistory>
    suspend fun getRecentsAll(includeRead: Boolean, filterScanlators: Boolean, search: String = "", limit: Long = 25L, offset: Long = 0L): List<MangaChapterHistory>
}
