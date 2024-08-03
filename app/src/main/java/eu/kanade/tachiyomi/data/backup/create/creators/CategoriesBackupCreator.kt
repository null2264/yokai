package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CategoriesBackupCreator(
    private val db: DatabaseHelper = Injekt.get(),
) {
    /**
     * Backup the categories of library
     *
     * @return list of [BackupCategory] to be backed up
     */
    operator fun invoke(options: BackupOptions): List<BackupCategory> {
        if (!options.categories) return emptyList()

        return db.getCategories()
            .executeAsBlocking()
            .map { BackupCategory.copyFrom(it) }
    }
}
