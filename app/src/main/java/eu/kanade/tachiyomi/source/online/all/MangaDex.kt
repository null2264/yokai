package eu.kanade.tachiyomi.source.online.all

import android.net.Uri
import eu.kanade.tachiyomi.data.database.models.toChapter
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.DelegatedHttpSource
import eu.kanade.tachiyomi.source.online.HttpSource
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.manga.interactor.GetManga
import yokai.i18n.MR
import yokai.util.lang.getString

class MangaDex(delegate: HttpSource) : DelegatedHttpSource(delegate) {

    private val getManga: GetManga = Injekt.get()

    override val lang: String = "all"

    override val domainName: String = "mangadex"

    val sourceManager: SourceManager by injectLazy()

    override fun canOpenUrl(uri: Uri): Boolean {
        return uri.pathSegments?.lastOrNull() != "comments"
    }

    override fun chapterUrl(uri: Uri): String? {
        val chapterNumber = uri.pathSegments.getOrNull(1) ?: return null
        return "/chapter/$chapterNumber"
    }

    override fun pageNumber(uri: Uri): Int? {
        return uri.pathSegments.getOrNull(2)?.toIntOrNull()
    }

    override suspend fun fetchMangaFromChapterUrl(uri: Uri): Triple<SChapter, SManga, List<SChapter>>? {
        val url = chapterUrl(uri) ?: return null
        val request =
            GET("https:///api.mangadex.org/v2$url", delegate!!.headers, CacheControl.FORCE_NETWORK)
        val response = network.client.newCall(request).await()
        if (response.code != 200) throw Exception("HTTP error ${response.code}")
        val body = response.body.string()
        if (body.isEmpty()) {
            throw Exception("Null Response")
        }

        val jsonObject = Json.decodeFromString<MangaDexChapterData>(body)
        val dataObject = jsonObject.data ?: throw Exception("Chapter not found")
        val mangaId = dataObject.mangaId ?: throw Exception("No manga associated with chapter")
        val mangaUrl = "/manga/$mangaId/"
        return withContext(Dispatchers.IO) {
            val cachedManga = getManga.awaitByUrlAndSource(mangaUrl, delegate.id)
            val update = getMangaUpdateByUrl(
                mangaUrl,
                fetchDetails = cachedManga == null,
                fetchChapters = true,
            )
            val manga = cachedManga ?: update.manga
            val chapters = update.chapters
            val context = Injekt.get<PreferencesHelper>().context
            val trueChapter = chapters.find { it.url == "/api$url" }?.toChapter() ?: error(
                context.getString(MR.strings.chapter_not_found),
            )
            Triple(trueChapter, manga, chapters)
        }
    }

    @Serializable
    private data class MangaDexChapterData(
        val data: MangaDexChapterInfo? = null,
    )

    @Serializable
    private data class MangaDexChapterInfo(
        val mangaId: Int? = null,
        val language: String? = null,
    )

    private fun getRealLangCode(langCode: String): String {
        return when (langCode.lowercase(Locale.getDefault())) {
            "gb" -> "en"
            "vn" -> "vi"
            "mx" -> "es-419"
            "br" -> "pt-BR"
            "ph" -> "fil"
            "sa" -> "ar"
            "bd" -> "bn"
            "mm" -> "my"
            "cz" -> "cs"
            "dk" -> "da"
            "gr" -> "el"
            "jp" -> "ja"
            "kr" -> "ko"
            "my" -> "ms"
            "ir" -> "fa"
            "rs" -> "sh"
            "ua" -> "uk"
            "cn" -> "zh-Hans"
            "hk" -> "zh-Hant"
            else -> langCode
        }
    }
}
