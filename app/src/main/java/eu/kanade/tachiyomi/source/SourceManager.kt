package eu.kanade.tachiyomi.source

import android.content.Context
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.DelegatedHttpSource
import eu.kanade.tachiyomi.source.online.HttpSource
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import yokai.i18n.MR
import yokai.util.lang.getString

class SourceManager(
    private val context: Context,
    private val extensionManager: ExtensionManager,
) {

    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    private val sourcesMapFlow = MutableStateFlow(ConcurrentHashMap<Long, Source>())

    private val stubSourcesMap = ConcurrentHashMap<Long, StubSource>()

    val catalogueSources: Flow<List<CatalogueSource>> = sourcesMapFlow.map { it.values.filterIsInstance<CatalogueSource>() }
    val onlineSources: Flow<List<HttpSource>> = catalogueSources.map { it.filterIsInstance<HttpSource>() }

    // FIXME: Delegated source, unused at the moment, J2K only delegate deep links
    private val delegatedSources = emptyList<DelegatedSource>().associateBy { it.sourceId }

    init {
        scope.launch {
            extensionManager.installedExtensionsFlow
                .collectLatest { extensions ->
                    val mutableMap = ConcurrentHashMap<Long, Source>(mapOf(LocalSource.ID to LocalSource(context)))
                    extensions.forEach { extension ->
                        extension.sources.forEach {
                            mutableMap[it.id] = it
                            //delegatedSources[it.id]?.delegatedHttpSource?.delegate = it as? HttpSource
                            //registerStubSource(it)
                        }
                    }
                    sourcesMapFlow.value = mutableMap
                }
        }

//        scope.launch {
//            sourceRepository.subscribeAll()
//                .collectLatest { sources ->
//                    val mutableMap = stubSourcesMap.toMutableMap()
//                    sources.forEach {
//                        mutableMap[it.id] = StubSource(it)
//                    }
//                }
//        }
    }

    fun get(sourceKey: Long): Source? {
        return sourcesMapFlow.value[sourceKey]
    }

    fun getOrStub(sourceKey: Long): Source {
        return sourcesMapFlow.value[sourceKey] ?: stubSourcesMap.getOrPut(sourceKey) {
            runBlocking { StubSource(sourceKey) }
        }
    }

    fun isDelegatedSource(source: Source): Boolean {
        return delegatedSources.values.count { it.sourceId == source.id } > 0
    }

    fun getDelegatedSource(urlName: String): DelegatedHttpSource? {
        return delegatedSources.values.find { it.urlName == urlName }?.delegatedHttpSource
    }

    fun getOnlineSources() = sourcesMapFlow.value.values.filterIsInstance<HttpSource>()

    fun getCatalogueSources() = sourcesMapFlow.value.values.filterIsInstance<CatalogueSource>()

    @Suppress("OverridingDeprecatedMember")
    inner class StubSource(override val id: Long) : Source {

        override val name: String
            get() = extensionManager.getStubSource(id)?.name ?: id.toString()

        override val supportsLatest: Boolean
            get() = false

        override suspend fun getPopularManga(page: Int): MangasPage =
            throw getSourceNotInstalledException()

        override suspend fun getLatestUpdates(page: Int): MangasPage =
            throw getSourceNotInstalledException()

        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
            throw getSourceNotInstalledException()

        override suspend fun getMangaDetails(manga: SManga): SManga =
            throw getSourceNotInstalledException()

        override suspend fun getChapterList(manga: SManga): List<SChapter> =
            throw getSourceNotInstalledException()

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate =
            throw getSourceNotInstalledException()

        override suspend fun getPageList(chapter: SChapter): List<Page> =
            throw getSourceNotInstalledException()

        override fun toString(): String {
            return name
        }

        private fun getSourceNotInstalledException(): Exception {
            return SourceNotFoundException(
                context.getString(
                    MR.strings.source_not_installed_,
                    extensionManager.getStubSource(id)?.name ?: id.toString(),
                ),
                id,
            )
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as StubSource
            return id == other.id
        }
    }

    private data class DelegatedSource(
        val urlName: String,
        val sourceId: Long,
        val delegatedHttpSource: DelegatedHttpSource,
    )
}

class SourceNotFoundException(message: String, val id: Long) : Exception(message)
