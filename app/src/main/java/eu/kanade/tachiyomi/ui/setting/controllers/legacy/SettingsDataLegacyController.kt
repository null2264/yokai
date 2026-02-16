package eu.kanade.tachiyomi.ui.setting.controllers.legacy

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceScreen
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.create.BackupCreatorJob
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import eu.kanade.tachiyomi.ui.setting.bindTo
import eu.kanade.tachiyomi.ui.setting.infoPreference
import eu.kanade.tachiyomi.ui.setting.intListPreference
import eu.kanade.tachiyomi.ui.setting.onChange
import eu.kanade.tachiyomi.ui.setting.onClick
import eu.kanade.tachiyomi.ui.setting.preference
import eu.kanade.tachiyomi.ui.setting.preferenceCategory
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.disableItems
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.tryTakePersistableUriPermission
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.util.view.setPositiveButton
import eu.kanade.tachiyomi.util.view.setTitle
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.injectLazy
import yokai.domain.backup.BackupPreferences
import yokai.domain.storage.StorageManager
import yokai.domain.storage.StoragePreferences
import yokai.i18n.MR
import yokai.presentation.settings.screen.BACKUPS_HELP_URL
import yokai.util.lang.getString
import android.R as AR
import eu.kanade.tachiyomi.ui.setting.summaryMRes as summaryRes
import eu.kanade.tachiyomi.ui.setting.titleMRes as titleRes

@Deprecated("Migrating to compose", replaceWith = ReplaceWith("SettingsDataController"))
class SettingsDataLegacyController : SettingsLegacyController() {

    /**
     * Flags containing information of what to backup.
     */
    private var backupFlags: BackupOptions = BackupOptions()
    internal val storagePreferences: StoragePreferences by injectLazy()
    internal val storageManager: StorageManager by injectLazy()
    internal val extensionManager: ExtensionManager by injectLazy()
    internal val backupPreferences: BackupPreferences by injectLazy()

    private val coverCache: CoverCache by injectLazy()
    private val chapterCache: ChapterCache by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = MR.strings.data_and_storage

        preference {
            key = "pref_storage_location"
            bindTo(storagePreferences.baseStorageDirectory())
            titleRes = MR.strings.storage_location

            onClick {
                try {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    startActivityForResult(intent, CODE_DATA_DIR)
                } catch (e: ActivityNotFoundException) {
                    activity?.toast(MR.strings.file_picker_error)
                }
            }

            storagePreferences.baseStorageDirectory().changes()
                .onEach { path ->
                    summary = UniFile.fromUri(context, path.toUri())?.let { dir ->
                        dir.filePath ?: context.getString(MR.strings.invalid_location, dir.uri)
                    } ?: context.getString(MR.strings.invalid_location_generic)
                }
                .launchIn(viewScope)
        }

        preference {
            key = "pref_create_backup"
            titleRes = MR.strings.create_backup
            summaryRes = MR.strings.can_be_used_to_restore

            onClick {
                if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                    context.toast(MR.strings.restore_miui_warning, Toast.LENGTH_LONG)
                }

                if (!BackupCreatorJob.isManualJobRunning(context)) {
                    showBackupCreateDialog()
                } else {
                    context.toast(MR.strings.backup_in_progress)
                }
            }
        }
        preference {
            key = "pref_restore_backup"
            titleRes = MR.strings.restore_backup
            summaryRes = MR.strings.restore_from_backup_file

            onClick {
                if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                    context.toast(MR.strings.restore_miui_warning, Toast.LENGTH_LONG)
                }

                if (!BackupRestoreJob.isRunning(context)) {
                    (activity as? AppCompatActivity)?.lifecycleScope?.launchIO {
                        extensionManager.getExtensionUpdates(true)
                    }
                    val intent = Intent(Intent.ACTION_GET_CONTENT)
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    storageManager.getBackupsDirectory()?.let {
                        intent.setDataAndType(it.uri, "*/*")
                    }
                    val title = activity?.getString(MR.strings.select_backup_file)
                    val chooser = Intent.createChooser(intent, title)
                    startActivityForResult(chooser, CODE_BACKUP_RESTORE)
                } else {
                    context.toast(MR.strings.restore_in_progress)
                }
            }
        }

        preferenceCategory {
            titleRes = MR.strings.automatic_backups

            intListPreference(activity) {
                bindTo(backupPreferences.backupInterval())
                titleRes = MR.strings.backup_frequency
                entriesRes = arrayOf(
                    MR.strings.manual,
                    MR.strings.every_6_hours,
                    MR.strings.every_12_hours,
                    MR.strings.daily,
                    MR.strings.every_2_days,
                    MR.strings.weekly,
                )
                entryValues = listOf(0, 6, 12, 24, 48, 168)

                onChange { newValue ->
                    val interval = newValue as Int
                    BackupCreatorJob.setupTask(context, interval)
                    true
                }
            }
            intListPreference(activity) {
                bindTo(backupPreferences.numberOfBackups())
                titleRes = MR.strings.max_auto_backups
                entries = (1..5).map(Int::toString)
                entryRange = 1..5

                visibleIf(backupPreferences.backupInterval()) { it > 0 }
            }
        }

        infoPreference(MR.strings.backup_info)

        preferenceCategory {
            titleRes = MR.strings.storage_usage

            preference {
                key = CLEAR_CACHE_KEY
                titleRes = MR.strings.clear_chapter_cache
                summary = context.getString(MR.strings.used_, chapterCache.readableSize)

                onClick { clearChapterCache() }
            }

            preference {
                key = "clear_cached_not_library"
                titleRes = MR.strings.clear_cached_covers_non_library
                summary = context.getString(
                    MR.strings.delete_all_covers__not_in_library_used_,
                    coverCache.getOnlineCoverCacheSize(),
                )

                onClick {
                    context.toast(MR.strings.starting_cleanup)
                    (activity as? AppCompatActivity)?.lifecycleScope?.launchIO {
                        coverCache.deleteAllCachedCovers()
                    }
                }
            }

            preference {
                key = "clean_cached_covers"
                titleRes = MR.strings.clean_up_cached_covers
                summary = context.getString(
                    MR.strings.delete_old_covers_in_library_used_,
                    coverCache.getChapterCacheSize(),
                )

                onClick {
                    context.toast(MR.strings.starting_cleanup)
                    (activity as? AppCompatActivity)?.lifecycleScope?.launchIO {
                        coverCache.deleteOldCovers()
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.settings_backup, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_backup_help -> activity?.openInBrowser(BACKUPS_HELP_URL)
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (data != null && resultCode == Activity.RESULT_OK) {
            val activity = activity ?: return
            val uri = data.data

            if (uri == null) {
                activity.toast(MR.strings.backup_restore_invalid_uri)
                return
            }

            when (requestCode) {
                CODE_DATA_DIR -> {
                    // Get UriPermission so it's possible to write files
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                    activity.tryTakePersistableUriPermission(uri, flags)
                    val file = UniFile.fromUri(activity, uri)!!
                    storagePreferences.baseStorageDirectory().set(file.uri.toString())
                }

                CODE_BACKUP_CREATE -> {
                    doBackup(backupFlags, uri, true)
                }

                CODE_BACKUP_RESTORE -> {
                    (activity as? MainActivity)?.showNotificationPermissionPrompt(true)
                    showBackupRestoreDialog(uri)
                }
            }
        }
    }

    private fun doBackup(options: BackupOptions, uri: Uri, requirePersist: Boolean = false) {
        val activity = activity ?: return

        val actualUri =
            if (requirePersist) {
                val intentFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                activity.tryTakePersistableUriPermission(uri, intentFlags)
                uri
            } else {
                UniFile.fromUri(activity, uri)?.createFile(Backup.getBackupFilename())?.uri
            } ?: return
        activity.toast(MR.strings.creating_backup)
        BackupCreatorJob.startNow(activity, actualUri, options)
    }

    private fun createBackup(options: BackupOptions, picker: Boolean = false) {
        backupFlags = options

        val dir = storageManager.getBackupsDirectory()
        if (dir == null) {
            activity?.toast(MR.strings.invalid_location_generic)
            return
        }

        if (!picker) {
            doBackup(backupFlags, dir.uri)
            return
        }

        try {
            // Use Android's built-in file creator
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/*")
                .putExtra(Intent.EXTRA_TITLE, Backup.getBackupFilename())

            startActivityForResult(intent, CODE_BACKUP_CREATE)
        } catch (e: ActivityNotFoundException) {
            activity?.toast(MR.strings.file_picker_error)
        }
    }

    private fun showBackupCreateDialog() {
        val activity = activity ?: return
        val options = BackupOptions.getOptions().map { activity.getString(it) }

        activity.materialAlertDialog()
            .setTitle(MR.strings.what_should_backup)
            .setMultiChoiceItems(
                options.toTypedArray(),
                BackupOptions().asBooleanArray(),
            ) { dialog, position, _ ->
                if (position == 0) {
                    val listView = (dialog as AlertDialog).listView
                    listView.setItemChecked(position, true)
                }
            }
            .setPositiveButton(MR.strings.create) { dialog, _ ->
                val listView = (dialog as AlertDialog).listView
                val booleanArrayList = arrayListOf(true)
                // TODO: Allow library_entries to be disabled
                for (i in 1 until listView.count) {  // skip 0, since 0 is always enabled
                    booleanArrayList.add(listView.isItemChecked(i))
                }
                createBackup(BackupOptions.fromBooleanArray(booleanArrayList.toBooleanArray()), picker = true)
            }
            .setNegativeButton(AR.string.cancel, null)
            .show().apply {
                disableItems(arrayOf(options.first()))
            }
    }

    private fun showBackupRestoreDialog(uri: Uri) {
        val activity = activity ?: return

        try {
            val results = BackupFileValidator().validate(activity, uri)

            var message = activity.getString(MR.strings.restore_content_full)
            if (results.missingSources.isNotEmpty()) {
                message += "\n\n${activity.getString(MR.strings.restore_missing_sources)}\n${
                results.missingSources.joinToString(
                    "\n",
                ) { "- $it" }
                }"
            }
            if (results.missingTrackers.isNotEmpty()) {
                message += "\n\n${activity.getString(MR.strings.restore_missing_trackers)}\n${
                results.missingTrackers.joinToString(
                    "\n",
                ) { "- $it" }
                }"
            }

            activity.materialAlertDialog()
                .setTitle(MR.strings.restore_backup)
                .setMessage(message)
                .setPositiveButton(MR.strings.restore) { _, _ ->
                    val context = applicationContext
                    if (context != null) {
                        activity.toast(MR.strings.restoring_backup)
                        BackupRestoreJob.start(context, uri)
                    }
                }.show()
        } catch (e: Exception) {
            activity.materialAlertDialog()
                .setTitle(MR.strings.invalid_backup_file)
                .setMessage(e.message)
                .setPositiveButton(AR.string.cancel, null)
                .show()
        }
    }

    private fun clearChapterCache() {
        if (activity == null) return
        viewScope.launchIO {
            try {
                val deletedFiles = chapterCache.clear()
                withUIContext {
                    activity?.toast(
                        activity?.getString(
                            MR.plurals.cache_cleared,
                            deletedFiles,
                            deletedFiles,
                        ),
                    )
                    findPreference(CLEAR_CACHE_KEY)?.summary =
                        activity?.getString(MR.strings.used_, chapterCache.readableSize)
                }
            } catch (_: Exception) {
                withUIContext {
                    activity?.toast(MR.strings.cache_delete_error)
                }
            }
        }
    }
}

private const val CLEAR_CACHE_KEY = "pref_clear_cache_key"

private const val CODE_DATA_DIR = 104
private const val CODE_BACKUP_CREATE = 504
private const val CODE_BACKUP_RESTORE = 505
