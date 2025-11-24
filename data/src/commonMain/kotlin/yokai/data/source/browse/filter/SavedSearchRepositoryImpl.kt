package yokai.data.source.browse.filter

import yokai.data.DatabaseHandler
import yokai.domain.source.browse.filter.SavedSearchRepository
import yokai.domain.source.browse.filter.models.RawSavedSearch

class SavedSearchRepositoryImpl(private val handler: DatabaseHandler) : SavedSearchRepository {
    override suspend fun findAll(): List<RawSavedSearch> = handler.awaitList {
        saved_searchQueries.findAll(RawSavedSearch::mapper)
    }

    override fun subscribeAllBySourceId(sourceId: Long) = handler.subscribeToList {
        saved_searchQueries.findBySourceId(sourceId, RawSavedSearch::mapper)
    }

    override suspend fun findAllBySourceId(sourceId: Long) = handler.awaitList {
        saved_searchQueries.findBySourceId(sourceId, RawSavedSearch::mapper)
    }

    override suspend fun findOneBySourceIdAndName(sourceId: Long, name: String): RawSavedSearch? = handler.awaitFirstOrNull {
        saved_searchQueries.findBySourceIdAndName(sourceId, name, RawSavedSearch::mapper)
    }

    override suspend fun findById(id: Long): RawSavedSearch? = handler.awaitFirstOrNull {
        saved_searchQueries.findById(id, RawSavedSearch::mapper)
    }

    override suspend fun deleteById(id: Long) {
        handler.await {
            saved_searchQueries.deleteById(id)
        }
    }

    override suspend fun insert(sourceId: Long, name: String, query: String?, filtersJson: String?) =
        handler.awaitOneOrNullExecutable(inTransaction = true) {
            saved_searchQueries.insert(sourceId, name, query, filtersJson)
            saved_searchQueries.selectLastInsertedRowId()
        }
}
