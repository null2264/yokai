package yokai.domain.history.interactor

import eu.kanade.tachiyomi.data.database.models.History
import yokai.domain.history.HistoryRepository

class UpsertHistory(
    private val historyRepository: HistoryRepository
) {
    suspend fun await(chapterId: Long, lastRead: Long, timeRead: Long) =
        historyRepository.upsert(chapterId, lastRead, timeRead)

    suspend fun await(history: History) =
        historyRepository.upsert(history.chapter_id, history.last_read, history.time_read)

    suspend fun awaitBulk(histories: List<History>) =
        historyRepository.bulkUpsert(histories)
}
