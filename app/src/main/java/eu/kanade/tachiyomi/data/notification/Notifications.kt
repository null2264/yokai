package eu.kanade.tachiyomi.data.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import eu.kanade.tachiyomi.R
import yokai.i18n.MR
import yokai.util.lang.getString
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.util.system.notificationManager

/**
 * Class to manage the basic information of all the notifications used in the app.
 */
object Notifications {

    /**
     * Common notification channel and ids used anywhere.
     */
    const val CHANNEL_COMMON = "common_channel"
    const val ID_UPDATER = 1
    const val ID_DOWNLOAD_IMAGE = 2
    const val ID_INSTALL = 3
    const val CHANNEL_INSTALLING = "installing_channel"
    const val CHANNEL_UPDATED = "updated_channel"
    const val ID_INSTALLED = -6
    const val ID_UPDATER_FAILED = -7
    const val GROUP_APP_UPDATES = "eu.kanade.tachiyomi.APP_UPDATES"

    /**
     * Notification channel and ids used by the downloader.
     */
    const val CHANNEL_DOWNLOADER = "downloader_channel"
    const val ID_DOWNLOAD_CHAPTER = -201
    const val ID_DOWNLOAD_CHAPTER_ERROR = -202
    const val ID_DOWNLOAD_SIZE_WARNING = -203
    const val ID_DOWNLOAD_PAUSED = -204

    /**
     * Notification channel and ids used by the library updater.
     */
    private const val GROUP_LIBRARY = "group_library"
    const val CHANNEL_NEW_CHAPTERS = "new_chapters_channel"
    const val ID_NEW_CHAPTERS = -301
    const val GROUP_NEW_CHAPTERS = "eu.kanade.tachiyomi.NEW_CHAPTERS"

    /**
     * Notification channel and ids used by the library updater.
     */
    const val CHANNEL_LIBRARY_PROGRESS = "library_progress_channel"
    const val ID_LIBRARY_PROGRESS = -101
    const val CHANNEL_LIBRARY_ERROR = "library_errors_channel"
    const val ID_LIBRARY_ERROR = -102
    const val ID_LIBRARY_SIZE_WARNING = -103
    const val CHANNEL_LIBRARY_SKIPPED = "library_skipped_channel"
    const val ID_LIBRARY_SKIPPED = -104

    /**
     * Notification channel and ids used by the library updater.
     */
    private const val GROUP_EXTENSION_UPDATES = "group_extension_updates"
    const val CHANNEL_UPDATES_TO_EXTS = "updates_ext_channel"
    const val ID_UPDATES_TO_EXTS = -401

    const val CHANNEL_EXT_PROGRESS = "ext_update_progress_channel"
    const val ID_EXTENSION_PROGRESS = -402
    const val CHANNEL_EXT_UPDATED = "ext_updated_channel"
    const val ID_UPDATED_EXTS = -403

    private const val GROUP_BACKUP_RESTORE = "group_backup_restore"
    const val CHANNEL_BACKUP_RESTORE_PROGRESS = "backup_restore_progress_channel"
    const val ID_RESTORE_PROGRESS = -501
    const val ID_RESTORE_COMPLETE = -502
    const val CHANNEL_BACKUP_RESTORE_COMPLETE = "backup_restore_complete_channel"
    const val ID_BACKUP_PROGRESS = -502
    const val ID_BACKUP_COMPLETE = -503

    /**
     * Notification channel used for crash log file sharing.
     */
    const val CHANNEL_CRASH_LOGS = "crash_logs_channel"
    const val ID_CRASH_LOGS = -601

    /**
     * Notification channel used for Incognito Mode
     */
    const val CHANNEL_INCOGNITO_MODE = "incognito_mode_channel"
    const val ID_INCOGNITO_MODE = -701

    private val deprecatedChannels = listOf(
        "backup_restore_channel",
        "library_channel",
    )

    /**
     * Creates the notification channels introduced in Android Oreo.
     *
     * @param context The application context.
     */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        // Delete old notification channels
        deprecatedChannels.forEach(context.notificationManager::deleteNotificationChannel)

        listOf(
            NotificationChannelGroup(
                GROUP_BACKUP_RESTORE,
                context.getString(MR.strings.backup_and_restore),
            ),
            NotificationChannelGroup(
                GROUP_EXTENSION_UPDATES,
                context.getString(MR.strings.extension_updates),
            ),
            NotificationChannelGroup(GROUP_APP_UPDATES, context.getString(MR.strings.app_updates)),
            NotificationChannelGroup(GROUP_LIBRARY, context.getString(MR.strings.library)),
        ).forEach(context.notificationManager::createNotificationChannelGroup)

        val channels = listOf(
            NotificationChannel(
                CHANNEL_COMMON,
                context.getString(MR.strings.common),
                NotificationManager.IMPORTANCE_LOW,
            ),
            NotificationChannel(
                CHANNEL_LIBRARY_PROGRESS,
                context.getString(MR.strings.updating_library),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                group = GROUP_LIBRARY
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_LIBRARY_ERROR,
                context.getString(MR.strings.channel_errors),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                group = GROUP_LIBRARY
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_LIBRARY_SKIPPED,
                context.getString(MR.strings.channel_skipped),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                group = GROUP_LIBRARY
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_DOWNLOADER,
                context.getString(MR.strings.downloads),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_UPDATES_TO_EXTS,
                context.getString(MR.strings.extension_updates_pending),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                group = GROUP_EXTENSION_UPDATES
            },
            NotificationChannel(
                CHANNEL_NEW_CHAPTERS,
                context.getString(MR.strings.new_chapters),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                group = GROUP_LIBRARY
            },
            NotificationChannel(
                CHANNEL_BACKUP_RESTORE_PROGRESS,
                context.getString(MR.strings.progress),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                group = GROUP_BACKUP_RESTORE
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_BACKUP_RESTORE_COMPLETE,
                context.getString(MR.strings.complete),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                group = GROUP_BACKUP_RESTORE
                setShowBadge(false)
                setSound(null, null)
            },
            NotificationChannel(
                CHANNEL_CRASH_LOGS,
                context.getString(MR.strings.crash_logs),
                NotificationManager.IMPORTANCE_HIGH,
            ),
            NotificationChannel(
                CHANNEL_INCOGNITO_MODE,
                context.getString(MR.strings.incognito_mode),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            },
            NotificationChannel(
                CHANNEL_EXT_PROGRESS,
                context.getString(MR.strings.updating_extensions),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                group = GROUP_EXTENSION_UPDATES
                setShowBadge(false)
                setSound(null, null)
            },
            NotificationChannel(
                CHANNEL_EXT_UPDATED,
                context.getString(MR.strings.extensions_updated),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                group = GROUP_EXTENSION_UPDATES
            },
            NotificationChannel(
                CHANNEL_INSTALLING,
                context.getString(MR.strings.installing),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                group = GROUP_APP_UPDATES
            },
            NotificationChannel(
                CHANNEL_UPDATED,
                context.getString(MR.strings.update_completed),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                setShowBadge(false)
                group = GROUP_APP_UPDATES
            },
        )
        context.notificationManager.createNotificationChannels(channels)
    }

    fun isNotificationChannelEnabled(context: Context, channelId: String?): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!channelId.isNullOrBlank()) {
                val manager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = manager.getNotificationChannel(channelId)
                return channel.importance != NotificationManager.IMPORTANCE_NONE
            }
            false
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
}
