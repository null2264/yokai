package eu.kanade.tachiyomi.ui.setting.controllers

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import yokai.domain.ui.UiPreferences
import yokai.i18n.MR
import yokai.util.lang.getString
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.data.preference.changesIn
import eu.kanade.tachiyomi.data.updater.AppDownloadInstallJob
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.ExtensionUpdateJob
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.migration.MigrationController
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import eu.kanade.tachiyomi.ui.setting.bindTo
import eu.kanade.tachiyomi.ui.setting.defaultValue
import eu.kanade.tachiyomi.ui.setting.infoPreference
import eu.kanade.tachiyomi.ui.setting.intListPreference
import eu.kanade.tachiyomi.ui.setting.onChange
import eu.kanade.tachiyomi.ui.setting.onClick
import eu.kanade.tachiyomi.ui.setting.preference
import eu.kanade.tachiyomi.ui.setting.preferenceCategory
import eu.kanade.tachiyomi.ui.setting.summaryMRes as summaryRes
import eu.kanade.tachiyomi.ui.setting.switchPreference
import eu.kanade.tachiyomi.ui.setting.titleMRes as titleRes
import eu.kanade.tachiyomi.util.lang.addBetaTag
import eu.kanade.tachiyomi.util.view.setAction
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import uy.kohesive.injekt.injectLazy
import yokai.domain.base.BasePreferences.ExtensionInstaller
import yokai.presentation.extension.repo.ExtensionRepoController

class SettingsBrowseController : SettingsLegacyController() {

    val sourceManager: SourceManager by injectLazy()
    var updatedExtNotifPref: SwitchPreferenceCompat? = null

    private val uiPreferences: UiPreferences by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = MR.strings.browse

        preferenceCategory {
            switchPreference {
                bindTo(preferences.hideInLibraryItems())
                titleRes = MR.strings.hide_in_library_items
            }
        }

        preferenceCategory {
            titleRes = MR.strings.extensions
            preference {
                title = context.getString(MR.strings.source_repos).addBetaTag(context)
                onClick { router.pushController(ExtensionRepoController().withFadeTransaction()) }
            }
            switchPreference {
                key = PreferenceKeys.automaticExtUpdates
                titleRes = MR.strings.check_for_extension_updates
                defaultValue = true

                onChange {
                    it as Boolean
                    ExtensionUpdateJob.setupTask(context, it)
                    true
                }
            }
            if (ExtensionManager.canAutoInstallUpdates()) {
                val intPref = intListPreference(activity) {
                    key = PreferenceKeys.autoUpdateExtensions
                    titleRes = MR.strings.auto_update_extensions
                    entryRange = 0..2
                    entriesRes = arrayOf(
                        MR.strings.over_any_network,
                        MR.strings.over_wifi_only,
                        MR.strings.dont_auto_update,
                    )
                    defaultValue = AppDownloadInstallJob.ONLY_ON_UNMETERED
                }
                val infoPref = if (basePreferences.extensionInstaller().get() != ExtensionInstaller.SHIZUKU) {
                    infoPreference(MR.strings.some_extensions_may_not_update)
                } else {
                    null
                }
                val switchPref = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    switchPreference {
                        key = "notify_ext_updated"
                        isPersistent = false
                        titleRes = MR.strings.notify_extension_updated
                        isChecked = Notifications.isNotificationChannelEnabled(
                            context,
                            Notifications.CHANNEL_EXT_UPDATED,
                        )
                        updatedExtNotifPref = this
                        onChange {
                            false
                        }
                        onClick {
                            val intent =
                                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID)
                                    putExtra(
                                        Settings.EXTRA_CHANNEL_ID,
                                        Notifications.CHANNEL_EXT_UPDATED,
                                    )
                                }
                            startActivity(intent)
                        }
                    }
                } else {
                    null
                }
                preferences.automaticExtUpdates().changesIn(viewScope) { value ->
                    arrayOf(intPref, infoPref, switchPref).forEach { it?.isVisible = value }
                }
            }
        }

        preferenceCategory {
            titleRes = MR.strings.pref_global_search
            switchPreference {
                key = PreferenceKeys.onlySearchPinned
                titleRes = MR.strings.only_search_pinned_when
            }
        }

        preferenceCategory {
            titleRes = MR.strings.migration
            // Only show this if someone has mass migrated manga once

            preference {
                titleRes = MR.strings.source_migration
                onClick { router.pushController(MigrationController().withFadeTransaction()) }
            }
            if (preferences.skipPreMigration().get() || preferences.migrationSources()
                .isSet()
            ) {
                switchPreference {
                    key = PreferenceKeys.skipPreMigration
                    titleRes = MR.strings.skip_pre_migration
                    summaryRes = MR.strings.use_last_saved_migration_preferences
                    defaultValue = false
                }
            }
            preference {
                key = "match_pinned_sources"
                titleRes = MR.strings.match_pinned_sources
                summaryRes = MR.strings.only_enable_pinned_for_migration
                onClick {
                    val ogSources = preferences.migrationSources().get()
                    val pinnedSources =
                        preferences.pinnedCatalogues().get().joinToString("/")
                    preferences.migrationSources().set(pinnedSources)
                    (activity as? MainActivity)?.setUndoSnackBar(
                        view?.snack(
                            MR.strings.migration_sources_changed,
                        ) {
                            setAction(MR.strings.undo) {
                                preferences.migrationSources().set(ogSources)
                            }
                        },
                    )
                }
            }

            preference {
                key = "match_enabled_sources"
                titleRes = MR.strings.match_enabled_sources
                summaryRes = MR.strings.only_enable_enabled_for_migration
                onClick {
                    val ogSources = preferences.migrationSources().get()
                    val languages = preferences.enabledLanguages().get()
                    val hiddenCatalogues = preferences.hiddenSources().get()
                    val enabledSources =
                        sourceManager.getCatalogueSources().filter { it.lang in languages }
                            .filterNot { it.id.toString() in hiddenCatalogues }
                            .sortedBy { "(${it.lang}) ${it.name}" }
                            .joinToString("/") { it.id.toString() }
                    preferences.migrationSources().set(enabledSources)
                    (activity as? MainActivity)?.setUndoSnackBar(
                        view?.snack(
                            MR.strings.migration_sources_changed,
                        ) {
                            setAction(MR.strings.undo) {
                                preferences.migrationSources().set(ogSources)
                            }
                        },
                    )
                }
            }

            infoPreference(MR.strings.you_can_migrate_in_library)
        }
        
        preferenceCategory {
            titleRes = MR.strings.sources

            switchPreference {
                bindTo(uiPreferences.enableSourceSwipeAction())
                titleRes = MR.strings.enable_source_swipe_action
            }
        }

        preferenceCategory {
            titleRes = MR.strings.nsfw_sources

            switchPreference {
                key = PreferenceKeys.showNsfwSource
                titleRes = MR.strings.show_in_sources_and_extensions
                summaryRes = MR.strings.requires_app_restart
                defaultValue = true
            }
            infoPreference(MR.strings.does_not_prevent_unofficial_nsfw)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        updatedExtNotifPref?.isChecked = Notifications.isNotificationChannelEnabled(activity, Notifications.CHANNEL_EXT_UPDATED)
    }
}
