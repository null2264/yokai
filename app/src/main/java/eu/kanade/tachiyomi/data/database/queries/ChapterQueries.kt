package eu.kanade.tachiyomi.data.database.queries

import com.pushtorefresh.storio.sqlite.queries.Query
import eu.kanade.tachiyomi.data.database.DbProvider
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.tables.ChapterTable
import eu.kanade.tachiyomi.domain.manga.models.Manga

interface ChapterQueries : DbProvider {

    fun getChapters(manga: Manga) = getChapters(manga.id)

    fun getChapters(mangaId: Long?) = db.get()
        .listOfObjects(Chapter::class.java)
        .withQuery(
            Query.builder()
                .table(ChapterTable.TABLE)
                .where("${ChapterTable.COL_MANGA_ID} = ?")
                .whereArgs(mangaId)
                .build(),
        )
        .prepare()

    // FIXME: Migrate to SQLDelight, on halt: in StorIO transaction
    fun insertChapters(chapters: List<Chapter>) = db.put().objects(chapters).prepare()
}
