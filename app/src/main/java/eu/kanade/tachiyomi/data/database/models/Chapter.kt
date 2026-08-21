package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.decodeMemo
import java.io.Serializable
import yokai.domain.chapter.models.ChapterUpdate

interface Chapter : SChapter, Serializable {

    var id: Long?

    var manga_id: Long?

    var read: Boolean

    var bookmark: Boolean

    var last_page_read: Int

    var pages_left: Int

    var date_fetch: Long

    var source_order: Int

    val isRecognizedNumber: Boolean
        get() = chapter_number >= 0f

    fun toProgressUpdate() =
        ChapterUpdate(
            id = this.id!!,
            read = this.read,
            bookmark = this.bookmark,
            lastPageRead = this.last_page_read.toLong(),
            pagesLeft = this.pages_left.toLong(),
        )

    companion object {

        fun create(): Chapter = ChapterImpl().apply {
            chapter_number = -1f
        }

        fun List<Chapter>.copy(): List<Chapter> {
            return map {
                ChapterImpl().apply {
                    copyFrom(it)
                }
            }
        }

        fun mapper(
            id: Long,
            mangaId: Long,
            url: String,
            name: String,
            scanlator: String?,
            read: Boolean,
            bookmark: Boolean,
            lastPageRead: Long,
            pagesLeft: Long,
            chapterNumber: Double,
            sourceOrder: Long,
            dateFetch: Long,
            dateUpload: Long,
            memo: String,
        ): Chapter = create().apply {
            this.id = id
            this.manga_id = mangaId
            this.url = url
            this.name = name
            this.scanlator = scanlator
            this.read = read
            this.bookmark = bookmark
            this.last_page_read = lastPageRead.toInt()
            this.pages_left = pagesLeft.toInt()
            this.chapter_number = chapterNumber.toFloat()
            this.source_order = sourceOrder.toInt()
            this.date_fetch = dateFetch
            this.date_upload = dateUpload
            this.memo = memo.decodeMemo()
        }
    }

    fun copyFrom(other: Chapter) {
        id = other.id
        manga_id = other.manga_id
        read = other.read
        bookmark = other.bookmark
        last_page_read = other.last_page_read
        pages_left = other.pages_left
        date_fetch = other.date_fetch
        source_order = other.source_order
        copyFrom(other as SChapter)
    }

    fun copy() = ChapterImpl().apply {
        copyFrom(this@Chapter)
    }
}
