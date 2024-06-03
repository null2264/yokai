package dev.yokai.data.manga

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.updateStrategyAdapter

val mangaMapper: (Long, Long, String, String?, String?, String?, String?, String, Int, String?, Boolean, Long, Boolean, Int, Int, Boolean, Long, String?, Int) -> Manga =
    { id, source, url, artist, author, description, genre, title, status, thumbnailUrl, favorite, lastUpdate, initialized, viewerFlags, chapterFlags, hideTitle, dateAdded, filteredScanlators, updateStrategy ->
        Manga.create(source).apply {
            this.id = id
            this.url = url
            this.artist = artist
            this.author = author
            this.description = description
            this.genre = genre
            this.title = title
            this.status = status
            this.thumbnail_url = thumbnailUrl
            this.favorite = favorite
            this.last_update = lastUpdate
            this.initialized = initialized
            this.viewer_flags = viewerFlags
            this.chapter_flags = chapterFlags
            this.hide_title = hideTitle
            this.date_added = dateAdded
            this.filtered_scanlators = filteredScanlators
            this.update_strategy = updateStrategy.let(updateStrategyAdapter::decode)
        }
    }
