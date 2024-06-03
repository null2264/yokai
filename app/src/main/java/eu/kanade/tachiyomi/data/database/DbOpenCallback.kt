package eu.kanade.tachiyomi.data.database

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import tachiyomi.data.Database
import timber.log.Timber

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

    override fun onCreate(db: SupportSQLiteDatabase) {
        Timber.d("Creating new database...")
        super.onCreate(db)
    }

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < newVersion) {
            Timber.d("Upgrading database from $oldVersion to $newVersion")
            super.onUpgrade(db, oldVersion, newVersion)
        }
    }

    override fun onConfigure(db: SupportSQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }
}
