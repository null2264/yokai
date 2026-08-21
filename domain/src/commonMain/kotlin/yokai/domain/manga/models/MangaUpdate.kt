package yokai.domain.manga.models

import eu.kanade.tachiyomi.source.model.UpdateStrategy

data class MangaUpdate(
    val id: Long,
    val url: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genres: List<String>? = null,
    val status: Int? = null,
    val thumbnailUrl: String? = null,
    val updateStrategy: UpdateStrategy? = null,
    val initialized: Boolean? = null,
    var source: Long? = null,
    var favorite: Boolean? = null,
    var lastUpdate: Long? = null,
    var dateAdded: Long? = null,
    var viewerFlags: Int? = null,
    var chapterFlags: Int? = null,
    var hideTitle: Boolean? = null,
    var filteredScanlators: String? = null,
    var coverLastModified: Long? = null,
    var memo: String? = null,
)
