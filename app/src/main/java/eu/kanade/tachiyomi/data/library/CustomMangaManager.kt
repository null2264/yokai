package eu.kanade.tachiyomi.data.library

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.database.models.MangaImpl
import eu.kanade.tachiyomi.domain.manga.models.Manga
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import nl.adaptivity.xmlutil.core.AndroidXmlReader
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue
import uy.kohesive.injekt.injectLazy
import yokai.core.metadata.COMIC_INFO_EDITS_FILE
import yokai.core.metadata.ComicInfo
import yokai.core.metadata.ComicInfoPublishingStatus
import yokai.core.metadata.copyFromComicInfo
import yokai.domain.library.custom.interactor.CreateCustomManga
import yokai.domain.library.custom.interactor.DeleteCustomManga
import yokai.domain.library.custom.interactor.GetCustomManga
import yokai.domain.library.custom.interactor.RelinkCustomManga
import yokai.domain.library.custom.model.CustomMangaInfo
import yokai.domain.library.custom.model.CustomMangaInfo.Companion.getMangaInfo

class CustomMangaManager(val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val xml: XML by injectLazy()

    private val externalDir = UniFile.fromFile(context.getExternalFilesDir(null))

    private var customMangaMap = mutableMapOf<Long, Manga>()

    private val createCustomManga: CreateCustomManga by injectLazy()
    private val deleteCustomManga: DeleteCustomManga by injectLazy()
    private val getCustomManga: GetCustomManga by injectLazy()
    private val relinkCustomManga: RelinkCustomManga by injectLazy()

    init {
        scope.launch {
            getCustomManga.subscribeAll().collectLatest {
                customMangaMap = it.associate { info ->
                    val id = info.mangaId
                    id to info.toManga()
                }.toMutableMap()
            }
            fetchCustomData()
        }
    }

    companion object {
        const val EDIT_JSON_FILE = "edits.json"
    }

    fun getManga(manga: Manga): Manga? = customMangaMap[manga.id]

    private suspend fun fetchCustomData() {
        val comicInfoEdits = externalDir?.findFile(COMIC_INFO_EDITS_FILE)
        val editJson = externalDir?.findFile(EDIT_JSON_FILE)

        // TODO: Remove after awhile
        if (comicInfoEdits != null && comicInfoEdits.exists() && comicInfoEdits.isFile) {
            fetchFromComicInfo(comicInfoEdits)
            return
        }

        // TODO: Remove after awhile
        if (editJson != null && editJson.exists() && editJson.isFile) {
            fetchFromLegacyJson(editJson)
            return
        }
    }

    private suspend fun fetchFromComicInfo(comicInfoFile: UniFile) {
        val comicInfoEdits =
            try {
                AndroidXmlReader(comicInfoFile.openInputStream(), StandardCharsets.UTF_8.name()).use {
                    xml.decodeFromReader<ComicList>(it)
                }
            } catch (e: NullPointerException) {
                // Couldn't load it somehow
                null
            } ?: return

        if (comicInfoEdits.comics == null) return

        comicInfoEdits.comics.mapNotNull { obj ->
            val id = obj.id ?: return@mapNotNull null
            customMangaMap[id] = mangaFromComicInfoObject(id, obj.value)
        }

        saveCustomInfo { comicInfoFile.delete() }
    }

    private suspend fun fetchFromLegacyJson(jsonFile: UniFile) {
        val json = try {
            Json.decodeFromStream<MangaList>(jsonFile.openInputStream())
        } catch (e: Exception) {
            null
        } ?: return

        val mangasJson = json.mangas ?: return
        mangasJson.mapNotNull { mangaObject ->
            val id = mangaObject.id ?: return@mapNotNull null
            customMangaMap[id] = mangaObject.toManga()
        }

        saveCustomInfo { jsonFile.delete() }
    }

    private val CustomMangaInfo.shouldDelete
        get() = (
            title == null &&
            author == null &&
            artist == null &&
            description == null &&
            genre == null &&
            (status ?: -1) == -1
        )

    suspend fun updateMangaInfo(oldId: Long?, newId: Long?, manga: CustomMangaInfo) {
        if (oldId == null || newId == null) return
        if (manga.shouldDelete) {
            deleteCustomInfo(manga.mangaId)
        } else {
            relinkCustomManga.await(oldId, newId)
        }
    }

    suspend fun saveMangaInfo(manga: CustomMangaInfo) {
        val mangaId = manga.mangaId
        if (manga.shouldDelete) {
            deleteCustomInfo(mangaId)
        } else {
            addCustomInfo(manga)
        }
    }

    private suspend fun deleteCustomInfo(mangaId: Long, onComplete: () -> Unit = {}) {
        deleteCustomManga.await(mangaId)
        onComplete()
    }

    private suspend fun addCustomInfo(manga: CustomMangaInfo, onComplete: () -> Unit = {}) {
        createCustomManga.await(manga)
        onComplete()
    }

    private suspend fun saveCustomInfo(onComplete: () -> Unit = {}) {
        val edits = customMangaMap.values.map { it.getMangaInfo() }
        if (edits.isNotEmpty()) {
            createCustomManga.bulk(edits)
            onComplete()
        }
    }

    @Serializable
    data class MangaList(
        val mangas: List<MangaJson>? = null,
    )

    @Serializable
    data class MangaJson(
        var id: Long? = null,
        val title: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val description: String? = null,
        val genre: Array<String>? = null,
        val status: Int? = null,
    ) {

        fun toManga() = MangaImpl(id = this.id).apply {
            title = this@MangaJson.title ?: ""
            author = this@MangaJson.author
            artist = this@MangaJson.artist
            description = this@MangaJson.description
            genre = this@MangaJson.genre?.joinToString(", ")
            status = this@MangaJson.status ?: -1
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as MangaJson
            if (id != other.id) return false
            return true
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }
    }

    @Serializable
    @XmlSerialName("ComicListYokai", "http://www.w3.org/2001/XMLSchema", "yk")
    data class ComicList(
        val comics: List<ComicInfoYokai>? = null,
    ) {
        @Serializable
        @XmlSerialName("ComicInfoYokai", "http://www.w3.org/2001/XMLSchema", "yk")
        data class ComicInfoYokai(
            @XmlValue(true) val value: ComicInfo,
            var id: Long? = null,
        ) {
            companion object {
                fun create(
                    id: Long? = null,
                    title: String? = null,
                    author: String? = null,
                    artist: String? = null,
                    description: String? = null,
                    genre: Array<String>? = null,
                    status: Int? = null,
                ): ComicInfoYokai {
                    return create(
                        id = id,
                        title = title,
                        author = author,
                        artist = artist,
                        description = description,
                        genre = genre?.joinToString(", ").orEmpty(),
                        status = status,
                    )
                }

                fun create(
                    id: Long? = null,
                    title: String? = null,
                    author: String? = null,
                    artist: String? = null,
                    description: String? = null,
                    genre: String? = null,
                    status: Int? = null,
                ): ComicInfoYokai {
                    return ComicInfoYokai(
                        id = id,
                        value = ComicInfo(
                            title = null,
                            series = title?.let { ComicInfo.Series(it) },
                            number = null,
                            writer = author?.let { ComicInfo.Writer(it) },
                            penciller = artist?.let { ComicInfo.Penciller(it) },
                            inker = null,
                            colorist = null,
                            letterer = null,
                            coverArtist = null,
                            translator = null,
                            summary = description?.let { ComicInfo.Summary(it) },
                            genre = genre?.let { ComicInfo.Genre(it) },
                            tags = null,
                            web = null,
                            publishingStatus = status.takeUnless { it == 0 }?.let {
                                ComicInfo.PublishingStatusTachiyomi(
                                    ComicInfoPublishingStatus.toComicInfoValue(it.toLong())
                                )
                            },
                            categories = null,
                            source = null,
                            language = null,
                        )
                    )
                }
            }
        }
    }

    private fun mangaFromComicInfoObject(id: Long, comicInfo: ComicInfo) =
        MangaImpl(id = id).apply { this.copyFromComicInfo(comicInfo) }
}
