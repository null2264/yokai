package yokai.domain.ui

import eu.kanade.tachiyomi.core.preference.PreferenceStore
import eu.kanade.tachiyomi.data.preference.PreferenceKeys

class UiPreferences(private val preferenceStore: PreferenceStore) {
    fun recentsViewType() = preferenceStore.getInt("recents_view_type", 0)

    fun outlineOnCovers() = preferenceStore.getBoolean(PreferenceKeys.outlineOnCovers, true)

    fun uniformGrid() = preferenceStore.getBoolean(PreferenceKeys.uniformGrid, true)

    fun enableChapterSwipeAction() = preferenceStore.getBoolean("enable_chapter_swipe_action", true)

    fun enableSourceSwipeAction() = preferenceStore.getBoolean("enable_source_swipe_action", true)
}
