package eu.kanade.tachiyomi.data.database.tables

object MangaTable {

    const val TABLE = "mangas"

    const val COL_ID = "_id"

    const val COL_SOURCE = "source"

    const val COL_URL = "url"

    const val COL_ARTIST = "artist"

    const val COL_AUTHOR = "author"

    const val COL_DESCRIPTION = "description"

    const val COL_GENRE = "genre"

    const val COL_TITLE = "title"

    const val COL_STATUS = "status"

    const val COL_THUMBNAIL_URL = "thumbnail_url"

    const val COL_FAVORITE = "favorite"

    const val COL_LAST_UPDATE = "last_update"

    const val COL_INITIALIZED = "initialized"

    const val COL_VIEWER = "viewer"

    const val COL_CHAPTER_FLAGS = "chapter_flags"

    const val COL_UNREAD = "unread"

    const val COL_HAS_READ = "has_read"

    const val COL_BOOKMARK_COUNT = "bookmark_count"

    const val COL_CATEGORY = "category"

    const val COL_HIDE_TITLE = "hideTitle"

    const val COL_DATE_ADDED = "date_added"

    const val COL_FILTERED_SCANLATORS = "filtered_scanlators"

    const val COL_UPDATE_STRATEGY = "update_strategy"

    val createLibraryIndexQuery: String
        get() = "CREATE INDEX library_${COL_FAVORITE}_index ON $TABLE($COL_FAVORITE) " +
            "WHERE $COL_FAVORITE = 1"

    val addHideTitle: String
        get() = "ALTER TABLE $TABLE ADD COLUMN $COL_HIDE_TITLE INTEGER DEFAULT 0"

    val addDateAddedCol: String
        get() = "ALTER TABLE $TABLE ADD COLUMN $COL_DATE_ADDED LONG DEFAULT 0"

    val addFilteredScanlators: String
        get() = "ALTER TABLE $TABLE ADD COLUMN $COL_FILTERED_SCANLATORS TEXT"

    val addUpdateStrategy: String
        get() = "ALTER TABLE $TABLE ADD COLUMN $COL_UPDATE_STRATEGY INTEGER NOT NULL DEFAULT 0"
}
