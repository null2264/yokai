package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.domain.manga.models.Manga

/**
 * Object containing manga, chapter and history
 *
 * @param manga object containing manga
 * @param chapter object containing chater
 * @param history object containing history
 */
data class MangaChapterHistory(val manga: Manga, val chapter: Chapter, val history: History, var extraChapters: List<ChapterHistory> = emptyList()) {

    companion object {
        fun createBlank() = MangaChapterHistory(MangaImpl(), ChapterImpl(), HistoryImpl())
    }
}

data class ChapterHistory(val chapter: Chapter, var history: History? = null) : Chapter by chapter
