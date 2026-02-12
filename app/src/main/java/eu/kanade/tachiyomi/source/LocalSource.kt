package eu.kanade.tachiyomi.source

import android.app.Application
import android.content.Context
import androidx.core.net.toFile
import co.touchlab.kermit.Logger
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.extension
import eu.kanade.tachiyomi.util.system.nameWithoutExtension
import eu.kanade.tachiyomi.util.system.withIOContext
import eu.kanade.tachiyomi.util.system.writeText
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import nl.adaptivity.xmlutil.core.AndroidXmlReader
import nl.adaptivity.xmlutil.serialization.XML
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.core.archive.util.archiveReader
import yokai.core.archive.util.epubReader
import yokai.core.metadata.COMIC_INFO_FILE
import yokai.core.metadata.ComicInfo
import yokai.core.metadata.copyFromComicInfo
import yokai.core.metadata.toComicInfo
import yokai.domain.chapter.services.ChapterRecognition
import yokai.domain.source.SourcePreferences
import yokai.domain.storage.StorageManager
import yokai.i18n.MR
import yokai.util.fillMetadata
import yokai.util.lang.getString

class LocalSource(private val context: Context) : CatalogueSource, UnmeteredSource {
    companion object {
        const val ID = 0L
        const val HELP_URL = "https://mihon.app/docs/guides/local-source/"

        private const val COVER_NAME = "cover.jpg"
        private val LATEST_THRESHOLD = TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS)
        private val langMap = hashMapOf<String, String>()

        private fun getMangaDirs(context: Context, url: String): Sequence<UniFile> {
            return getBaseDirectories(context).asSequence()
                .mapNotNull { it?.findFile(url) }
        }

        fun decodeComicInfo(stream: InputStream, xml: XML = Injekt.get()): ComicInfo {
            return AndroidXmlReader(stream, StandardCharsets.UTF_8.name()).use { reader ->
                xml.decodeFromReader<ComicInfo>(reader)
            }
        }

        fun getMangaLang(manga: SManga, context: Context = Injekt.get<Application>()): String {
            return langMap.getOrPut(manga.url) {
                // Modified: check all directories for language info
                val localDetails = getMangaDirs(context, manga.url)
                    .mapNotNull { dir ->
                         dir.listFiles().orEmpty()
                            .filter { !it.isDirectory }
                            .firstOrNull { it.name == COMIC_INFO_FILE }
                    }
                    .firstOrNull()

                val lang = if (localDetails != null) {
                    try {
                        decodeComicInfo(localDetails.openInputStream()).language?.value
                    } catch (e: Exception) {
                        Logger.e(e) { "Unable to retrieve manga language" }
                        null
                    }
                } else {
                    null
                }

                return lang ?: "other"
            }
        }

        fun invalidateCover(manga: SManga, context: Context = Injekt.get<Application>()) {
            val cover = getMangaDirs(context, manga.url)
                .mapNotNull { getCoverFile(it) }
                .firstOrNull() ?: return

            manga.thumbnail_url = cover.uri.toString()
        }

        fun updateCover(manga: SManga, input: InputStream, context: Context = Injekt.get<Application>()): UniFile? {
            val dirs = getMangaDirs(context, manga.url).toList()
            if (dirs.isEmpty()) {
                input.close()
                return null
            }

            // Prefer directory that already has a cover, else use the first one (primary)
            val dir = dirs.find { getCoverFile(it) != null } ?: dirs.first()

            val cover = getCoverFile(dir) ?: dir.createFile(COVER_NAME)!!
            // It might not exist if using the external SD card
            cover.parentFile?.parentFile?.createDirectory(cover.parentFile?.name)
            input.use {
                cover.openOutputStream().use {
                    input.copyTo(it)
                }
            }
            manga.thumbnail_url = cover.uri.toString()
            return cover
        }

        private fun updateMetadata(chapter: SChapter, manga: SManga, stream: InputStream) {
            val comicInfo = decodeComicInfo(stream)

            comicInfo.title?.let { chapter.name = it.value }
            chapter.chapter_number =
                comicInfo.number?.value?.toFloatOrNull()
                    ?: ChapterRecognition.parseChapterNumber(chapter.name, manga.title, chapter.chapter_number)
            comicInfo.translator?.let { chapter.scanlator = it.value }
        }

        /**
         * Returns valid cover file inside [parent] directory.
         */
        private fun getCoverFile(parent: UniFile?): UniFile? {
            return parent?.listFiles()
                ?.filter { it.isFile && it.nameWithoutExtension.equals("cover", ignoreCase = true) }
                ?.firstOrNull { ImageUtil.isImage(it.name) { it.openInputStream() } }
        }

        private fun getBaseDirectory(): UniFile? {
            val storageManager: StorageManager = Injekt.get()
            return storageManager.getLocalSourceDirectory()
        }

        private fun getBaseDirectories(context: Context): List<UniFile?> {
            val sourcePreferences: SourcePreferences = Injekt.get()
            val base = listOf(getBaseDirectory())
            if (!sourcePreferences.externalLocalSource().get()) return base

            return base + DiskUtil.getExternalStorages(context, false).map {
                UniFile.fromFile(it)?.createDirectory(StorageManager.LOCAL_SOURCE_PATH)
            }
        }
    }

    private val json: Json by injectLazy()
    private val xml: XML by injectLazy()

    override val id = ID
    override val name = context.getString(MR.strings.local_source)
    override val lang = "other"
    override val supportsLatest = true

    override fun toString() = name

    override suspend fun getPopularManga(page: Int) = getSearchManga(page, "", popularFilters)

    override suspend fun getSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage = withIOContext {
        val time = if (filters === latestFilters) {
            System.currentTimeMillis() - LATEST_THRESHOLD
        } else {
            0L
        }

        var mangaDirs = getBaseDirectories(context).asSequence()
            .mapNotNull { it?.listFiles()?.toList() }
            .flatten()
            .filter { it.isDirectory && !it.name.orEmpty().startsWith('.') }
            .distinctBy { it.name }
            .filter {
                if (time == 0L && query.isBlank())
                    true
                else if (time == 0L)
                    it.name.orEmpty().contains(query, ignoreCase = true)
                else
                    it.lastModified() >= time
            }

        val state = ((if (filters.isEmpty()) popularFilters else filters)[0] as OrderBy).state
        when (state?.index) {
            0 -> {
                mangaDirs = if (state.ascending) {
                    mangaDirs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() })
                } else {
                    mangaDirs.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty()})
                }
            }
            1 -> {
                mangaDirs = if (state.ascending) {
                    mangaDirs.sortedBy(UniFile::lastModified)
                } else {
                    mangaDirs.sortedByDescending(UniFile::lastModified)
                }
            }
        }

        val mangas = mangaDirs.map { mangaDir ->
            async {
                SManga.create().apply {
                    title = mangaDir.name.orEmpty()
                    url = mangaDir.name.orEmpty()

                    // Try to find the cover
                    val cover = getCoverFile(mangaDir)
                    if (cover != null && cover.exists()) {
                        thumbnail_url = cover.uri.toString()
                    }
                }
            }
        }.toList().awaitAll()

        MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int) = getSearchManga(page, "", latestFilters)

    override suspend fun getMangaDetails(manga: SManga): SManga = withIOContext {
        // Making sure that we have the latest cover file path, in case user use different file format
        invalidateCover(manga, context)

        try {
            val mangaDirs = getMangaDirs(context, manga.url).toList()
            if (mangaDirs.isEmpty()) throw Exception("${manga.url} is not a valid directory")

            // Scan all dirs for metadata files
            val metadataFiles = mangaDirs.map { dir ->
                val files = dir.listFiles().orEmpty().filter { !it.isDirectory }
                val info = files.firstOrNull { it.name.orEmpty() == COMIC_INFO_FILE }
                val json = files.firstOrNull { it.extension.orEmpty().equals("json", true) }
                Pair(info, json)
            }

            // Priority: 1. ComicInfo, 2. Legacy JSON
            val comicInfoFile = metadataFiles.mapNotNull { it.first }.firstOrNull()
            val legacyJsonFile = metadataFiles.mapNotNull { it.second }.firstOrNull()

            if (comicInfoFile != null)
                return@withIOContext manga.copy().apply {
                    setMangaDetailsFromComicInfoFile(comicInfoFile.openInputStream(), this)
                }

            // TODO: Remove after awhile
            if (legacyJsonFile != null) {
                val rt = manga.copy().apply {
                    setMangaDetailsFromLegacyJsonFile(legacyJsonFile.openInputStream(), this)
                }

                // Try to find parent of legacy json to write the new info to
                val legacyParent = mangaDirs.firstOrNull { it.findFile(legacyJsonFile.name) != null }

                if (legacyParent != null) {
                    val comicInfo = rt.toComicInfo()
                    legacyParent.createFile(COMIC_INFO_FILE)
                        ?.writeText(xml.encodeToString(ComicInfo.serializer(), comicInfo)) { legacyJsonFile.delete() }
                }
                return@withIOContext rt
            }
        } catch (e: Exception) {
            Logger.e(e) { "Something went wrong while trying to load manga details" }
        }

        return@withIOContext manga
    }

    private fun setMangaDetailsFromComicInfoFile(stream: InputStream, manga: SManga) {
        val comicInfo = decodeComicInfo(stream, xml)

        comicInfo.language?.let { langMap[manga.url] = it.value }
        manga.copyFromComicInfo(comicInfo)
    }

    private fun setMangaDetailsFromLegacyJsonFile(stream: InputStream, manga: SManga) {
        val obj = json.decodeFromStream<MangaJson>(stream)

        obj.lang?.let { langMap[manga.url] = it }
        manga.apply {
            title = obj.title ?: manga.title
            author = obj.author ?: manga.author
            artist = obj.artist ?: manga.artist
            description = obj.description ?: manga.description
            genre = obj.genre?.joinToString(", ") ?: manga.genre
            status = obj.status ?: manga.status
        }
    }

    fun updateMangaInfo(manga: SManga, lang: String?) {
        val directory = getMangaDirs(context, manga.url).firstOrNull() ?: return
        if (!directory.exists()) return

        lang?.let { langMap[manga.url] = it }
        directory.createFile(COMIC_INFO_FILE)?.let { file ->
            file.writeText(xml.encodeToString(ComicInfo.serializer(), manga.toComicInfo(lang = lang)))
        }
    }

    @Serializable
    data class MangaJson(
        val title: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val description: String? = null,
        val genre: Array<String>? = null,
        val status: Int? = null,
        val lang: String? = null,
    ) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as MangaJson

            return title == other.title
        }

        override fun hashCode(): Int {
            return title.hashCode()
        }
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> = withIOContext {
        val dirs = getMangaDirs(context, manga.url).toList()

        // 1. Gather files from ALL directories
        // 2. Distinct by name to merge duplicates (prioritizing based on directory order in base dirs)
        val validFiles = dirs.asSequence()
            .flatMap { it.listFiles().orEmpty().asSequence() }
            .filter { it.isDirectory || isSupportedArchive(it.extension.orEmpty()) || it.extension.equals("epub", true) }
            .distinctBy { it.name }
            .toList()

        val chapters = validFiles
            .map { chapterFile ->
                SChapter.create().apply {
                    url = "${manga.url}/${chapterFile.name}"
                    name = if (chapterFile.isDirectory) {
                        chapterFile.name.orEmpty()
                    } else {
                        chapterFile.nameWithoutExtension.orEmpty()
                    }
                    date_upload = chapterFile.lastModified()

                    val success = updateMetadata(this, manga, chapterFile)
                    if (!success) {
                        chapter_number =
                            ChapterRecognition.parseChapterNumber(this.name, manga.title, this.chapter_number)
                    }
                }
            }
            .sortedWith { c1, c2 ->
                val c = c2.chapter_number.compareTo(c1.chapter_number)
                if (c == 0) c2.name.compareToCaseInsensitiveNaturalOrder(c1.name) else c
            }
            .toList()

        // Copy the cover from the first chapter found.
        if (manga.thumbnail_url.isNullOrBlank()) {
            chapters.lastOrNull()?.let { chapter ->
                try {
                    updateCover(chapter, manga)
                } catch (e: Exception) {
                    Logger.e(e) { "Unable to update cover" }
                }
            }
        }

        chapters
    }

    override suspend fun getPageList(chapter: SChapter) = throw Exception("Unused")

    private fun isSupportedArchive(extension: String?): Boolean {
        extension ?: return false
        return extension.lowercase() in SUPPORTED_ARCHIVE_TYPES
    }

    fun getFormat(chapter: SChapter): Format {
        val (mangaDirName, chapterName) = chapter.url.split('/', limit = 2)

        // Modified to look for the chapter file in any storage location
        val chapFile = getMangaDirs(context, mangaDirName)
            .mapNotNull { it.findFile(chapterName) }
            .firstOrNull()

        if (chapFile == null || !chapFile.exists())
            throw Exception(context.getString(MR.strings.chapter_not_found))

        return getFormat(chapFile)
    }

    private fun getFormat(file: UniFile) = with(file) {
        when {
            isDirectory -> Format.Directory(this)
            isSupportedArchive(extension) -> Format.Archive(this)
            extension.equals("epub", true) -> Format.Epub(this)
            else -> throw Exception(context.getString(MR.strings.local_invalid_format))
        }
    }

    private fun updateCover(chapter: SChapter, manga: SManga): UniFile? {
        return try {
            when (val format = getFormat(chapter)) {
                is Format.Directory -> {
                    val entry = format.file.listFiles()
                        ?.sortedWith { f1, f2 -> f1.name.orEmpty().compareToCaseInsensitiveNaturalOrder(f2.name.orEmpty()) }
                        ?.find { !it.isDirectory && ImageUtil.isImage(it.name) { FileInputStream(it.uri.toFile()) } }

                    entry?.let { updateCover(manga, it.openInputStream(), context) }
                }
                is Format.Archive -> {
                    format.file.archiveReader(context).use { reader ->
                        val entry = reader.useEntries { entries ->
                            entries
                                .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                                .find { it.isFile && ImageUtil.isImage(it.name) { reader.getInputStream(it.name)!! } }
                        }

                        entry?.let { updateCover(manga, reader.getInputStream(it.name)!!, context) }
                    }
                }
                is Format.Epub -> {
                    format.file.epubReader(context).use { epub ->
                        val entry = epub.getImagesFromPages().firstOrNull()

                        entry?.let { updateCover(manga, epub.getInputStream(it)!!, context) }
                    }
                }
            }
        } catch (e: Throwable) {
            Logger.e(e) { "Error updating cover for ${manga.title}" }
            null
        }
    }

    private fun updateMetadata(chapter: SChapter, manga: SManga, chapterFile: UniFile? = null): Boolean {
        return try {
            when (val format = if (chapterFile != null) getFormat(chapterFile) else getFormat(chapter)) {
                is Format.Directory -> {
                    val entry = format.file.findFile(COMIC_INFO_FILE) ?: return false

                    updateMetadata(chapter, manga, entry.openInputStream())
                    true
                }
                is Format.Epub -> {
                    format.file.epubReader(context).use { epub ->
                        epub.fillMetadata(chapter, manga)
                    }
                    true
                }
                is Format.Archive -> format.file.archiveReader(context).use { reader ->
                    reader.getInputStream(COMIC_INFO_FILE)?.use {
                        updateMetadata(chapter, manga, it)
                        true
                    } ?: false
                }
            }
        } catch (e: Throwable) {
            Logger.e(e) { "Error updating a metadata" }
            false
        }
    }

    override fun getFilterList() = popularFilters

    private val popularFilters = FilterList(OrderBy(context))
    private val latestFilters = FilterList(OrderBy(context).apply { state = Filter.Sort.Selection(1, false) })

    private class OrderBy(context: Context) : Filter.Sort(
        context.getString(MR.strings.order_by),
        arrayOf(context.getString(MR.strings.title), context.getString(MR.strings.date)),
        Selection(0, true),
    )

    sealed class Format {
        data class Directory(val file: UniFile) : Format()
        data class Archive(val file: UniFile) : Format()
        data class Epub(val file: UniFile) : Format()
    }
}

private val SUPPORTED_ARCHIVE_TYPES = listOf("zip", "cbz", "rar", "cbr", "7z", "cb7", "tar", "cbt")
