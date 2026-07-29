package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.safeMemo
import kotlin.jvm.Transient
import kotlinx.serialization.json.JsonObject

fun SChapter.toChapter(): ChapterImpl {
    return ChapterImpl().apply {
        name = this@toChapter.name
        url = this@toChapter.url
        date_upload = this@toChapter.date_upload
        chapter_number = this@toChapter.chapter_number
        scanlator = this@toChapter.scanlator
        memo = this@toChapter.safeMemo()
    }
}


class ChapterImpl : Chapter {

    override var id: Long? = null

    override var manga_id: Long? = null

    override lateinit var url: String

    override lateinit var name: String

    override var scanlator: String? = null

    override var read: Boolean = false

    override var bookmark: Boolean = false

    override var last_page_read: Int = 0

    override var pages_left: Int = 0

    override var date_fetch: Long = 0

    override var date_upload: Long = 0

    override var chapter_number: Float = 0f

    override var source_order: Int = 0

    @Transient
    override var memo: JsonObject = JsonObject(emptyMap())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val chapter = other as Chapter
        return url == chapter.url
    }

    override fun hashCode(): Int {
        return url.hashCode()
    }
}
