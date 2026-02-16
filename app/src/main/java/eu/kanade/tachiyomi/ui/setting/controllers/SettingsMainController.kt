package eu.kanade.tachiyomi.ui.setting.controllers

import android.app.ActivityManager
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.content.getSystemService
import androidx.preference.PreferenceScreen
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.SimpleSwapChangeHandler
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.more.AboutController
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import eu.kanade.tachiyomi.ui.setting.controllers.legacy.SettingsDataLegacyController
import eu.kanade.tachiyomi.ui.setting.controllers.search.SettingsSearchController
import eu.kanade.tachiyomi.ui.setting.iconRes
import eu.kanade.tachiyomi.ui.setting.iconTint
import eu.kanade.tachiyomi.ui.setting.onClick
import eu.kanade.tachiyomi.ui.setting.onLongClick
import eu.kanade.tachiyomi.ui.setting.preference
import eu.kanade.tachiyomi.ui.setting.preferenceLongClickable
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.fadeTransactionHandler
import eu.kanade.tachiyomi.util.view.openInBrowser
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import yokai.i18n.MR
import yokai.util.lang.getString
import eu.kanade.tachiyomi.ui.setting.titleMRes as titleRes

class SettingsMainController : SettingsLegacyController(), FloatingSearchInterface {

    init {
        setHasOptionsMenu(true)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        titleRes = MR.strings.settings

        val tintColor = context.getResourceColor(R.attr.colorSecondary)

        preference {
            iconRes = R.drawable.ic_tune_24dp
            iconTint = tintColor
            titleRes = MR.strings.general
            onClick { navigateTo(SettingsGeneralController()) }
        }
        preference {
            iconRes = R.drawable.ic_appearance_outline_24dp
            iconTint = tintColor
            titleRes = MR.strings.appearance
            onClick { navigateTo(SettingsAppearanceController()) }
        }
        preference {
            iconRes = R.drawable.ic_library_outline_24dp
            iconTint = tintColor
            titleRes = MR.strings.library
            onClick { navigateTo(SettingsLibraryController()) }
        }
        preference {
            iconRes = R.drawable.ic_read_outline_24dp
            iconTint = tintColor
            titleRes = MR.strings.reader
            onClick { navigateTo(SettingsReaderController()) }
        }
        preference {
            iconRes = R.drawable.ic_file_download_24dp
            iconTint = tintColor
            titleRes = MR.strings.downloads
            onClick { navigateTo(SettingsDownloadController()) }
        }
        preference {
            iconRes = R.drawable.ic_browse_outline_24dp
            iconTint = tintColor
            titleRes = MR.strings.browse
            onClick { navigateTo(SettingsBrowseController()) }
        }
        preference {
            iconRes = R.drawable.ic_sync_24dp
            iconTint = tintColor
            titleRes = MR.strings.tracking
            onClick { navigateTo(SettingsTrackingController()) }
        }
        preferenceLongClickable {
            iconRes = R.drawable.ic_storage_24dp
            iconTint = tintColor
            titleRes = MR.strings.data_and_storage
            onClick { navigateTo(SettingsDataController()) }
            onLongClick {
                navigateTo(SettingsDataLegacyController())
                context.toast("You're entering legacy version of 'Data and storage'")
            }
        }
        preference {
            iconRes = R.drawable.ic_security_24dp
            iconTint = tintColor
            titleRes = MR.strings.security
            onClick { navigateTo(SettingsSecurityController()) }
        }
        preference {
            iconRes = R.drawable.ic_code_24dp
            iconTint = tintColor
            titleRes = MR.strings.advanced
            onClick { navigateTo(SettingsAdvancedController()) }
        }
        preference {
            iconRes = R.drawable.ic_info_outline_24dp
            iconTint = tintColor
            titleRes = MR.strings.about
            onClick { navigateTo(AboutController()) }
        }
        this
    }
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.settings_main, menu)
        // Change hint to show global search.
        activityBinding?.searchToolbar?.searchQueryHint = applicationContext?.getString(MR.strings.search_settings)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_help -> openInBrowser(URL_HELP)
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActionViewExpand(item: MenuItem?) {
        val isLowRam = activity?.getSystemService<ActivityManager>()?.isLowRamDevice == true
        router.pushController(
            RouterTransaction.with(SettingsSearchController())
                .pushChangeHandler(SimpleSwapChangeHandler(removesFromViewOnPush = isLowRam))
                .popChangeHandler(fadeTransactionHandler()),
        )
    }

    private fun navigateTo(controller: Controller) {
        router.pushController(controller.withFadeTransaction())
    }

    private companion object {
        private const val URL_HELP = "https://tachiyomi.org/docs/guides/troubleshooting/"
    }
}
