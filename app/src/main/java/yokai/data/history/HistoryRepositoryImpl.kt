package yokai.data.history

import eu.kanade.tachiyomi.data.database.models.History
import eu.kanade.tachiyomi.data.database.models.MangaChapterHistory
import eu.kanade.tachiyomi.util.system.toInt
import yokai.data.DatabaseHandler
import yokai.domain.history.HistoryRepository

class HistoryRepositoryImpl(private val handler: DatabaseHandler) : HistoryRepository {
    override suspend fun upsert(chapterId: Long, lastRead: Long, timeRead: Long) =
        handler.awaitOneOrNullExecutable(true) {
            historyQueries.upsert(chapterId, lastRead, timeRead)
            historyQueries.selectLastInsertedRowId()
        }

    override suspend fun bulkUpsert(histories: List<History>) =
        handler.await(true) {
            histories.forEach { history ->
                historyQueries.upsert(
                    history.chapter_id,
                    history.last_read,
                    history.time_read,
                )
            }
        }

    override suspend fun getByChapterUrl(chapterUrl: String): History? =
        handler.awaitOneOrNull { historyQueries.getByChapterUrl(chapterUrl, History::mapper) }

    override suspend fun getByMangaId(mangaId: Long): History? =
        handler.awaitOneOrNull { historyQueries.getByMangaId(mangaId, History::mapper) }

    override suspend fun getAllByMangaId(mangaId: Long): List<History> =
        handler.awaitList { historyQueries.getByMangaId(mangaId, History::mapper) }

    override suspend fun getRecentsUngrouped(
        filterScanlators: Boolean,
        search: String,
        limit: Long,
        offset: Long,
    ): List<MangaChapterHistory> =
        handler.awaitList { historyQueries.getRecentsUngrouped(search, filterScanlators.toInt().toLong(), limit, offset, MangaChapterHistory::mapper) }

    override suspend fun getRecentsBySeries(
        filterScanlators: Boolean,
        search: String,
        limit: Long,
        offset: Long,
    ): List<MangaChapterHistory> =
        handler.awaitList { historyQueries.getRecentsBySeries(search, filterScanlators.toInt().toLong(), limit, offset, MangaChapterHistory::mapper) }

    override suspend fun getRecentsAll(
        includeRead: Boolean,
        filterScanlators: Boolean,
        search: String,
        limit: Long,
        offset: Long
    ): List<MangaChapterHistory> =
        handler.awaitList { historyQueries.getRecentsAll(includeRead.toInt().toLong(), search, filterScanlators.toInt().toLong(), limit, offset, MangaChapterHistory::mapper) }
}
