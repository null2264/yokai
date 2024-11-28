package yokai.domain.storage

import android.content.Context
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.buildLogWritersToAdd
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.setToDefault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn

class StorageManager(
    private val context: Context,
    storagePreferences: StoragePreferences,
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private var baseDir: UniFile? = getBaseDir(storagePreferences.baseStorageDirectory().get())

    private val _changes: Channel<Unit> = Channel(Channel.UNLIMITED)
    val changes = _changes.receiveAsFlow()
        .shareIn(scope, SharingStarted.Lazily, 1)

    init {
        storagePreferences.baseStorageDirectory().changes()
            .drop(1)
            .distinctUntilChanged()
            .onEach { uri ->
                baseDir = getBaseDir(uri)
                baseDir?.let { parent ->
                    parent.createDirectory(BACKUPS_PATH)
                    parent.createDirectory(AUTOMATIC_BACKUPS_PATH)
                    parent.createDirectory(LOCAL_SOURCE_PATH)
                    parent.createDirectory(DOWNLOADS_PATH).also {
                        DiskUtil.createNoMediaFile(it, context)
                    }
                    parent.createDirectory(COVERS_PATH)
                    parent.createDirectory(PAGES_PATH)
                    parent.createDirectory(LOGS_PATH)?.also {
                        try {
                            Logger.setToDefault(buildLogWritersToAdd(it))
                        } catch (e: Exception) {
                            // Just in case something went horribly wrong
                            Logger.setToDefault(buildLogWritersToAdd(null))
                            Logger.e(e) { "Something went wrong while trying to setup log file" }
                        }
                    }
                }

                _changes.send(Unit)
            }
            .launchIn(scope)
    }

    private fun getBaseDir(uri: String): UniFile? {
        return UniFile.fromUri(context, uri.toUri())
            .takeIf { it?.exists() == true }
    }

    fun getBackupsDirectory(): UniFile? {
        return baseDir?.createDirectory(BACKUPS_PATH)
    }

    fun getAutomaticBackupsDirectory(): UniFile? {
        return baseDir?.createDirectory(AUTOMATIC_BACKUPS_PATH)
    }

    fun getDownloadsDirectory(): UniFile? {
        return baseDir?.createDirectory(DOWNLOADS_PATH)
    }

    fun getLocalSourceDirectory(): UniFile? {
        return baseDir?.createDirectory(LOCAL_SOURCE_PATH)
    }

    fun getCoversDirectory(): UniFile? {
        return baseDir?.createDirectory(COVERS_PATH)
    }

    fun getPagesDirectory(): UniFile? {
        return baseDir?.createDirectory(PAGES_PATH)
    }

    fun getLogsDirectory(): UniFile? {
        return baseDir?.createDirectory(LOGS_PATH)
    }

    companion object {
        private const val BACKUPS_PATH = "backup"
        private const val AUTOMATIC_BACKUPS_PATH = "autobackup"
        private const val DOWNLOADS_PATH = "downloads"
        const val LOCAL_SOURCE_PATH = "local"
        private const val COVERS_PATH = "covers"
        private const val PAGES_PATH = "pages"
        private const val LOGS_PATH = "logs"
    }
}
