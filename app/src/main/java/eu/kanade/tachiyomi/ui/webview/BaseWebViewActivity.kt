package eu.kanade.tachiyomi.ui.webview

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.changesIn
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate
import eu.kanade.tachiyomi.util.system.getPrefTheme
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isInNightMode
import android.R as AR

// FIXME: Not sure if some of these stuff still needed
open class BaseWebViewActivity : BaseActivity<ViewBinding>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        delegate.localNightMode = preferences.nightMode().get()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ColorUtils.setAlphaComponent(
            getResourceColor(R.attr.colorSurface),
            255,
        )

        preferences.incognitoMode()
            .changesIn(lifecycleScope) {
                SecureActivityDelegate.setSecure(this)
            }
    }

    @SuppressLint("ResourceType")
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val lightMode = !isInNightMode()
        val prefTheme = getPrefTheme(preferences)
        setTheme(prefTheme.styleRes)
        if (!lightMode && preferences.themeDarkAmoled().get()) {
            setTheme(R.style.ThemeOverlay_Tachiyomi_Amoled)
        }
        val themeValue = TypedValue()
        theme.resolveAttribute(AR.attr.windowLightStatusBar, themeValue, true)

        val wic = WindowInsetsControllerCompat(window, window.decorView)
        wic.isAppearanceLightStatusBars = themeValue.data == -1
        wic.isAppearanceLightNavigationBars = themeValue.data == -1

        val attrs = theme.obtainStyledAttributes(
            intArrayOf(
                R.attr.colorSurface,
                R.attr.colorPrimaryVariant,
            ),
        )
        val colorSurface = attrs.getColor(0, 0)
        val colorPrimaryVariant = attrs.getColor(1, 0)
        attrs.recycle()

        window.statusBarColor = ColorUtils.setAlphaComponent(colorSurface, 255)
        window.navigationBarColor =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O || !lightMode) {
                colorPrimaryVariant
            } else {
                Color.BLACK
            }

        WindowCompat.setDecorFitsSystemWindows(window, false)
    }
}
