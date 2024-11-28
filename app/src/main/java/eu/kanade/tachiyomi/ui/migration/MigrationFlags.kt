package eu.kanade.tachiyomi.ui.migration

import android.content.Context
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.util.system.toInt
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.injectLazy
import yokai.domain.track.interactor.GetTrack
import yokai.i18n.MR
import yokai.util.lang.getString

object MigrationFlags {

    private const val CHAPTERS = 0b0001
    private const val CATEGORIES = 0b0010
    private const val TRACK = 0b0100
    private const val CUSTOM_MANGA_INFO = 0b1000

    private val coverCache: CoverCache by injectLazy()
    private val db: DatabaseHelper by injectLazy()
    private val customMangaManager: CustomMangaManager by injectLazy()
    private val getTrack: GetTrack by injectLazy()

    val titles get() = arrayOf(MR.strings.chapters, MR.strings.categories, MR.strings.tracking, MR.strings.custom_manga_info)
    val flags get() = arrayOf(CHAPTERS, CATEGORIES, TRACK, CUSTOM_MANGA_INFO)

    fun hasChapters(value: Int): Boolean {
        return value and CHAPTERS != 0
    }

    fun hasCategories(value: Int): Boolean {
        return value and CATEGORIES != 0
    }

    fun hasTracks(value: Int): Boolean {
        return value and TRACK != 0
    }

    fun hasCustomMangaInfo(value: Int): Boolean {
        return value and CUSTOM_MANGA_INFO != 0
    }

    fun getEnabledFlags(value: Int): List<Boolean> {
        return flags.map { flag -> value and flag != 0 }
    }

    fun getFlagsFromPositions(positions: Array<Boolean>, manga: Manga?): Int {
        val flags = flags(manga)
        return positions.foldIndexed(0) { index, accumulated, enabled ->
            accumulated or (if (enabled) flags[index] else 0)
        }
    }

    fun getFlagsFromPositions(positions: Array<Boolean>): Int {
        return positions.foldIndexed(0) { index, accumulated, enabled ->
            accumulated or (enabled.toInt() shl index)
        }
    }

    fun flags(manga: Manga?): Array<Int> {
        val flags = arrayOf(CHAPTERS, CATEGORIES).toMutableList()
        if (manga != null) {
            if (runBlocking { getTrack.awaitAllByMangaId(manga.id) }.isNotEmpty()) {
                flags.add(TRACK)
            }

            if (coverCache.getCustomCoverFile(manga).exists() || customMangaManager.getManga(manga) != null) {
                flags.add(CUSTOM_MANGA_INFO)
            }
        }
        return flags.toTypedArray()
    }

    private fun titleForFlag(flag: Int): StringResource {
        return when (flag) {
            CHAPTERS -> MR.strings.chapters
            CATEGORIES -> MR.strings.categories
            TRACK -> MR.strings.tracking
            CUSTOM_MANGA_INFO -> MR.strings.custom_manga_info
            else -> throw IllegalStateException("Invalid flag")
        }
    }

    fun titles(context: Context, manga: Manga?): Array<String> {
        return flags(manga).map { context.getString(titleForFlag(it)) }.toTypedArray()
    }
}
