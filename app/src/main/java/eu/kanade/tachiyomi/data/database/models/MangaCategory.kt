package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.domain.manga.models.Manga

class MangaCategory {

    var id: Long? = null

    var manga_id: Long = 0

    var category_id: Long = 0

    companion object {

        fun create(manga: Manga, category: Category): MangaCategory {
            val mc = MangaCategory()
            mc.manga_id = manga.id!!
            mc.category_id = category.id!!
            return mc
        }
    }
}
