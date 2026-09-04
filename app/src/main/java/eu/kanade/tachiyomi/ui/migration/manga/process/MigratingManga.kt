package eu.kanade.tachiyomi.ui.migration.manga.process

import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.view.DeferredField
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import uy.kohesive.injekt.injectLazy
import yokai.domain.manga.interactor.GetManga

class MigratingManga(
    private val sourceManager: SourceManager,
    val mangaId: Long,
    parentContext: CoroutineContext,
) {
    private val getManga: GetManga by injectLazy()
    val searchResult = DeferredField<Long?>()

    // <MAX, PROGRESS>
    val progress = MutableStateFlow(1 to 0)

    // A proper CoroutineScope (not just a CoroutineContext) so it can be
    // cleanly cancelled and its children are tracked. SupervisorJob means
    // one failing source search doesn't cancel sibling searches.
    val migrationScope = CoroutineScope(parentContext + SupervisorJob() + Dispatchers.Default)

    // Keep a context reference for call sites that need CoroutineContext directly.
    val migrationJob: CoroutineContext get() = migrationScope.coroutineContext

    var migrationStatus: Int = MigrationStatus.RUNNUNG

    @Volatile
    private var manga: Manga? = null

    suspend fun manga(): Manga? {
        if (manga == null) manga = getManga.awaitById(mangaId)
        return manga
    }

    suspend fun mangaSource(): Source {
        return sourceManager.getOrStub(manga()?.source ?: -1)
    }

    /** Cancel all in-flight coroutines for this manga entry. */
    fun cancel() {
        migrationScope.cancel()
    }

    fun toModal(): MigrationProcessItem {
        // Create the model object.
        return MigrationProcessItem(this)
    }
}

class MigrationStatus {
    companion object {
        val RUNNUNG = 0
        val MANGA_FOUND = 1
        val MANGA_NOT_FOUND = 2
    }
}
