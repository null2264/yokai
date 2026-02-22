package yokai.presentation.settings.screen

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.ui.setting.controllers.database.ClearDatabaseController
import eu.kanade.tachiyomi.ui.setting.controllers.debug.DebugController
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.compose.LocalDialogHostState
import eu.kanade.tachiyomi.util.compose.LocalRouter
import eu.kanade.tachiyomi.util.compose.currentOrThrow
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.localeContext
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import java.io.File
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy
import yokai.domain.base.BasePreferences
import yokai.i18n.MR
import yokai.presentation.component.preference.Preference
import yokai.presentation.settings.ComposableSettings
import yokai.presentation.settings.screen.advanced.awaitCheckForBetaPrompt

object SettingsAdvancedScreen : ComposableSettings {
    @Composable
    override fun getTitleRes(): StringResource = MR.strings.advanced

    @Composable
    override fun getPreferences(): List<Preference> {
        val preferences: PreferencesHelper by injectLazy()
        val basePreferences: BasePreferences by injectLazy()
        val networkPreferences: NetworkPreferences by injectLazy()
        val isUpdaterEnabled = BuildConfig.INCLUDE_UPDATER

        return buildList {
            add(Preference.PreferenceItem.SwitchPreference(
                pref = basePreferences.crashReport(),
                title = stringResource(MR.strings.send_crash_report),
                subtitle = stringResource(MR.strings.helps_fix_bugs),
            ))
            add(getDumpCrashLog())
            add(Preference.PreferenceItem.SwitchPreference(
                pref = networkPreferences.verboseLogging(),
                title = stringResource(MR.strings.pref_verbose_logging),
                subtitle = stringResource(MR.strings.pref_verbose_logging_summary),
            ))
            add(getDebugInfo())
            add(getBackgroundActivityGroup())
            if (isUpdaterEnabled) {
                add(getCheckForBeta(preferences))
            }
            add(getDataManagementGroup())
        }.toPersistentList()
    }

    @Composable
    private fun getDumpCrashLog(): Preference.PreferenceItem.TextPreference {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        return Preference.PreferenceItem.TextPreference(
            title = stringResource(MR.strings.dump_crash_logs),
            subtitle = stringResource(MR.strings.saves_error_logs),
            onClick = {
                scope.launchIO {
                    CrashLogUtil(context.localeContext).dumpLogs()
                }
            }
        )
    }

    @Composable
    private fun getDebugInfo(): Preference.PreferenceItem.TextPreference {
        val router = LocalRouter.currentOrThrow

        return Preference.PreferenceItem.TextPreference(
            title = stringResource(MR.strings.pref_debug_info),
            onClick = {
                router.pushController(DebugController().withFadeTransaction())
            }
        )
    }

    @Composable
    private fun getBackgroundActivityGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current

        val children = buildList {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager?
            if (pm != null) {
                add(Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.disable_battery_optimization),
                    subtitle = stringResource(MR.strings.disable_if_issues_with_updating),
                    onClick = {
                        val packageName: String = context.packageName
                        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                            val intent = Intent().apply {
                                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                data = "package:$packageName".toUri()
                            }
                            context.startActivity(intent)
                        } else {
                            context.toast(MR.strings.battery_optimization_disabled)
                        }
                    },
                ))
            }
            add(Preference.PreferenceItem.TextPreference(
                title = "Don't kill my app!",
                subtitle = stringResource(MR.strings.about_dont_kill_my_app),
                onClick = {
                    uriHandler.openUri("https://dontkillmyapp.com/")
                },
            ))
        }.toPersistentList()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_background_activity),
            preferenceItems = children,
        )
    }

    @Composable
    private fun getCheckForBeta(preferences: PreferencesHelper): Preference.PreferenceItem.SwitchPreference {
        val scope = rememberCoroutineScope()
        val alertDialog = LocalDialogHostState.currentOrThrow
        val pref = preferences.checkForBetas()

        return Preference.PreferenceItem.SwitchPreference(
            pref = pref,
            title = stringResource(MR.strings.check_for_beta_releases),
            subtitle = stringResource(MR.strings.try_new_features),
            onValueChanged = {
                if (it != BuildConfig.BETA) {
                    scope.launch {
                        alertDialog.awaitCheckForBetaPrompt(it) {
                            pref.set(it)
                        }
                    }
                    false
                } else {
                    true
                }
            }
        )
    }

    @Composable
    private fun getDataManagementGroup(): Preference.PreferenceGroup {
        // FIXME: Maybe this should be moved to Data and storage?
        val context = LocalContext.current
        val router = LocalRouter.currentOrThrow

        val downloadManager: DownloadManager by injectLazy()

        val children = buildList {
            add(Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.force_download_cache_refresh),
                subtitle = stringResource(MR.strings.force_download_cache_refresh_summary),
                onClick = { downloadManager.refreshCache() },
            ))
            add(Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.clean_up_downloaded_chapters),
                subtitle = stringResource(MR.strings.delete_unused_chapters),
                onClick = {
                    // TODO:
                },
            ))
            add(Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_clear_webview_data),
                onClick = { context.clearWebViewData() },
            ))
            add(Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.clear_database),
                subtitle = stringResource(MR.strings.clear_database_summary),
                onClick = { router.pushController(ClearDatabaseController().withFadeTransaction()) },
            ))
        }.toPersistentList()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.data_management),
            preferenceItems = children,
        )
    }

    private fun Context.clearWebViewData() {
        try {
            val webview = WebView(this)
            webview.setDefaultSettings()
            webview.clearCache(true)
            webview.clearFormData()
            webview.clearHistory()
            webview.clearSslPreferences()
            WebStorage.getInstance().deleteAllData()
            applicationInfo?.dataDir?.let { File("$it/app_webview/").deleteRecursively() }
            toast(MR.strings.webview_data_deleted)
        } catch (e: Throwable) {
            Logger.e(e) { "Unable to delete WebView data" }
            toast(MR.strings.cache_delete_error)
        }
    }
}
