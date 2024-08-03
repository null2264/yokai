package eu.kanade.tachiyomi.data.backup.create

import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.persistentListOf
import yokai.i18n.MR

data class BackupOptions(
    val libraryEntries: Boolean = true,
    val categories: Boolean = true,
    val chapters: Boolean = true,
    val tracking: Boolean = true,
    val history: Boolean = true,
    val appPrefs: Boolean = true,
    val sourcePrefs: Boolean = true,
    val customInfo: Boolean = true,
    val readManga: Boolean = true,
    val includePrivate: Boolean = false,
) {
    fun asBooleanArray() = booleanArrayOf(
        libraryEntries,
        categories,
        chapters,
        tracking,
        history,
        appPrefs,
        sourcePrefs,
        customInfo,
        readManga,
        includePrivate,
    )

    companion object {
        fun getOptions() = persistentListOf(
            MR.strings.library_entries,
            MR.strings.categories,
            MR.strings.chapters,
            MR.strings.tracking,
            MR.strings.history,
            MR.strings.app_settings,
            MR.strings.source_settings,
            MR.strings.custom_manga_info,
            MR.strings.all_read_manga,
            MR.strings.backup_private_pref,
        )

        fun getEntries() = persistentListOf(
            Entry(
                label = MR.strings.library_entries,
                getter = BackupOptions::libraryEntries,
                setter = { options, enabled -> options.copy(libraryEntries = enabled) },
            ),
            Entry(
                label = MR.strings.chapters,
                getter = BackupOptions::chapters,
                setter = { options, enabled -> options.copy(chapters = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.tracking,
                getter = BackupOptions::tracking,
                setter = { options, enabled -> options.copy(tracking = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.history,
                getter = BackupOptions::history,
                setter = { options, enabled -> options.copy(history = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.custom_manga_info,
                getter = BackupOptions::customInfo,
                setter = { options, enabled -> options.copy(customInfo = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.all_read_manga,
                getter = BackupOptions::readManga,
                setter = { options, enabled -> options.copy(readManga = enabled) },
                enabled = { it.libraryEntries },
            ),
            Entry(
                label = MR.strings.categories,
                getter = BackupOptions::categories,
                setter = { options, enabled -> options.copy(categories = enabled) },
            ),
            Entry(
                label = MR.strings.app_settings,
                getter = BackupOptions::appPrefs,
                setter = { options, enabled -> options.copy(appPrefs = enabled) },
            ),
            Entry(
                label = MR.strings.source_settings,
                getter = BackupOptions::sourcePrefs,
                setter = { options, enabled -> options.copy(sourcePrefs = enabled) },
            ),
            Entry(
                label = MR.strings.backup_private_pref,
                getter = BackupOptions::includePrivate,
                setter = { options, enabled -> options.copy(includePrivate = enabled) },
            ),
        )

        fun fromBooleanArray(array: BooleanArray): BackupOptions = BackupOptions(
            array[0],
            array[1],
            array[2],
            array[3],
            array[4],
            array[5],
            array[6],
            array[7],
            array[8],
            array[9],
        )
    }

    data class Entry(
        val label: StringResource,
        val getter: (BackupOptions) -> Boolean,
        val setter: (BackupOptions, Boolean) -> BackupOptions,
        val enabled: (BackupOptions) -> Boolean = { true },
    )
}
