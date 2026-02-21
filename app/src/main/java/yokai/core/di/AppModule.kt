package yokai.core.di

import android.app.Application
import androidx.core.content.ContextCompat
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_CREATE
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READWRITE
import app.cash.sqldelight.db.SqlDriver
import co.touchlab.kermit.Logger
import com.chuckerteam.chucker.api.ChuckerCollector
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import com.eygraber.sqldelight.androidx.driver.File
import com.eygraber.sqldelight.androidx.driver.SqliteJournalMode
import com.eygraber.sqldelight.androidx.driver.SqliteSync
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.core.storage.AndroidStorageFolderProvider
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.library.CustomMangaManager
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.chapter.ChapterFilter
import eu.kanade.tachiyomi.util.manga.MangaShortcutManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import org.koin.dsl.module
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.data.AndroidDatabaseHandler
import yokai.data.Database
import yokai.data.DatabaseHandler
import yokai.domain.SplashState
import yokai.domain.storage.StorageManager

fun appModule(app: Application) = module {
    single { app }

    single<SqlDriver> {
        AndroidxSqliteDriver(
            createConnection = { name ->
                BundledSQLiteDriver().open(name, SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE)
            },
            databaseType = AndroidxSqliteDatabaseType.File(app, "tachiyomi.db"),
            configuration = AndroidxSqliteConfiguration().apply {
                isForeignKeyConstraintsEnabled = true
                journalMode = SqliteJournalMode.WAL
                sync = SqliteSync.Normal
            },
            schema = Database.Schema,
            onCreate = {
                Logger.d { "Creating new database..." }
            },
            onUpdate = { oldVersion, newVersion ->
                if (oldVersion < newVersion) {
                    Logger.d { "Upgrading database from $oldVersion to $newVersion" }
                }
            },
        )
    }

    single {
        Database(
            driver = get(),
        )
    }
    single<DatabaseHandler> { AndroidDatabaseHandler(get(), get()) }

    single { ChapterCache(app) }

    single { CoverCache(app) }

    single {
        NetworkHelper(
            app,
            get(),
        ) { builder ->
            if (BuildConfig.DEBUG) {
                builder.addInterceptor(
                    ChuckerInterceptor.Builder(app)
                        .collector(ChuckerCollector(app))
                        .maxContentLength(250000L)
                        .redactHeaders(emptySet())
                        .alwaysReadResponseBody(false)
                        .build(),
                )
            }
        }
    }

    single { JavaScriptEngine(app) }

    single { SourceManager(app, get()) }
    single { ExtensionManager(app) }

    single { DownloadProvider(app) }
    single { DownloadManager(app) }
    single { DownloadCache(app) }

    single { CustomMangaManager(app) }

    single { TrackManager(app) }

    single {
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
    single {
        XML {
            defaultPolicy {
                ignoreUnknownChildren()
            }
            autoPolymorphic = true
            xmlDeclMode = XmlDeclMode.Charset
            indent = 2
            xmlVersion = XmlVersion.XML10
        }
    }
    single<ProtoBuf> {
        ProtoBuf
    }

    single { ChapterFilter() }

    single { MangaShortcutManager() }

    single { AndroidStorageFolderProvider(app) }
    single { StorageManager(app, get()) }

    single { SplashState() }
}

// REF: https://github.com/jobobby04/TachiyomiSY/blob/26cfb4811fef4059fb7e8e03361c141932fec6b5/app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt#L177C1-L192C2
fun initExpensiveComponents(app: Application) {
    // Asynchronously init expensive components for a faster cold start
    ContextCompat.getMainExecutor(app).execute {
        Injekt.get<NetworkHelper>()

        Injekt.get<SourceManager>()

        Injekt.get<Database>()

        Injekt.get<DownloadManager>()

        Injekt.get<CustomMangaManager>()
    }
}
