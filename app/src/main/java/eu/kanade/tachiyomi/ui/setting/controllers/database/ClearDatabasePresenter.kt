package eu.kanade.tachiyomi.ui.setting.controllers.database

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.withUIContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.data.DatabaseHandler

class ClearDatabasePresenter : BaseCoroutinePresenter<ClearDatabaseController>() {

    private val db = Injekt.get<DatabaseHelper>()

    private val handler = Injekt.get<DatabaseHandler>()

    private val sourceManager = Injekt.get<SourceManager>()

    var sortBy = SortSources.ALPHA
        private set

    var hasStubSources = false

    enum class SortSources {
        ALPHA,
        MOST_ENTRIES,
    }

    override fun onCreate() {
        super.onCreate()
        presenterScope.launchUI {
            getDatabaseSources()
        }
    }

    fun clearDatabaseForSourceIds(sources: List<Long>, keepReadManga: Boolean) {
        presenterScope.launchIO {
            if (keepReadManga) {
                db.deleteMangasNotInLibraryAndNotReadBySourceIds(sources).executeAsBlocking()
            } else {
                db.deleteMangasNotInLibraryBySourceIds(sources).executeAsBlocking()
            }
            handler.await { historyQueries.deleteAllUnread() }
            getDatabaseSources()
        }
    }

    fun reorder(sortBy: SortSources) {
        this.sortBy = sortBy
        presenterScope.launchUI {
            getDatabaseSources()
        }
    }

    private suspend fun getDatabaseSources() = withUIContext {
        hasStubSources = false
        val sources = db.getSourceIdsWithNonLibraryManga().executeAsBlocking()
            .map {
                val sourceObj = sourceManager.getOrStub(it.source)
                hasStubSources = sourceObj is SourceManager.StubSource || hasStubSources
                ClearDatabaseSourceItem(sourceObj, it.count)
            }
            .sortedWith(
                compareBy(
                    {
                        when (sortBy) {
                            SortSources.ALPHA -> it.source.name
                            SortSources.MOST_ENTRIES -> Int.MAX_VALUE - it.mangaCount
                        }
                    },
                    { it.source.name },
                ),
            )
        view?.setItems(sources)
    }
}
