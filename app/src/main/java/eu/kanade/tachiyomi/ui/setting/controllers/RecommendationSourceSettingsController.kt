package eu.kanade.tachiyomi.ui.setting.controllers

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import eu.kanade.tachiyomi.ui.setting.defaultValue
import eu.kanade.tachiyomi.ui.setting.preferenceCategory
import eu.kanade.tachiyomi.ui.setting.switchPreference
import java.util.Locale
import uy.kohesive.injekt.injectLazy
import yokai.i18n.MR
import yokai.util.lang.getString
import eu.kanade.tachiyomi.ui.setting.titleMRes as titleRes

class RecommendationSourceSettingsController : SettingsLegacyController() {
    private val sourceManager: SourceManager by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = MR.strings.recommendation_source_settings

        sourceManager.getOnlineSources()
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.lang }, { it.name.lowercase(Locale.ROOT) }))
            .groupBy { it.lang }
            .forEach { (language, sources) ->
                preferenceCategory {
                    title = language.uppercase(Locale.ROOT)
                    sources.forEach { source ->
                        switchPreference {
                            key = "recommendation_source_${source.id}_network_enabled_v1"
                            title = source.name
                            summary = context.getString(MR.strings.recommendation_source_enabled_summary)
                            defaultValue = false
                        }
                    }
                }
            }
    }
}
