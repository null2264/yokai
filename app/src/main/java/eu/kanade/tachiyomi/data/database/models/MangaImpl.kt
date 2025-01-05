package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import uy.kohesive.injekt.injectLazy

open class MangaImpl(
    override var id: Long? = null,
    override var source: Long = -1,
    override var url: String = "",
) : Manga {

    private val customMangaManager: CustomMangaManager by injectLazy()

    override var title: String
        get() = if (favorite) {
            val customTitle = customMangaManager.getManga(this)?.title
            if (customTitle.isNullOrBlank()) ogTitle else customTitle
        } else {
            ogTitle
        }
        set(value) {
            ogTitle = value
        }

    override var author: String?
        get() = if (favorite) customMangaManager.getManga(this)?.author ?: ogAuthor else ogAuthor
        set(value) { ogAuthor = value }

    override var artist: String?
        get() = if (favorite) customMangaManager.getManga(this)?.artist ?: ogArtist else ogArtist
        set(value) { ogArtist = value }

    override var description: String?
        get() = if (favorite) customMangaManager.getManga(this)?.description ?: ogDesc else ogDesc
        set(value) { ogDesc = value }

    override var genre: String?
        get() = if (favorite) customMangaManager.getManga(this)?.genre ?: ogGenre else ogGenre
        set(value) { ogGenre = value }

    override var status: Int
        get() = if (favorite) {
            customMangaManager.getManga(this)?.status.takeIf { it != -1 }
                ?: ogStatus
        } else {
            ogStatus
        }
        set(value) { ogStatus = value }

    override var thumbnail_url: String? = null

    override var favorite: Boolean = false

    override var last_update: Long = 0

    override var initialized: Boolean = false

    override var viewer_flags: Int = -1

    override var chapter_flags: Int = 0

    override var hide_title: Boolean = false

    override var date_added: Long = 0

    override var update_strategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE

    // TODO: It's probably fine to set this to non-null string in the future
    override var filtered_scanlators: String? = ""

    override lateinit var ogTitle: String
    override var ogAuthor: String? = null
    override var ogArtist: String? = null
    override var ogDesc: String? = null
    override var ogGenre: String? = null
    override var ogStatus: Int = 0

    override var cover_last_modified: Long = 0L

    override fun copyFrom(other: SManga) {
        if (other is MangaImpl && other::ogTitle.isInitialized &&
            other.title.isNotBlank() && other.ogTitle != ogTitle
        ) {
            val oldTitle = ogTitle
            title = other.ogTitle
            val db: DownloadManager by injectLazy()
            val provider = DownloadProvider(db.context)
            provider.renameMangaFolder(oldTitle, ogTitle, source)
        }
        super.copyFrom(other)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val manga = other as Manga

        return url == manga.url && source == manga.source
    }

    override fun hashCode(): Int {
        return if (url.isNotBlank()) {
            url.hashCode()
        } else {
            (id ?: 0L).hashCode()
        }
    }
}
