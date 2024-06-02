package eu.kanade.tachiyomi.data.database

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import eu.kanade.tachiyomi.data.database.tables.CategoryTable
import eu.kanade.tachiyomi.data.database.tables.ChapterTable
import eu.kanade.tachiyomi.data.database.tables.HistoryTable
import eu.kanade.tachiyomi.data.database.tables.MangaTable
import eu.kanade.tachiyomi.data.database.tables.TrackTable
import tachiyomi.data.Database

class DbOpenCallback : AndroidSqliteDriver.Callback(Database.Schema) {

    companion object {
        /**
         * Name of the database file.
         */
        const val DATABASE_NAME = "tachiyomi.db"
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        setPragma(db, "foreign_keys = ON")
        setPragma(db, "journal_mode = WAL")
        setPragma(db, "synchronous = NORMAL")
    }

    private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
        val cursor = db.query("PRAGMA $pragma")
        cursor.moveToFirst()
        cursor.close()
    }

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 4) {
            db.execSQL(ChapterTable.bookmarkUpdateQuery)
        }
        if (oldVersion < 5) {
            db.execSQL(ChapterTable.addScanlator)
        }
        if (oldVersion < 6) {
            db.execSQL(TrackTable.addTrackingUrl)
        }
        if (oldVersion < 7) {
            db.execSQL(TrackTable.addLibraryId)
        }
        if (oldVersion < 8) {
            db.execSQL("DROP INDEX IF EXISTS mangas_favorite_index")
            db.execSQL(MangaTable.createLibraryIndexQuery)
            db.execSQL(ChapterTable.createUnreadChaptersIndexQuery)
        }
        if (oldVersion < 9) {
            db.execSQL(MangaTable.addHideTitle)
        }
        if (oldVersion < 10) {
            db.execSQL(CategoryTable.addMangaOrder)
        }
        if (oldVersion < 11) {
            db.execSQL(ChapterTable.pagesLeftQuery)
        }
        if (oldVersion < 12) {
            db.execSQL(MangaTable.addDateAddedCol)
        }
        if (oldVersion < 13) {
            db.execSQL(TrackTable.addStartDate)
            db.execSQL(TrackTable.addFinishDate)
        }
        if (oldVersion < 14) {
            db.execSQL(MangaTable.addFilteredScanlators)
        }
        if (oldVersion < 15) {
            db.execSQL(TrackTable.renameTableToTemp)
            db.execSQL(TrackTable.createTableQuery)
            db.execSQL(TrackTable.insertFromTempTable)
            db.execSQL(TrackTable.dropTempTable)
        }
        if (oldVersion < 16) {
            db.execSQL(MangaTable.addUpdateStrategy)
        }
        if (oldVersion < 17) {
            db.execSQL(TrackTable.updateMangaUpdatesScore)
        }
    }

    override fun onConfigure(db: SupportSQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }
}
