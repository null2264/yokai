package yokai.domain.base

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.preference.Preference
import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.core.preference.getEnum
import eu.kanade.tachiyomi.util.system.GLUtil
import yokai.i18n.MR

class BasePreferences(private val preferenceStore: PreferenceStore) {
    fun extensionInstaller() = preferenceStore.getEnum("extension_installer", ExtensionInstaller.PACKAGEINSTALLER)

    enum class ExtensionInstaller(val titleResId: StringResource, val requiresSystemPermission: Boolean) {
        PACKAGEINSTALLER(MR.strings.ext_installer_packageinstaller, true),
        SHIZUKU(MR.strings.ext_installer_shizuku, false),
        PRIVATE(MR.strings.ext_installer_private, false),
        LEGACY(MR.strings.ext_installer_legacy, true),  // Technically useless, but just in case it being missing crashes the app
        ;

        companion object {
            fun migrate(oldValue: Int) =
                when (oldValue) {
                    1 -> BasePreferences.ExtensionInstaller.SHIZUKU
                    2 -> BasePreferences.ExtensionInstaller.PRIVATE
                    else -> BasePreferences.ExtensionInstaller.PACKAGEINSTALLER
                }
        }
    }

    fun displayProfile() = preferenceStore.getString("pref_display_profile_key", "")

    fun hasShownOnboarding() = preferenceStore.getBoolean(Preference.appStateKey("onboarding_complete"), false)

    fun crashReport() = preferenceStore.getBoolean("pref_crash_report", true)

    fun longTapBrowseNavBehaviour() = preferenceStore.getEnum("pref_browser_long_tap", LongTapBrowse.DEFAULT)

    enum class LongTapBrowse(val titleResId: StringResource) {
        DEFAULT(MR.strings.browse_long_tap_default),
        SEARCH(MR.strings.browse_long_tap_search),
    }

    fun longTapRecentsNavBehaviour() = preferenceStore.getEnum("pref_recents_long_tap", LongTapRecents.DEFAULT)

    enum class LongTapRecents(val titleResId: StringResource) {
        DEFAULT(MR.strings.recents_long_tap_default),
        LAST_READ(MR.strings.recents_long_tap_last_read),
    }

    fun hardwareBitmapThreshold() = preferenceStore.getInt("pref_hardware_bitmap_threshold", GLUtil.SAFE_TEXTURE_LIMIT)

    fun composeLibrary() = preferenceStore.getBoolean("pref_use_compose_library", false)

    fun appIcon() = preferenceStore.getEnum("pref_use_compose_library", AppIcons.DEFAULT)

    enum class AppIcons(val displayName: String, val id: String, val icon: Int) {
        DEFAULT("Default", "${BuildConfig.APPLICATION_ID}.MainActivityDefault", R.mipmap.ic_launcher_round),
        BLUE("Blue", "${BuildConfig.APPLICATION_ID}.MainActivityBlue", R.mipmap.ic_launcher_round_blue),
        ORANGE("Orange", "${BuildConfig.APPLICATION_ID}.MainActivityOrange", R.mipmap.ic_launcher_round_orange),
        GRAY("Gray", "${BuildConfig.APPLICATION_ID}.MainActivityGray", R.mipmap.ic_launcher_round_gray),
        CLASSIC("Classic", "${BuildConfig.APPLICATION_ID}.MainActivityClassic", R.mipmap.ic_launcher_round_classic),
    }
}
