package yokai.presentation.settings.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import co.touchlab.kermit.Logger
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.core.storage.preference.collectAsState
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.create.BackupCreatorJob
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.util.compose.LocalDialogHostState
import eu.kanade.tachiyomi.util.compose.currentOrThrow
import eu.kanade.tachiyomi.util.relativeTimeSpanString
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.launchNonCancellableIO
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withUIContext
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.backup.BackupPreferences
import yokai.domain.storage.StorageManager
import yokai.domain.storage.StoragePreferences
import yokai.i18n.MR
import yokai.presentation.component.ToolTipButton
import yokai.presentation.component.preference.Preference
import yokai.presentation.component.preference.storageLocationText
import yokai.presentation.component.preference.widget.BasePreferenceWidget
import yokai.presentation.component.preference.widget.PrefsHorizontalPadding
import yokai.presentation.settings.ComposableSettings
import yokai.presentation.settings.screen.data.StorageInfo
import yokai.presentation.settings.screen.data.awaitCreateBackup
import yokai.presentation.settings.screen.data.awaitRestoreBackup
import yokai.presentation.settings.screen.data.storageLocationPicker
import yokai.util.lang.getString

object SettingsDataScreen : ComposableSettings {
    @Composable
    override fun getTitleRes(): StringResource = MR.strings.data_and_storage

    @Composable
    override fun RowScope.AppBarAction() {
        val uriHandler = LocalUriHandler.current

        ToolTipButton(
            toolTipLabel = stringResource(MR.strings.help),
            icon = Icons.AutoMirrored.Outlined.Help,
            buttonClicked = { uriHandler.openUri(BACKUPS_HELP_URL) },
        )
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val storagePreferences: StoragePreferences by injectLazy()
        val backupPreferences: BackupPreferences by injectLazy()

        return persistentListOf(
            getStorageLocationPreference(storagePreferences = storagePreferences),
            getBackupAndRestoreGroup(backupPreferences = backupPreferences),
            getDataGroup(),
        )
    }

    @Composable
    private fun getStorageLocationPreference(storagePreferences: StoragePreferences): Preference.PreferenceItem.TextPreference {
        val context = LocalContext.current
        val pickStoragePicker = storageLocationPicker(storagePreferences.baseStorageDirectory())

        return Preference.PreferenceItem.TextPreference(
            title = stringResource(MR.strings.storage_location),
            subtitle = storageLocationText(storagePreferences.baseStorageDirectory()),
            onClick = {
                try {
                    pickStoragePicker.launch(null)
                } catch (e: ActivityNotFoundException) {
                    context.toast(MR.strings.file_picker_error)
                }
            }
        )
    }

    @Composable
    private fun getBackupAndRestoreGroup(backupPreferences: BackupPreferences): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val alertDialog = LocalDialogHostState.currentOrThrow
        val extensionManager = remember { Injekt.get<ExtensionManager>() }
        val storageManager = remember { Injekt.get<StorageManager>() }

        val chooseBackup = rememberLauncherForActivityResult(
            object : ActivityResultContracts.GetContent() {
                override fun createIntent(context: Context, input: String): Intent {
                    val intent = super.createIntent(context, input)
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    return Intent.createChooser(intent, context.getString(MR.strings.select_backup_file))
                }
            },
        ) {
            if (it == null) return@rememberLauncherForActivityResult

            val results = try {
                Pair(BackupFileValidator().validate(context, it), null)
            } catch (e: Exception) {
                Pair(null, e)
            }

            scope.launch {
                alertDialog.awaitRestoreBackup(
                    context = context,
                    uri = it,
                    pair = results,
                )
            }
        }

        val backupInterval by backupPreferences.backupInterval().collectAsState()
        val lastAutoBackup by backupPreferences.lastAutoBackupTimestamp().collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.backup_and_restore),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MR.strings.backup_and_restore),
                ) {
                    BasePreferenceWidget(
                        subcomponent = {
                            MultiChoiceSegmentedButtonRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(intrinsicSize = IntrinsicSize.Min)
                                    .padding(horizontal = PrefsHorizontalPadding),
                            ) {
                                SegmentedButton(
                                    modifier = Modifier.fillMaxHeight(),
                                    checked = false,
                                    onCheckedChange = {
                                        if (!BackupRestoreJob.isRunning(context)) {
                                            if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                                                context.toast(MR.strings.restore_miui_warning, Toast.LENGTH_LONG)
                                            }

                                            val dir = storageManager.getBackupsDirectory()
                                            if (dir == null) {
                                                context.toast(MR.strings.invalid_location_generic)
                                                return@SegmentedButton
                                            }

                                            scope.launch {
                                                alertDialog.awaitCreateBackup(
                                                    context = context,
                                                    uri = dir.uri,
                                                )
                                            }
                                        } else {
                                            context.toast(MR.strings.backup_in_progress)
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                                ) {
                                    Text(stringResource(MR.strings.create_backup))
                                }
                                SegmentedButton(
                                    modifier = Modifier.fillMaxHeight(),
                                    checked = false,
                                    onCheckedChange = {
                                        if (!BackupRestoreJob.isRunning(context)) {
                                            if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                                                context.toast(MR.strings.restore_miui_warning, Toast.LENGTH_LONG)
                                            }

                                            scope.launch { extensionManager.getExtensionUpdates(true) }
                                            chooseBackup.launch("*/*")
                                        } else {
                                            context.toast(MR.strings.restore_in_progress)
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                                ) {
                                    Text(stringResource(MR.strings.restore_backup))
                                }
                            }
                        },
                    )
                },

                // Automatic backups
                Preference.PreferenceItem.ListPreference(
                    pref = backupPreferences.backupInterval(),
                    title = stringResource(MR.strings.backup_frequency),
                    entries = persistentMapOf(
                        0 to stringResource(MR.strings.manual),
                        6 to stringResource(MR.strings.every_6_hours),
                        12 to stringResource(MR.strings.every_12_hours),
                        24 to stringResource(MR.strings.daily),
                        48 to stringResource(MR.strings.every_2_days),
                        168 to stringResource(MR.strings.weekly),
                    ),
                    onValueChanged = {
                        BackupCreatorJob.setupTask(context, it)
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    pref = backupPreferences.numberOfBackups(),
                    title = stringResource(MR.strings.max_auto_backups),
                    entries = (1..5).associateWith { it.toString() }.toImmutableMap(),
                    enabled = backupInterval > 0,
                ),
                Preference.PreferenceItem.InfoPreference(
                    stringResource(MR.strings.backup_info) +
                        "\n\n" + stringResource(MR.strings.last_auto_backup_info, relativeTimeSpanString(lastAutoBackup)),
                ),
            ),
        )
    }

    @Composable
    private fun getDataGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        // TODO
        // val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }

        val coverCache = remember { Injekt.get<CoverCache>() }
        val chapterCache = remember { Injekt.get<ChapterCache>() }
        var cacheReadableSizeSema by remember { mutableIntStateOf(0) }
        val cacheReadableSize = remember(cacheReadableSizeSema) { chapterCache.readableSize }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.storage_usage),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MR.strings.storage_usage),
                ) {
                    BasePreferenceWidget(
                        subcomponent = {
                            StorageInfo(
                                modifier = Modifier.padding(horizontal = PrefsHorizontalPadding),
                            )
                        },
                    )
                },

                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.clear_chapter_cache),
                    subtitle = stringResource(MR.strings.used_, cacheReadableSize),
                    onClick = {
                        scope.launchNonCancellableIO {
                            try {
                                val deletedFiles = chapterCache.clear()
                                withUIContext {
                                    context.toast(context.getString(
                                        MR.plurals.cache_cleared,
                                        deletedFiles,
                                        deletedFiles,
                                    ))
                                    cacheReadableSizeSema++
                                }
                            } catch (e: Throwable) {
                                Logger.e(e) { "Unable to clear cache" }
                                withUIContext { context.toast(MR.strings.cache_delete_error) }
                            }
                        }
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.clear_cached_covers_non_library),
                    subtitle = stringResource(
                        MR.strings.delete_all_covers__not_in_library_used_,
                        coverCache.getOnlineCoverCacheSize(),
                    ),
                    onClick = {
                        context.toast(MR.strings.starting_cleanup)
                        scope.launchNonCancellableIO {
                            coverCache.deleteAllCachedCovers()
                        }
                    }
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.clean_up_cached_covers),
                    subtitle = stringResource(
                        MR.strings.delete_old_covers_in_library_used_,
                        coverCache.getChapterCacheSize(),
                    ),
                    onClick = {
                        context.toast(MR.strings.starting_cleanup)
                        scope.launchNonCancellableIO {
                            coverCache.deleteOldCovers()
                        }
                    }
                ),
                /*
                Preference.PreferenceItem.SwitchPreference(
                    pref = libraryPreferences.autoClearChapterCache(),
                    title = stringResource(MR.strings.pref_auto_clear_chapter_cache),
                ),
                 */
            ),
        )
    }
}

const val BACKUPS_HELP_URL = "https://mihon.app/docs/guides/backups"
