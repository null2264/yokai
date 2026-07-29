package eu.kanade.tachiyomi.source.online.all

import android.net.Uri
import eu.kanade.tachiyomi.data.database.models.toChapter
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.DelegatedHttpSource
import eu.kanade.tachiyomi.source.online.HttpSource
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.chapter.interactor.GetChapter
import yokai.domain.manga.interactor.GetManga
import yokai.i18n.MR
import yokai.util.lang.getString

class Cubari(delegate: HttpSource) :
    DelegatedHttpSource(delegate) {

    private val getManga: GetManga = Injekt.get()
    private val getChapter: GetChapter = Injekt.get()

    override val lang = "all"

    override val domainName: String = "cubari"
    override fun canOpenUrl(uri: Uri): Boolean = true

    override fun chapterUrl(uri: Uri): String? = null

    override fun pageNumber(uri: Uri): Int? = uri.pathSegments.getOrNull(4)?.toIntOrNull()

    override suspend fun fetchMangaFromChapterUrl(uri: Uri): Triple<SChapter, SManga, List<SChapter>>? {
        val cubariType = uri.pathSegments.getOrNull(1)?.lowercase(Locale.ROOT) ?: return null
        val cubariPath = uri.pathSegments.getOrNull(2) ?: return null
        val chapterNumber = uri.pathSegments.getOrNull(3)?.replace("-", ".")?.toFloatOrNull() ?: return null
        val mangaUrl = "/read/$cubariType/$cubariPath"
        return withContext(Dispatchers.IO) {
            val cachedManga = getManga.awaitByUrlAndSource(mangaUrl, delegate.id)
            if (cachedManga != null) {
                val chapters = getChapter.awaitAll(cachedManga, false)
                val chapter = findChapter(chapters, cubariType, chapterNumber)
                if (chapter != null) {
                    return@withContext Triple(chapter.toChapter(), cachedManga, chapters)
                }
            }
            val update = getMangaUpdateByUrl(
                mangaUrl,
                fetchDetails = cachedManga == null,
                fetchChapters = true,
            )
            val manga = cachedManga ?: update.manga
            val chapters = update.chapters
            val context = Injekt.get<PreferencesHelper>().context
            val trueChapter = findChapter(chapters, cubariType, chapterNumber)?.toChapter()
                ?: error(
                    context.getString(MR.strings.chapter_not_found),
                )
            Triple(trueChapter, manga, chapters)
        }
    }

    fun findChapter(chapters: List<SChapter>?, cubariType: String, chapterNumber: Float): SChapter? {
        return when (cubariType) {
            "imgur" -> chapters?.firstOrNull()
            else -> chapters?.find { it.chapter_number == chapterNumber }
        }
    }
}
