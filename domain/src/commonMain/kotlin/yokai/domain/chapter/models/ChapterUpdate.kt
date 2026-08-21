package yokai.domain.chapter.models

data class ChapterUpdate(
    val id: Long,
    val mangaId: Long? = null,
    val url: String? = null,
    val name: String? = null,
    val scanlator: String? = null,
    val read: Boolean? = null,
    val bookmark: Boolean? = null,
    val lastPageRead: Long? = null,
    val pagesLeft: Long? = null,
    val chapterNumber: Double? = null,
    val sourceOrder: Long? = null,
    val dateFetch: Long? = null,
    val dateUpload: Long? = null,
    val memo: String? = null,
)
