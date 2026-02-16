package yokai.data.library.custom

import android.database.sqlite.SQLiteException
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.util.system.e
import kotlinx.coroutines.flow.Flow
import yokai.data.DatabaseHandler
import yokai.domain.library.custom.CustomMangaRepository
import yokai.domain.library.custom.exception.SaveCustomMangaException
import yokai.domain.library.custom.model.CustomMangaInfo

class CustomMangaRepositoryImpl(private val handler: DatabaseHandler) : CustomMangaRepository {
    override fun subscribeAll(): Flow<List<CustomMangaInfo>> =
        handler.subscribeToList { custom_manga_infoQueries.findAll(::mapCustomMangaInfo) }

    override suspend fun getAll(): List<CustomMangaInfo> =
        handler.awaitList { custom_manga_infoQueries.findAll(::mapCustomMangaInfo) }

    override suspend fun insertCustomManga(
        mangaId: Long,
        title: String?,
        author: String?,
        artist: String?,
        description: String?,
        genre: String?,
        status: Int?
    ) {
        try {
            handler.await { custom_manga_infoQueries.insert(mangaId, title, author, artist, description, genre, status?.toLong()) }
        } catch (exc: SQLiteException) {
            Logger.e(exc)
            throw SaveCustomMangaException(exc)
        }
    }

    override suspend fun insertBulkCustomManga(mangaList: List<CustomMangaInfo>) {
        try {
            handler.await(true) {
                for (customMangaInfo in mangaList) {
                    custom_manga_infoQueries.insert(
                        customMangaInfo.mangaId,
                        customMangaInfo.title,
                        customMangaInfo.author,
                        customMangaInfo.artist,
                        customMangaInfo.description,
                        customMangaInfo.genre,
                        customMangaInfo.status?.toLong(),
                    )
                }
            }
        } catch (exc: SQLiteException) {
            Logger.e(exc)
            throw SaveCustomMangaException(exc)
        }
    }

    override suspend fun deleteCustomManga(mangaId: Long) {
        handler.await { custom_manga_infoQueries.delete(mangaId) }
    }

    override suspend fun deleteBulkCustomManga(mangaIds: List<Long>) {
        handler.await(true) {
            for (mangaId in mangaIds) {
                custom_manga_infoQueries.delete(mangaId)
            }
        }
    }

    override suspend fun relinkCustomManga(oldId: Long, newId: Long) {
        handler.await { custom_manga_infoQueries.relink(newId, oldId) }
    }

    private fun mapCustomMangaInfo(
        mangaId: Long,
        title: String?,
        author: String?,
        artist: String?,
        description: String?,
        genre: String?,
        status: Long?
    ): CustomMangaInfo = CustomMangaInfo(mangaId, title, author, artist, description, genre, status?.toInt())
}
