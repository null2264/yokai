package eu.kanade.tachiyomi.ui.setting.controllers

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.DEVICE_BATTERY_NOT_LOW
import eu.kanade.tachiyomi.data.preference.DEVICE_CHARGING
import eu.kanade.tachiyomi.data.preference.DEVICE_ONLY_ON_WIFI
import eu.kanade.tachiyomi.data.preference.DelayedLibrarySuggestionsJob
import eu.kanade.tachiyomi.data.preference.MANGA_HAS_UNREAD
import eu.kanade.tachiyomi.data.preference.MANGA_NON_COMPLETED
import eu.kanade.tachiyomi.data.preference.MANGA_NON_READ
import eu.kanade.tachiyomi.data.preference.changesIn
import eu.kanade.tachiyomi.ui.category.CategoryController
import eu.kanade.tachiyomi.ui.library.LibraryPresenter
import eu.kanade.tachiyomi.ui.library.display.TabbedLibraryDisplaySheet
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import eu.kanade.tachiyomi.ui.setting.bindTo
import eu.kanade.tachiyomi.ui.setting.defaultValue
import eu.kanade.tachiyomi.ui.setting.intListPreference
import eu.kanade.tachiyomi.ui.setting.multiSelectListPreferenceMat
import eu.kanade.tachiyomi.ui.setting.onChange
import eu.kanade.tachiyomi.ui.setting.onClick
import eu.kanade.tachiyomi.ui.setting.preference
import eu.kanade.tachiyomi.ui.setting.preferenceCategory
import eu.kanade.tachiyomi.ui.setting.switchPreference
import eu.kanade.tachiyomi.ui.setting.triStateListPreference
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.category.interactor.GetCategories
import yokai.domain.library.LibraryPreferences
import yokai.domain.manga.interactor.GetLibraryManga
import yokai.domain.ui.UiPreferences
import yokai.i18n.MR
import yokai.util.lang.getString
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys
import eu.kanade.tachiyomi.ui.setting.summaryMRes as summaryRes
import eu.kanade.tachiyomi.ui.setting.titleMRes as titleRes

class SettingsLibraryController : SettingsLegacyController() {

    private val getLibraryManga: GetLibraryManga by injectLazy()
    private val getCategories: GetCategories by injectLazy()

    private val uiPreferences: UiPreferences by injectLazy()
    private val libraryPreferences: LibraryPreferences by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = MR.strings.library
        preferenceCategory {
            titleRes = MR.strings.general
            switchPreference {
                key = Keys.removeArticles
                titleRes = MR.strings.sort_by_ignoring_articles
                summaryRes = MR.strings.when_sorting_ignore_articles
                defaultValue = false
            }

            switchPreference {
                key = Keys.showLibrarySearchSuggestions
                titleRes = MR.strings.search_suggestions
                summaryRes = MR.strings.search_tips_show_periodically

                onChange {
                    it as Boolean
                    if (it) {
                        launchIO {
                            LibraryPresenter.setSearchSuggestion(preferences, getLibraryManga, Injekt.get())
                        }
                    } else {
                        DelayedLibrarySuggestionsJob.setupTask(context, false)
                        preferences.librarySearchSuggestion().set("")
                    }
                    true
                }
            }

            preference {
                key = "library_display_options"
                isPersistent = false
                titleRes = MR.strings.display_options
                summaryRes = MR.strings.can_be_found_in_library_filters

                onClick {
                    TabbedLibraryDisplaySheet(this@SettingsLibraryController).show()
                }
            }
        }

        // FIXME: Don't do blocking
        val dbCategories = runBlocking { getCategories.await() }

        preferenceCategory {
            titleRes = MR.strings.categories
            preference {
                key = "edit_categories"
                isPersistent = false
                val catCount = dbCategories.size
                titleRes = if (catCount > 0) MR.strings.edit_categories else MR.strings.add_categories
                if (catCount > 0) summary = context.getString(MR.plurals.category_plural, catCount, catCount)
                onClick { router.pushController(CategoryController().withFadeTransaction()) }
            }
            intListPreference(activity) {
                key = Keys.defaultCategory
                titleRes = MR.strings.default_category

                val categories = listOf(Category.createDefault(context)) + dbCategories
                entries =
                    listOf(context.getString(MR.strings.last_used), context.getString(MR.strings.always_ask)) +
                        categories.map { it.name }.toTypedArray()
                entryValues = listOf(-2, -1) + categories.mapNotNull { it.id }.toList()
                defaultValue = "-2"

                val categoryName: (Int) -> String = { catId ->
                    when (catId) {
                        -2 -> context.getString(MR.strings.last_used)
                        -1 -> context.getString(MR.strings.always_ask)
                        else -> categories.find { it.id == preferences.defaultCategory().get() }?.name
                            ?: context.getString(MR.strings.last_used)
                    }
                }
                summary = categoryName(preferences.defaultCategory().get())
                onChange { newValue ->
                    summary = categoryName(newValue as Int)
                    true
                }
            }
        }

        preferenceCategory {
            titleRes = MR.strings.global_updates
            intListPreference(activity) {
                key = Keys.libraryUpdateInterval
                titleRes = MR.strings.library_update_frequency
                entriesRes = arrayOf(
                    MR.strings.manual,
                    MR.strings.every_12_hours,
                    MR.strings.daily,
                    MR.strings.every_2_days,
                    MR.strings.every_3_days,
                    MR.strings.weekly,
                )
                entryValues = listOf(0, 12, 24, 48, 72, 168)
                defaultValue = 24

                onChange { newValue ->
                    // Always cancel the previous task, it seems that sometimes they are not updated.
                    LibraryUpdateJob.setupTask(context, 0)

                    val interval = newValue as Int
                    if (interval > 0) {
                        (activity as? MainActivity)?.showNotificationPermissionPrompt(true)
                        LibraryUpdateJob.setupTask(context, interval)
                    }
                    true
                }
            }
            multiSelectListPreferenceMat(activity) {
                bindTo(preferences.libraryUpdateDeviceRestriction())
                titleRes = MR.strings.library_update_restriction
                entriesRes = arrayOf(MR.strings.wifi, MR.strings.charging, MR.strings.battery_not_low)
                entryValues = listOf(DEVICE_ONLY_ON_WIFI, DEVICE_CHARGING, DEVICE_BATTERY_NOT_LOW)
                preSummaryRes = MR.strings.restrictions_
                noSelectionRes = MR.strings.none

                preferences.libraryUpdateInterval().changesIn(viewScope) {
                    isVisible = it > 0
                }

                onChange {
                    // Post to event looper to allow the preference to be updated.
                    viewScope.launchUI { LibraryUpdateJob.setupTask(context) }
                    true
                }
            }

            multiSelectListPreferenceMat(activity) {
                bindTo(preferences.libraryUpdateMangaRestriction())
                titleRes = MR.strings.pref_library_update_manga_restriction
                entriesRes = arrayOf(
                    MR.strings.pref_update_only_completely_read,
                    MR.strings.pref_update_only_started,
                    MR.strings.pref_update_only_non_completed,
                )
                entryValues = listOf(MANGA_HAS_UNREAD, MANGA_NON_READ, MANGA_NON_COMPLETED)
                noSelectionRes = MR.strings.none
            }

            triStateListPreference(activity) {
                preferences.apply {
                    bindTo(libraryUpdateCategories(), libraryUpdateCategoriesExclude())
                }
                titleRes = MR.strings.categories

                val categories = listOf(Category.createDefault(context)) + dbCategories
                entries = categories.map { it.name }
                entryValues = categories.map { it.id.toString() }

                allSelectionRes = MR.strings.all
            }

            switchPreference {
                key = Keys.refreshCoversToo
                titleRes = MR.strings.auto_refresh_covers
                summaryRes = MR.strings.auto_refresh_covers_summary
                defaultValue = true
            }
        }

        preferenceCategory {
            titleRes = MR.strings.pref_behavior

            switchPreference {
                bindTo(uiPreferences.enableChapterSwipeAction())
                titleRes = MR.strings.enable_chapter_swipe_action
            }
            multiSelectListPreferenceMat(activity) {
                bindTo(libraryPreferences.markDuplicateReadChapterAsRead())
                titleRes = MR.strings.pref_mark_as_read_duplicate_read_chapter
                val entries = mapOf(
                    MR.strings.pref_mark_as_read_duplicate_read_chapter_existing to
                        LibraryPreferences.MARK_DUPLICATE_READ_CHAPTER_READ_EXISTING,
                    MR.strings.pref_mark_as_read_duplicate_read_chapter_new to
                        LibraryPreferences.MARK_DUPLICATE_READ_CHAPTER_READ_NEW,
                )
                entriesRes = entries.keys.toTypedArray()
                entryValues = entries.values.toList()
                noSelectionRes = MR.strings.none
            }
        }
    }
}
