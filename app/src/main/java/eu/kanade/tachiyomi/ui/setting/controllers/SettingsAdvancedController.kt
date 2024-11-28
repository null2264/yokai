package eu.kanade.tachiyomi.ui.setting.controllers

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceScreen
import co.touchlab.kermit.Logger
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob.Target
import eu.kanade.tachiyomi.data.preference.PreferenceKeys
import eu.kanade.tachiyomi.data.preference.changesIn
import eu.kanade.tachiyomi.extension.ShizukuInstaller
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.network.PREF_DOH_360
import eu.kanade.tachiyomi.network.PREF_DOH_ADGUARD
import eu.kanade.tachiyomi.network.PREF_DOH_ALIDNS
import eu.kanade.tachiyomi.network.PREF_DOH_CLOUDFLARE
import eu.kanade.tachiyomi.network.PREF_DOH_CONTROLD
import eu.kanade.tachiyomi.network.PREF_DOH_DNSPOD
import eu.kanade.tachiyomi.network.PREF_DOH_GOOGLE
import eu.kanade.tachiyomi.network.PREF_DOH_MULLVAD
import eu.kanade.tachiyomi.network.PREF_DOH_NJALLA
import eu.kanade.tachiyomi.network.PREF_DOH_QUAD101
import eu.kanade.tachiyomi.network.PREF_DOH_QUAD9
import eu.kanade.tachiyomi.network.PREF_DOH_SHECAN
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import eu.kanade.tachiyomi.ui.setting.bindTo
import eu.kanade.tachiyomi.ui.setting.controllers.database.ClearDatabaseController
import eu.kanade.tachiyomi.ui.setting.controllers.debug.DebugController
import eu.kanade.tachiyomi.ui.setting.defaultValue
import eu.kanade.tachiyomi.ui.setting.editTextPreference
import eu.kanade.tachiyomi.ui.setting.infoPreference
import eu.kanade.tachiyomi.ui.setting.intListPreference
import eu.kanade.tachiyomi.ui.setting.listPreference
import eu.kanade.tachiyomi.ui.setting.onChange
import eu.kanade.tachiyomi.ui.setting.onClick
import eu.kanade.tachiyomi.ui.setting.preference
import eu.kanade.tachiyomi.ui.setting.preferenceCategory
import eu.kanade.tachiyomi.ui.setting.switchPreference
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.lang.addBetaTag
import eu.kanade.tachiyomi.util.system.GLUtil
import eu.kanade.tachiyomi.util.system.disableItems
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.localeContext
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.workManager
import eu.kanade.tachiyomi.util.view.openInBrowser
import eu.kanade.tachiyomi.util.view.setMessage
import eu.kanade.tachiyomi.util.view.setPositiveButton
import eu.kanade.tachiyomi.util.view.setTitle
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import java.io.File
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.Headers
import rikka.sui.Sui
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.base.BasePreferences.ExtensionInstaller
import yokai.domain.extension.interactor.TrustExtension
import yokai.domain.manga.interactor.GetManga
import yokai.domain.source.SourcePreferences
import yokai.domain.ui.settings.ReaderPreferences
import yokai.i18n.MR
import yokai.util.lang.getString
import android.R as AR
import eu.kanade.tachiyomi.ui.setting.summaryMRes as summaryRes
import eu.kanade.tachiyomi.ui.setting.titleMRes as titleRes

class SettingsAdvancedController : SettingsLegacyController() {

    private val network: NetworkHelper by injectLazy()
    private val networkPreferences: NetworkPreferences by injectLazy()
    private val readerPreferences: ReaderPreferences by injectLazy()
    private val sourcePreferences: SourcePreferences by injectLazy()

    private val db: DatabaseHelper by injectLazy()

    private val getManga: GetManga by injectLazy()

    private val downloadManager: DownloadManager by injectLazy()

    private val trustExtension: TrustExtension by injectLazy()

    private val isUpdaterEnabled = BuildConfig.INCLUDE_UPDATER

    @SuppressLint("BatteryLife")
    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = MR.strings.advanced

        switchPreference {
            bindTo(basePreferences.crashReport())
            titleRes = MR.strings.send_crash_report
            summaryRes = MR.strings.helps_fix_bugs
        }

        preference {
            key = "dump_crash_logs"
            titleRes = MR.strings.dump_crash_logs
            summaryRes = MR.strings.saves_error_logs

            onClick {
                (activity as? AppCompatActivity)?.lifecycleScope?.launchIO {
                    CrashLogUtil(context.localeContext).dumpLogs()
                }
            }
        }

        switchPreference {
            bindTo(networkPreferences.verboseLogging())
            titleRes = MR.strings.pref_verbose_logging
            summaryRes = MR.strings.pref_verbose_logging_summary
        }

        preference {
            key = "debug_info"
            titleRes = MR.strings.pref_debug_info

            onClick {
                router.pushController(DebugController().withFadeTransaction())
            }
        }

        preferenceCategory {
            titleRes = MR.strings.label_background_activity
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager?
            if (pm != null) {
                preference {
                    key = "disable_batt_opt"
                    titleRes = MR.strings.disable_battery_optimization
                    summaryRes = MR.strings.disable_if_issues_with_updating

                    onClick {
                        val packageName: String = context.packageName
                        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                            val intent = Intent().apply {
                                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                data = "package:$packageName".toUri()
                            }
                            startActivity(intent)
                        } else {
                            context.toast(MR.strings.battery_optimization_disabled)
                        }
                    }
                }
            }

            preference {
                key = "pref_dont_kill_my_app"
                title = "Don't kill my app!"
                summaryRes = MR.strings.about_dont_kill_my_app

                onClick {
                    openInBrowser("https://dontkillmyapp.com/")
                }
            }
        }

        if (isUpdaterEnabled) {
            switchPreference {
                titleRes = MR.strings.check_for_beta_releases
                summaryRes = MR.strings.try_new_features
                bindTo(preferences.checkForBetas())

                onChange {
                    it as Boolean
                    if (it != BuildConfig.BETA) {
                        activity!!.materialAlertDialog()
                            .setTitle(MR.strings.warning)
                            .setMessage(if (it) MR.strings.warning_enroll_into_beta else MR.strings.warning_unenroll_from_beta)
                            .setPositiveButton(AR.string.ok) { _, _ -> isChecked = it }
                            .setNegativeButton(AR.string.cancel, null)
                            .show()
                        false
                    } else {
                        true
                    }
                }
            }
        }

        preferenceCategory {
            titleRes = MR.strings.data_management

            preference {
                titleRes = MR.strings.force_download_cache_refresh
                summaryRes = MR.strings.force_download_cache_refresh_summary
                onClick { downloadManager.refreshCache() }
            }

            preference {
                key = "clean_downloaded_chapters"
                titleRes = MR.strings.clean_up_downloaded_chapters

                summaryRes = MR.strings.delete_unused_chapters

                onClick {
                    activity!!.materialAlertDialog()
                        .setTitle(MR.strings.clean_up_downloaded_chapters)
                        .setMultiChoiceItems(
                            R.array.clean_up_downloads,
                            booleanArrayOf(true, true, true),
                        ) { dialog, position, _ ->
                            if (position == 0) {
                                val listView = (dialog as AlertDialog).listView
                                listView.setItemChecked(position, true)
                            }
                        }
                        .setPositiveButton(AR.string.ok) { dialog, _ ->
                            val listView = (dialog as AlertDialog).listView
                            val deleteRead = listView.isItemChecked(1)
                            val deleteNonFavorite = listView.isItemChecked(2)
                            cleanupDownloads(deleteRead, deleteNonFavorite)
                        }
                        .setNegativeButton(AR.string.cancel, null)
                        .show().apply {
                            disableItems(arrayOf(activity!!.getString(MR.strings.clean_orphaned_downloads)))
                        }
                }
            }
            preference {
                key = "pref_clear_webview_data"
                titleRes = MR.strings.pref_clear_webview_data

                onClick { clearWebViewData() }
            }
            preference {
                key = "clear_database"
                titleRes = MR.strings.clear_database
                summaryRes = MR.strings.clear_database_summary
                onClick { router.pushController(ClearDatabaseController().withFadeTransaction()) }
            }
        }

        preferenceCategory {
            titleRes = MR.strings.network
            preference {
                key = "clear_cookies"
                titleRes = MR.strings.clear_cookies

                onClick {
                    network.cookieJar.removeAll()
                    activity?.toast(MR.strings.cookies_cleared)
                }
            }
            intListPreference(activity) {
                key = PreferenceKeys.dohProvider
                titleRes = MR.strings.doh
                val entryMap = mapOf(
                    -1 to context.getString(MR.strings.disabled),
                    PREF_DOH_CLOUDFLARE to "Cloudflare",
                    PREF_DOH_GOOGLE to "Google",
                    PREF_DOH_ADGUARD to "AdGuard",
                    PREF_DOH_QUAD9 to "Quad9",
                    PREF_DOH_ALIDNS to "AliDNS",
                    PREF_DOH_DNSPOD to "DNSPod",
                    PREF_DOH_360 to "360",
                    PREF_DOH_QUAD101 to "Quad 101",
                    PREF_DOH_MULLVAD to "Mullvad",
                    PREF_DOH_CONTROLD to "Control D",
                    PREF_DOH_NJALLA to "Njalla",
                    PREF_DOH_SHECAN to "Shecan",
                )
                entries = entryMap.values.toList()
                entryValues = entryMap.keys.toList()
                defaultValue = -1
                onChange {
                    activity?.toast(MR.strings.requires_app_restart)
                    true
                }
            }
            editTextPreference(activity) {
                bindTo(networkPreferences.defaultUserAgent())
                titleRes = MR.strings.user_agent_string

                onChange {
                    it as String
                    try {
                        // OkHttp checks for valid values internally
                        Headers.Builder().add("User-Agent", it)
                    } catch (_: IllegalArgumentException) {
                        context.toast(MR.strings.error_user_agent_string_invalid)
                        return@onChange false
                    }
                    context.toast(MR.strings.requires_app_restart)
                    true
                }
            }
        }

        preferenceCategory {
            titleRes = MR.strings.extensions

            listPreference(activity) {
                bindTo(basePreferences.extensionInstaller())
                titleRes = MR.strings.ext_installer_pref

                val values = ExtensionInstaller.entries.toList()
                entriesRes = values.map { it.titleResId }.toTypedArray()
                entryValues = values.map { it.name }.toTypedArray().toList()

                onChange {
                    if (it == ExtensionInstaller.SHIZUKU) {
                        return@onChange if (!context.isPackageInstalled(ShizukuInstaller.shizukuPkgName) && !Sui.isSui()) {
                            context.materialAlertDialog()
                                .setTitle(MR.strings.ext_installer_shizuku)
                                .setMessage(MR.strings.ext_installer_shizuku_unavailable_dialog)
                                .setPositiveButton(MR.strings.download) { _, _ ->
                                    openInBrowser(ShizukuInstaller.downloadLink)
                                }
                                .setNegativeButton(AR.string.cancel, null)
                                .show()
                            false
                        } else {
                            true
                        }
                    }
                    true
                }
            }
            infoPreference(MR.strings.ext_installer_summary).apply {
                basePreferences.extensionInstaller().changesIn(viewScope) {
                    when (it) {
                        ExtensionInstaller.SHIZUKU -> {
                            summary = context.getString(MR.strings.ext_installer_summary)
                            isVisible = true && Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                        }
                        ExtensionInstaller.LEGACY -> {
                            summary = context.getString(MR.strings.ext_installer_summary_legacy)
                            isVisible = true
                        }
                        else -> isVisible = false
                    }
                }
            }
            preference {
                titleRes = MR.strings.action_revoke_all_extensions

                onClick {
                    activity?.materialAlertDialog()
                        ?.setTitle(MR.strings.confirm_revoke_all_extensions)
                        ?.setPositiveButton(AR.string.ok) { _, _ ->
                            trustExtension.revokeAll()
                            activity?.toast(MR.strings.requires_app_restart)
                        }
                        ?.setNegativeButton(AR.string.cancel, null)?.show()
                }
            }
        }

        preferenceCategory {
            titleRes = MR.strings.library
            preference {
                key = "refresh_lib_meta"
                titleRes = MR.strings.refresh_library_metadata
                summaryRes = MR.strings.updates_covers_genres_desc

                onClick { LibraryUpdateJob.startNow(context, target = Target.DETAILS) }
            }
            preference {
                key = "refresh_teacking_meta"
                titleRes = MR.strings.refresh_tracking_metadata
                summaryRes = MR.strings.updates_tracking_details

                onClick { LibraryUpdateJob.startNow(context, target = Target.TRACKING) }
            }
        }

        preferenceCategory {
            titleRes = MR.strings.reader

            intListPreference(activity) {
                bindTo(basePreferences.hardwareBitmapThreshold())
                titleRes = MR.strings.pref_hardware_bitmap_threshold

                val entryMap = GLUtil.CUSTOM_TEXTURE_LIMIT_OPTIONS
                    .mapIndexed { index, option ->
                        val display = if (index == 0) {
                            context.getString(MR.strings.pref_hardware_bitmap_threshold_default, option)
                        } else {
                            option.toString()
                        }
                        option to display
                    }
                    .toMap()
                    .toImmutableMap()
                entries = entryMap.values.toList()
                entryValues = entryMap.keys.toList()

                isVisible = GLUtil.DEVICE_TEXTURE_LIMIT > GLUtil.SAFE_TEXTURE_LIMIT

                basePreferences.hardwareBitmapThreshold().changesIn(viewScope) { threshold ->
                    summary = context.getString(MR.strings.pref_hardware_bitmap_threshold_summary, entryMap[threshold].orEmpty())
                }
            }

            preference {
                bindTo(basePreferences.displayProfile())
                titleRes = MR.strings.pref_display_profile
                onClick {
                    (activity as? MainActivity)?.showColourProfilePicker()
                }

                basePreferences.displayProfile().changesIn(viewScope) { path ->
                    val actualPath = UniFile.fromUri(context, path.toUri())?.filePath ?: path
                    if (actualPath.isNotEmpty()) summary = actualPath
                }
            }

            switchPreference {
                bindTo(readerPreferences.debugMode())
                titleRes = MR.strings.pref_reader_debug_mode
            }
        }

        preferenceCategory {
            titleRes = MR.strings.local_source

            switchPreference {
                bindTo(sourcePreferences.externalLocalSource())
                title = context.getString(MR.strings.pref_external_local_source).addBetaTag(context)
            }
        }

        preferenceCategory {
            title = "Danger zone!"

            preference {
                title = "Crash the app!"
                summary = "To test crashes"
                onClick {
                    activity!!.materialAlertDialog()
                        .setTitle(MR.strings.warning)
                        .setMessage("I told you this would crash the app, why would you want that?")
                        .setPositiveButton("Crash it anyway") { _, _ -> throw RuntimeException("Fell into the void") }
                        .setNegativeButton("Nevermind", null)
                        .show()
                }
            }

            preference {
                title = "Prune finished workers"
                summary = "In case worker stuck in FAILED state and you're too impatient to wait"
                onClick {
                    activity!!.materialAlertDialog()
                        .setTitle("Are you sure?")
                        .setMessage(
                            "Failed workers should clear out by itself eventually, " +
                                "this option should only be used if you're being impatient and you know what you're doing."
                        )
                        .setPositiveButton("Prune") { _, _ -> context.workManager.pruneWork() }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun cleanupDownloads(removeRead: Boolean, removeNonFavorite: Boolean) {
        if (job?.isActive == true) return
        activity?.toast(MR.strings.starting_cleanup)
        job = GlobalScope.launch(Dispatchers.IO, CoroutineStart.DEFAULT) {
            val mangaList = getManga.awaitAll()
            val sourceManager: SourceManager = Injekt.get()
            val downloadProvider = DownloadProvider(activity!!)
            var foldersCleared = 0
            val sources = sourceManager.getOnlineSources()

            for (source in sources) {
                val mangaFolders = downloadManager.getMangaFolders(source)
                val sourceManga = mangaList.filter { it.source == source.id }

                for (mangaFolder in mangaFolders) {
                    val manga = sourceManga.find { downloadProvider.getMangaDirName(it) == mangaFolder.name }
                    if (manga == null) {
                        // download is orphaned and not even in the db delete it if remove non favorited is enabled
                        if (removeNonFavorite) {
                            foldersCleared += 1 + (mangaFolder.listFiles()?.size ?: 0)
                            mangaFolder.delete()
                        }
                        continue
                    }
                    val chapterList = db.getChapters(manga).executeAsBlocking()
                    foldersCleared += downloadManager.cleanupChapters(chapterList, manga, source, removeRead, removeNonFavorite)
                }
            }
            launchUI {
                val activity = activity ?: return@launchUI
                val cleanupString =
                    if (foldersCleared == 0) {
                        activity.getString(MR.strings.no_folders_to_cleanup)
                    } else {
                        activity.getString(
                            MR.plurals.cleanup_done,
                            foldersCleared,
                            foldersCleared,
                        )
                    }
                activity.toast(cleanupString, Toast.LENGTH_LONG)
            }
        }
    }

    private fun clearWebViewData() {
        if (activity == null) return
        try {
            val webview = WebView(activity!!)
            webview.setDefaultSettings()
            webview.clearCache(true)
            webview.clearFormData()
            webview.clearHistory()
            webview.clearSslPreferences()
            WebStorage.getInstance().deleteAllData()
            activity?.applicationInfo?.dataDir?.let { File("$it/app_webview/").deleteRecursively() }
            activity?.toast(MR.strings.webview_data_deleted)
        } catch (e: Throwable) {
            Logger.e(e) { "Unable to delete WebView data" }
            activity?.toast(MR.strings.cache_delete_error)
        }
    }

    private companion object {
        private var job: Job? = null
    }
}
