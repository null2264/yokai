package dev.yokai.data.sync.models

import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R
import kotlinx.collections.immutable.persistentListOf

data class SyncTriggerOptions(
    val syncOnChapterRead: Boolean = false,
    val syncOnChapterOpen: Boolean = false,
    val syncOnAppStart: Boolean = false,
    val syncOnAppResume: Boolean = false,
    val syncOnLibraryUpdate: Boolean = false,
) {
    fun asBooleanArray() = booleanArrayOf(
        syncOnChapterRead,
        syncOnChapterOpen,
        syncOnAppStart,
        syncOnAppResume,
        syncOnLibraryUpdate,
    )

    fun anyEnabled() = syncOnChapterRead ||
        syncOnChapterOpen ||
        syncOnAppStart ||
        syncOnAppResume ||
        syncOnLibraryUpdate

    companion object {
        val mainOptions = persistentListOf(
            Entry(
                label = R.string.sync_on_chapter_read,
                getter = SyncTriggerOptions::syncOnChapterRead,
                setter = { options, enabled -> options.copy(syncOnChapterRead = enabled) },
            ),
            Entry(
                label = R.string.sync_on_chapter_open,
                getter = SyncTriggerOptions::syncOnChapterOpen,
                setter = { options, enabled -> options.copy(syncOnChapterOpen = enabled) },
            ),
            Entry(
                label = R.string.sync_on_app_start,
                getter = SyncTriggerOptions::syncOnAppStart,
                setter = { options, enabled -> options.copy(syncOnAppStart = enabled) },
            ),
            Entry(
                label = R.string.sync_on_app_resume,
                getter = SyncTriggerOptions::syncOnAppResume,
                setter = { options, enabled -> options.copy(syncOnAppResume = enabled) },
            ),
            Entry(
                label = R.string.sync_on_library_update,
                getter = SyncTriggerOptions::syncOnLibraryUpdate,
                setter = { options, enabled -> options.copy(syncOnLibraryUpdate = enabled) },
            ),
        )

        fun fromBooleanArray(array: BooleanArray) = SyncTriggerOptions(
            syncOnChapterRead = array[0],
            syncOnChapterOpen = array[1],
            syncOnAppStart = array[2],
            syncOnAppResume = array[3],
            syncOnLibraryUpdate = array[4],
        )
    }

    data class Entry(
        @StringRes val label: Int,
        val getter: (SyncTriggerOptions) -> Boolean,
        val setter: (SyncTriggerOptions, Boolean) -> SyncTriggerOptions,
        val enabled: (SyncTriggerOptions) -> Boolean = { true },
    )
}
