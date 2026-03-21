package eu.kanade.tachiyomi.ui.setting.controllers

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.data.preference.changesIn
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import eu.kanade.tachiyomi.ui.setting.ThemePreference
import eu.kanade.tachiyomi.ui.setting.bindTo
import eu.kanade.tachiyomi.ui.setting.defaultValue
import eu.kanade.tachiyomi.ui.setting.dropDownPreference
import eu.kanade.tachiyomi.ui.setting.infoPreference
import eu.kanade.tachiyomi.ui.setting.intListPreference
import eu.kanade.tachiyomi.ui.setting.onChange
import eu.kanade.tachiyomi.ui.setting.preferenceCategory
import eu.kanade.tachiyomi.ui.setting.switchPreference
import eu.kanade.tachiyomi.ui.setting.themePreference
import eu.kanade.tachiyomi.util.lang.addBetaTag
import eu.kanade.tachiyomi.util.system.SideNavMode
import eu.kanade.tachiyomi.util.system.appDelegateNightMode
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getPrefTheme
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.setAppIcon
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.moveRecyclerViewUp
import kotlin.math.max
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import yokai.domain.base.BasePreferences
import yokai.i18n.MR
import yokai.util.lang.getString
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys
import eu.kanade.tachiyomi.ui.setting.summaryMRes as summaryRes
import eu.kanade.tachiyomi.ui.setting.titleMRes as titleRes

class SettingsAppearanceController : SettingsLegacyController() {

    var lastThemeXLight: Int? = null
    var lastThemeXDark: Int? = null
    var themePreference: ThemePreference? = null

    @SuppressLint("NotifyDataSetChanged")
    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = MR.strings.appearance

        preferenceCategory {
            titleRes = MR.strings.app_theme

            themePreference = themePreference {
                key = "theme_preference"
                titleRes = MR.strings.app_theme
                lastScrollPostionLight = lastThemeXLight
                lastScrollPostionDark = lastThemeXDark
                summary = context.getString(context.getPrefTheme(preferences).nameRes)
                activity = this@SettingsAppearanceController.activity
            }

            // FIXME: Don't use dropdown, use something similar to Theme
            dropDownPreference {
                bindTo(basePreferences.appIcon())
                title = "Change App Icon".addBetaTag(context)
                summary = "This feature is still very experimental!"

                val values = BasePreferences.AppIcons.entries.toList()
                entries = values.map { it.displayName }.toTypedArray()
                entryValues = values.map { it.name }.toTypedArray()

                onChange {
                    it as String
                    context.materialAlertDialog()
                        .setTitle("Change App Icon?")
                        .setMessage("[Very Experimental] This may kill the app!")
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            value = it
                            context.setAppIcon(basePreferences, BasePreferences.AppIcons.valueOf(it))
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                    false
                }
            }

            switchPreference {
                key = "night_mode_switch"
                isPersistent = false
                titleRes = MR.strings.follow_system_theme
                isChecked =
                    preferences.nightMode().get() == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

                onChange {
                    if (it == true) {
                        preferences.nightMode().set(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                        activity?.recreate()
                    } else {
                        preferences.nightMode().set(context.appDelegateNightMode())
                        themePreference?.fastAdapterLight?.notifyDataSetChanged()
                        themePreference?.fastAdapterDark?.notifyDataSetChanged()
                    }
                    true
                }
                preferences.nightMode().changes().onEach { mode ->
                    isChecked = mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }.launchIn(viewScope)
            }

            switchPreference {
                key = Keys.themeDarkAmoled
                titleRes = MR.strings.pure_black_dark_mode
                defaultValue = false

                preferences.nightMode().changesIn(viewScope) { mode ->
                    isVisible = mode != AppCompatDelegate.MODE_NIGHT_NO
                }

                onChange {
                    if (context.isInNightMode()) {
                        activity?.recreate()
                    } else {
                        themePreference?.fastAdapterDark?.notifyDataSetChanged()
                    }
                    true
                }
            }
        }

        preferenceCategory {
            switchPreference {
                bindTo(preferences.useLargeToolbar())
                titleRes = MR.strings.expanded_toolbar
                summaryRes = MR.strings.show_larger_toolbar

                onChange {
                    val useLarge = it as Boolean
                    activityBinding?.appBar?.setToolbarModeBy(this@SettingsAppearanceController, !useLarge)
                    activityBinding?.appBar?.hideBigView(!useLarge, !useLarge)
                    activityBinding?.toolbar?.alpha = 1f
                    activityBinding?.toolbar?.translationY = 0f
                    activityBinding?.toolbar?.isVisible = true
                    activityBinding?.appBar?.doOnNextLayout {
                        listView.requestApplyInsets()
                        listView.post {
                            if (useLarge) {
                                moveRecyclerViewUp(true)
                            } else {
                                activityBinding?.appBar?.updateAppBarAfterY(listView)
                            }
                        }
                    }
                    true
                }
            }
        }

        preferenceCategory {
            titleRes = MR.strings.details_page
            switchPreference {
                key = Keys.themeMangaDetails
                titleRes = MR.strings.theme_buttons_based_on_cover
                defaultValue = true
            }
        }

        preferenceCategory {
            titleRes = MR.strings.navigation

            switchPreference {
                key = Keys.hideBottomNavOnScroll
                titleRes = MR.strings.hide_bottom_nav
                summaryRes = MR.strings.hides_on_scroll
                defaultValue = true
            }

            intListPreference(activity) {
                key = Keys.sideNavIconAlignment
                titleRes = MR.strings.side_nav_icon_alignment
                entriesRes = arrayOf(MR.strings.top, MR.strings.center, MR.strings.bottom)
                entryRange = 0..2
                defaultValue = 1
                isVisible = max(
                    context.resources.displayMetrics.widthPixels,
                    context.resources.displayMetrics.heightPixels,
                ) >= 720.dpToPx
            }

            intListPreference(activity) {
                key = Keys.sideNavMode
                titleRes = MR.strings.use_side_navigation
                val values = SideNavMode.entries
                entriesRes = values.map { it.stringRes }.toTypedArray()
                entryValues = values.map { it.prefValue }
                defaultValue = SideNavMode.DEFAULT.prefValue

                onChange {
                    activity?.recreate()
                    true
                }
            }

            infoPreference(MR.strings.by_default_side_nav_info)
        }
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        themePreference = null
    }

    override fun onSaveViewState(view: View, outState: Bundle) {
        outState.putInt(::lastThemeXLight.name, themePreference?.lastScrollPostionLight ?: 0)
        outState.putInt(::lastThemeXDark.name, themePreference?.lastScrollPostionDark ?: 0)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreViewState(view: View, savedViewState: Bundle) {
        super.onRestoreViewState(view, savedViewState)
        lastThemeXLight = savedViewState.getInt(::lastThemeXLight.name)
        lastThemeXDark = savedViewState.getInt(::lastThemeXDark.name)
        themePreference?.lastScrollPostionLight = lastThemeXLight
        themePreference?.lastScrollPostionDark = lastThemeXDark
    }
}
