package eu.kanade.tachiyomi.data.library

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.chapter.ChapterUtil.Companion.preferredChapterName
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.notification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notificationManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy
import yokai.domain.manga.models.cover
import yokai.i18n.MR
import yokai.util.lang.getString
import android.R as AR

class LibraryUpdateNotifier(private val context: Context) {

    private val preferences: PreferencesHelper by injectLazy()

    /**
     * Pending intent of action that cancels the library update
     */
    private val cancelIntent by lazy {
        NotificationReceiver.cancelLibraryUpdatePendingBroadcast(context)
    }

    /**
     * Bitmap of the app for notifications.
     */
    private val notificationBitmap by lazy {
        BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
    }

    /**
     * Cached progress notification to avoid creating a lot.
     */
    val progressNotificationBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_LIBRARY_PROGRESS) {
            setContentTitle(context.getString(MR.strings.updating_library))
            setSmallIcon(R.drawable.ic_refresh_24dp)
            setLargeIcon(notificationBitmap)
            setOngoing(true)
            setOnlyAlertOnce(true)
            setProgress(0, 0, true)
            color = ContextCompat.getColor(context, R.color.secondaryTachiyomi)
            addAction(R.drawable.ic_close_24dp, context.getString(AR.string.cancel), cancelIntent)
        }
    }

    /**
     * Shows the notification containing the currently updating manga and the progress.
     *
     * @param manga the manga that's being updated.
     * @param current the current progress.
     * @param total the total progress.
     */
    fun showProgressNotification(manga: Manga, current: Int, total: Int) {
        context.notificationManager.notify(
            Notifications.ID_LIBRARY_PROGRESS,
            progressNotificationBuilder
                .setContentTitle("${context.getString(MR.strings.updating_library)} (${current + 1}/$total)")
                .setContentText(if (preferences.hideNotificationContent().get()) null else manga.title)
                .setProgress(total, current, false)
                .build(),
        )
    }

    /**
     * Shows notification containing update entries that failed with action to open full log.
     *
     * @param errors List of entry titles that failed to update.
     * @param uri Uri for error log file containing all titles that failed.
     */
    fun showUpdateErrorNotification(errors: List<String>, uri: Uri) {
        if (errors.isEmpty()) {
            return
        }
        val pendingIntent = NotificationReceiver.openErrorOrSkippedLogPendingActivity(context, uri)
        context.notificationManager.notify(
            Notifications.ID_LIBRARY_ERROR,
            context.notificationBuilder(Notifications.CHANNEL_LIBRARY_ERROR) {
                setContentTitle(context.getString(MR.strings.notification_update_error, errors.size))
                setContentText(context.getString(MR.strings.tap_to_see_details))
                setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        errors.joinToString("\n") {
                            it.chop(TITLE_MAX_LEN)
                        },
                    ),
                )
                setContentIntent(pendingIntent)
                setSmallIcon(R.drawable.ic_yokai)
                addAction(
                    R.drawable.ic_file_open_24dp,
                    context.getString(MR.strings.open_log),
                    pendingIntent,
                )
            }
                .build(),
        )
    }

    /**
     * Shows notification containing update entries that were skipped with actions to open full log and learn more.
     *
     * @param skips List of entry titles that were skipped.
     * @param uri Uri for error log file containing all titles that were skipped.
     */
    fun showUpdateSkippedNotification(skips: List<String>, uri: Uri) {
        if (skips.isEmpty()) {
            return
        }

        context.notificationManager.notify(
            Notifications.ID_LIBRARY_SKIPPED,
            context.notificationBuilder(Notifications.CHANNEL_LIBRARY_SKIPPED) {
                setContentTitle(context.getString(MR.strings.notification_update_skipped, skips.size))
                setContentText(context.getString(MR.strings.tap_to_learn_more))
                setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        skips.joinToString("\n") {
                            it.chop(TITLE_MAX_LEN)
                        },
                    ),
                )
                setContentIntent(NotificationHandler.openUrl(context, HELP_SKIPPED_URL))
                setSmallIcon(R.drawable.ic_yokai)
                addAction(
                    R.drawable.ic_file_open_24dp,
                    context.getString(MR.strings.open_log),
                    NotificationReceiver.openErrorOrSkippedLogPendingActivity(context, uri),
                )
                addAction(
                    R.drawable.ic_help_outline_24dp,
                    context.getString(MR.strings.learn_why),
                    NotificationHandler.openUrl(context, HELP_SKIPPED_URL),
                )
            }
                .build(),
        )
    }

    /**
     * Shows the notification containing the result of the update done by the service.
     *
     * @param updates a list of manga with new updates.
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun showResultNotification(newUpdates: Map<LibraryManga, Array<Chapter>>) {
        // create a copy of the list since it will be cleared by the time it is used
        val updates = newUpdates.toMap()
        GlobalScope.launch {
            val notifications = ArrayList<Pair<Notification, Int>>()
            if (!preferences.hideNotificationContent().get()) {
                updates.forEach {
                    val manga = it.key
                    val chapters = it.value
                    val chapterNames = chapters.map { chapter ->
                        chapter.preferredChapterName(context, manga.manga, preferences)
                    }
                    notifications.add(
                        Pair(
                            context.notification(Notifications.CHANNEL_NEW_CHAPTERS) {
                                setSmallIcon(R.drawable.ic_yokai)
                                try {
                                    val request = ImageRequest.Builder(context).data(manga.manga.cover())
                                        .networkCachePolicy(CachePolicy.DISABLED)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .transformations(CircleCropTransformation())
                                        .size(width = ICON_SIZE, height = ICON_SIZE).build()

                                    context.imageLoader
                                        .execute(request).image?.asDrawable(context.resources)?.let { drawable ->
                                            setLargeIcon((drawable as? BitmapDrawable)?.bitmap)
                                        }
                                } catch (_: Exception) {
                                }
                                setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                                setContentTitle(manga.manga.title)
                                color = ContextCompat.getColor(context, R.color.secondaryTachiyomi)
                                val chaptersNames = if (chapterNames.size > MAX_CHAPTERS) {
                                    "${chapterNames.take(MAX_CHAPTERS - 1).joinToString(", ")}, " +
                                        context.getString(
                                            MR.plurals.notification_and_n_more,
                                            (chapterNames.size - (MAX_CHAPTERS - 1)),
                                            (chapterNames.size - (MAX_CHAPTERS - 1)),
                                        )
                                } else {
                                    chapterNames.joinToString(", ")
                                }
                                setContentText(chaptersNames)
                                setStyle(NotificationCompat.BigTextStyle().bigText(chaptersNames))
                                priority = NotificationCompat.PRIORITY_HIGH
                                setGroup(Notifications.GROUP_NEW_CHAPTERS)
                                setContentIntent(
                                    NotificationReceiver.openChapterPendingActivity(
                                        context,
                                        manga.manga,
                                        chapters.first(),
                                    ),
                                )
                                addAction(
                                    R.drawable.ic_eye_24dp,
                                    context.getString(MR.strings.mark_as_read),
                                    NotificationReceiver.markAsReadPendingBroadcast(
                                        context,
                                        manga.manga,
                                        chapters,
                                        Notifications.ID_NEW_CHAPTERS,
                                    ),
                                )
                                addAction(
                                    R.drawable.ic_book_24dp,
                                    context.getString(MR.strings.view_chapters),
                                    NotificationReceiver.openChapterPendingActivity(
                                        context,
                                        manga.manga,
                                        Notifications.ID_NEW_CHAPTERS,
                                    ),
                                )
                                setAutoCancel(true)
                            },
                            manga.manga.id.hashCode(),
                        ),
                    )
                }
            }

            NotificationManagerCompat.from(context).apply {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    return@apply
                }
                notify(
                    Notifications.ID_NEW_CHAPTERS,
                    context.notification(Notifications.CHANNEL_NEW_CHAPTERS) {
                        setSmallIcon(R.drawable.ic_yokai)
                        setLargeIcon(notificationBitmap)
                        setContentTitle(context.getString(MR.strings.new_chapters_found))
                        color = ContextCompat.getColor(context, R.color.secondaryTachiyomi)
                        if (updates.size > 1) {
                            setContentText(
                                context.getString(
                                    MR.plurals.for_n_titles,
                                    updates.size,
                                    updates.size,
                                ),
                            )
                            if (!preferences.hideNotificationContent().get()) {
                                setStyle(
                                    NotificationCompat.BigTextStyle()
                                        .bigText(
                                            updates.keys.joinToString("\n") {
                                                it.manga.title.chop(45)
                                            },
                                        ),
                                )
                            }
                        } else if (!preferences.hideNotificationContent().get()) {
                            setContentText(updates.keys.first().manga.title.chop(45))
                        }
                        priority = NotificationCompat.PRIORITY_HIGH
                        setGroup(Notifications.GROUP_NEW_CHAPTERS)
                        setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                        setGroupSummary(true)
                        setContentIntent(getNotificationIntent())
                        setAutoCancel(true)
                    },
                )

                notifications.forEach {
                    notify(it.second, it.first)
                }
            }
        }
    }

    fun showQueueSizeWarningNotification() {
        val notification = context.notificationBuilder(Notifications.CHANNEL_LIBRARY_PROGRESS) {
            setContentTitle(context.getString(MR.strings.warning))
            setSmallIcon(R.drawable.ic_warning_white_24dp)
            setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(MR.strings.notification_size_warning)))
            setContentIntent(NotificationHandler.openUrl(context, HELP_WARNING_URL))
            setTimeoutAfter(30000)
        }
            .build()

        context.notificationManager.notify(
            Notifications.ID_LIBRARY_SIZE_WARNING,
            notification,
        )
    }

    /**
     * Cancels the progress notification.
     */
    fun cancelProgressNotification() {
        context.notificationManager.cancel(Notifications.ID_LIBRARY_PROGRESS)
    }

    /**
     * Returns an intent to open the main activity.
     */
    private fun getNotificationIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = MainActivity.SHORTCUT_RECENTLY_UPDATED
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        private const val MAX_CHAPTERS = 5
        private const val TITLE_MAX_LEN = 45
        private const val ICON_SIZE = 192
        const val HELP_SKIPPED_URL = "https://tachiyomi.org/docs/faq/library#why-is-global-update-skipping-entries"
        const val HELP_WARNING_URL = "https://tachiyomi.org/docs/faq/library#why-am-i-warned-about-large-bulk-updates-and-downloads"
    }
}
