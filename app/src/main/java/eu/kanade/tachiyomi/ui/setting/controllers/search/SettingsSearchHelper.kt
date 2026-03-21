package eu.kanade.tachiyomi.ui.setting.controllers.search

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.ui.setting.SettingsComposeController
import eu.kanade.tachiyomi.ui.setting.SettingsControllerInterface
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import eu.kanade.tachiyomi.ui.setting.controllers.SettingsAppearanceController
import eu.kanade.tachiyomi.ui.setting.controllers.SettingsBrowseController
import eu.kanade.tachiyomi.ui.setting.controllers.SettingsDownloadController
import eu.kanade.tachiyomi.ui.setting.controllers.SettingsGeneralController
import eu.kanade.tachiyomi.ui.setting.controllers.SettingsLibraryController
import eu.kanade.tachiyomi.ui.setting.controllers.SettingsReaderController
import eu.kanade.tachiyomi.ui.setting.controllers.SettingsSecurityController
import eu.kanade.tachiyomi.ui.setting.controllers.SettingsTrackingController
import eu.kanade.tachiyomi.ui.setting.controllers.legacy.SettingsAdvancedLegacyController
import eu.kanade.tachiyomi.ui.setting.controllers.legacy.SettingsDataLegacyController
import eu.kanade.tachiyomi.util.system.isLTR
import eu.kanade.tachiyomi.util.system.launchNow
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

object SettingsSearchHelper {
    private var prefSearchResultList: MutableList<SettingsSearchResult> = mutableListOf()

    /**
     * All subclasses of `SettingsController` should be listed here, in order to have their preferences searchable.
     */
    private val settingControllersList: List<KClass<out SettingsControllerInterface>> = listOf(
        SettingsAdvancedLegacyController::class,
        SettingsDataLegacyController::class,
        // SettingsDataController::class,  // compose
        SettingsBrowseController::class,
        SettingsDownloadController::class,
        SettingsGeneralController::class,
        SettingsAppearanceController::class,
        SettingsSecurityController::class,
        SettingsLibraryController::class,
        SettingsReaderController::class,
        SettingsTrackingController::class,
    )

    /**
     * Must be called to populate `prefSearchResultList`
     */
    @SuppressLint("RestrictedApi")
    fun initPreferenceSearchResultCollection(context: Context) {
        val preferenceManager = PreferenceManager(context)
        prefSearchResultList.clear()

        launchNow {
            settingControllersList.forEach { kClass ->
                when (val ctrl = kClass.createInstance()) {
                    is SettingsLegacyController -> {
                        val settingsPrefScreen =
                            ctrl.setupPreferenceScreen(preferenceManager.createPreferenceScreen(context))
                        val prefCount = settingsPrefScreen.preferenceCount
                        for (i in 0 until prefCount) {
                            val rootPref = settingsPrefScreen.getPreference(i)
                            if (rootPref.title == null) continue // no title, not a preference. (note: only info notes appear to not have titles)
                            getLegacySettingSearchResult(ctrl, rootPref, "${settingsPrefScreen.title}")
                        }
                    }
                    is SettingsComposeController -> {
                        // TODO: Impossible to achieve, require search to be composable
                        // ctrl.getComposableSettings().getPreferences()
                    }
                }
            }
        }
    }

    fun getFilteredResults(query: String): List<SettingsSearchResult> {
        return prefSearchResultList.filter {
            val inTitle = it.title.contains(query, true)
            val inSummary = it.summary.contains(query, true)
            val inBreadcrumb = it.breadcrumb.replace(">", "").contains(query, true)

            return@filter inTitle || inSummary || inBreadcrumb
        }
    }

    /**
     * Extracts the data needed from a `Preference` to create a `SettingsSearchResult`, and then adds it to `prefSearchResultList`
     * Future enhancement: make bold the text matched by the search query.
     */
    private fun getLegacySettingSearchResult(
        ctrl: SettingsLegacyController,
        pref: Preference,
        breadcrumbs: String = "",
    ) {
        val resources = ctrl.resources
        when {
            pref is PreferenceGroup -> {
                val breadcrumbsStr = addLocalizedBreadcrumb(breadcrumbs, "${pref.title}", resources)

                for (x in 0 until pref.preferenceCount) {
                    val subPref = pref.getPreference(x)
                    getLegacySettingSearchResult(ctrl, subPref, breadcrumbsStr) // recursion
                }
            }
            pref is PreferenceCategory -> {
                val breadcrumbsStr = addLocalizedBreadcrumb(breadcrumbs, "${pref.title}", resources)

                for (x in 0 until pref.preferenceCount) {
                    val subPref = pref.getPreference(x)
                    getLegacySettingSearchResult(ctrl, subPref, breadcrumbsStr) // recursion
                }
            }
            (pref.title != null && pref.isVisible) -> {
                // Is an actual preference
                val title = pref.title.toString()
                // ListPreferences occasionally run into ArrayIndexOutOfBoundsException issues
                val summary = try { pref.summary?.toString() ?: "" } catch (e: Throwable) { "" }

                prefSearchResultList.add(
                    SettingsSearchResult(
                        key = pref.key,
                        title = title,
                        summary = summary,
                        breadcrumb = breadcrumbs,
                        searchController = ctrl,
                    ),
                )
            }
        }
    }

    private fun addLocalizedBreadcrumb(path: String, node: String, resources: Resources?): String {
        return if ((resources ?: Resources.getSystem()).isLTR) {
            // This locale reads left to right.
            "$path > $node"
        } else {
            // This locale reads right to left.
            "$node < $path"
        }
    }

    data class SettingsSearchResult(
        val key: String?,
        val title: String,
        val summary: String,
        val breadcrumb: String,
        val searchController: SettingsControllerInterface,
    )
}
