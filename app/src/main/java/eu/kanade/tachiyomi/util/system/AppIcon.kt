package eu.kanade.tachiyomi.util.system

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.BuildConfig
import yokai.domain.base.BasePreferences

fun Context.setAppIcon(basePreferences: BasePreferences, selectedIcon: BasePreferences.AppIcons) {
    val enabled = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    val disabled = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT

    var selectedIsEnabled = false

    BasePreferences.AppIcons.entries.forEach { icon ->
        try {
            val componentName = ComponentName(this, icon.id)
            val newState = if (selectedIcon == icon) enabled else disabled

            packageManager.setComponentEnabledSetting(
                componentName,
                newState,
                PackageManager.DONT_KILL_APP
            )

            if (!selectedIsEnabled) selectedIsEnabled = selectedIcon == icon
        } catch (e: Exception) {
            Logger.e(e) { "Failed to set state for ${icon.displayName}" }
        }
    }

    if (selectedIsEnabled) {
        basePreferences.appIcon().set(selectedIcon)

        packageManager.setComponentEnabledSetting(
            ComponentName(this, "${BuildConfig.APPLICATION_ID}.MainActivityDefaultDummy"),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    } else {
        packageManager.setComponentEnabledSetting(
            ComponentName(this, "${BuildConfig.APPLICATION_ID}.MainActivityDefaultDummy"),
            enabled,
            PackageManager.DONT_KILL_APP,
        )
    }
}
