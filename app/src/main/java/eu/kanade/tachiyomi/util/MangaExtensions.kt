package eu.kanade.tachiyomi.util

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import co.touchlab.kermit.Logger
import com.bluelinelabs.conductor.Controller
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.MangaCategory
import eu.kanade.tachiyomi.data.database.models.seriesType
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.EnhancedTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.category.addtolibrary.SetCategoriesSheet
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.migration.MigrationFlags
import eu.kanade.tachiyomi.ui.migration.manga.process.MigrationProcessAdapter
import eu.kanade.tachiyomi.util.chapter.syncChaptersWithTrackServiceTwoWay
import eu.kanade.tachiyomi.util.lang.asButton
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.setCustomTitleAndMessage
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.util.view.setAction
import eu.kanade.tachiyomi.util.view.setPositiveButton
import eu.kanade.tachiyomi.util.view.setTitle
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import eu.kanade.tachiyomi.widget.TriStateCheckBox
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.chapter.interactor.GetChapter
import yokai.domain.manga.interactor.GetManga
import yokai.i18n.MR
import yokai.util.lang.getString
import android.R as AR

fun Manga.isLocal() = source == LocalSource.ID

fun Manga.shouldDownloadNewChapters(db: DatabaseHelper, prefs: PreferencesHelper): Boolean {
    if (!favorite) return false

    // Boolean to determine if user wants to automatically download new chapters.
    val downloadNewChapters = prefs.downloadNewChapters().get()
    if (!downloadNewChapters) return false

    val includedCategories = prefs.downloadNewChaptersInCategories().get().map(String::toInt)
    val excludedCategories = prefs.excludeCategoriesInDownloadNew().get().map(String::toInt)
    if (includedCategories.isEmpty() && excludedCategories.isEmpty()) return true

    // Get all categories, else default category (0)
    val categoriesForManga =
        db.getCategoriesForManga(this).executeAsBlocking()
            .mapNotNull { it.id }
            .takeUnless { it.isEmpty() } ?: listOf(0)

    if (categoriesForManga.any { it in excludedCategories }) return false

    // Included category not selected
    if (includedCategories.isEmpty()) return true

    return categoriesForManga.any { it in includedCategories }
}

fun Manga.moveCategories(db: DatabaseHelper, activity: Activity, onMangaMoved: () -> Unit) {
    moveCategories(db, activity, false, onMangaMoved)
}

fun Manga.moveCategories(
    db: DatabaseHelper,
    activity: Activity,
    addingToLibrary: Boolean,
    onMangaMoved: () -> Unit,
) {
    val categories = db.getCategories().executeAsBlocking()
    val categoriesForManga = db.getCategoriesForManga(this).executeAsBlocking()
    val ids = categoriesForManga.mapNotNull { it.id }.toTypedArray()
    SetCategoriesSheet(
        activity,
        this,
        categories.toMutableList(),
        ids,
        addingToLibrary,
    ) {
        onMangaMoved()
        if (addingToLibrary) {
            autoAddTrack(db, onMangaMoved)
        }
    }.show()
}

fun List<Manga>.moveCategories(
    db: DatabaseHelper,
    activity: Activity,
    onMangaMoved: () -> Unit,
) {
    if (this.isEmpty()) return
    val categories = db.getCategories().executeAsBlocking()
    val commonCategories = map { db.getCategoriesForManga(it).executeAsBlocking() }
        .reduce { set1: Iterable<Category>, set2 -> set1.intersect(set2).toMutableList() }
        .toTypedArray()
    val mangaCategories = map { db.getCategoriesForManga(it).executeAsBlocking() }
    val common = mangaCategories.reduce { set1, set2 -> set1.intersect(set2).toMutableList() }
    val mixedCategories = mangaCategories.flatten().distinct().subtract(common).toMutableList()
    SetCategoriesSheet(
        activity,
        this,
        categories.toMutableList(),
        categories.map {
            when (it) {
                in commonCategories -> TriStateCheckBox.State.CHECKED
                in mixedCategories -> TriStateCheckBox.State.IGNORE
                else -> TriStateCheckBox.State.UNCHECKED
            }
        }.toTypedArray(),
        false,
    ) {
        onMangaMoved()
    }.show()
}

fun Manga.addOrRemoveToFavorites(
    db: DatabaseHelper,
    preferences: PreferencesHelper,
    view: View,
    activity: Activity,
    sourceManager: SourceManager,
    controller: Controller,
    checkForDupes: Boolean = true,
    onMangaAdded: (Pair<Long, Boolean>?) -> Unit,
    onMangaMoved: () -> Unit,
    onMangaDeleted: () -> Unit,
    getManga: GetManga = Injekt.get()
): Snackbar? {
    if (!favorite) {
        if (checkForDupes) {
            val duplicateManga = runBlocking(Dispatchers.IO) {
                getManga.awaitDuplicateFavorite(
                    this@addOrRemoveToFavorites.title,
                    this@addOrRemoveToFavorites.source,
                )
            }
            if (duplicateManga != null) {
                showAddDuplicateDialog(
                    this,
                    duplicateManga,
                    activity,
                    db,
                    sourceManager,
                    controller,
                    addManga = {
                        addOrRemoveToFavorites(
                            db,
                            preferences,
                            view,
                            activity,
                            sourceManager,
                            controller,
                            false,
                            onMangaAdded,
                            onMangaMoved,
                            onMangaDeleted,
                        )
                    },
                    migrateManga = { source, faved ->
                        onMangaAdded(source to faved)
                    },
                )
                return null
            }
        }

        val categories = db.getCategories().executeAsBlocking()
        val defaultCategoryId = preferences.defaultCategory().get()
        val defaultCategory = categories.find { it.id == defaultCategoryId }
        val lastUsedCategories = Category.lastCategoriesAddedTo.mapNotNull { catId ->
            categories.find { it.id == catId }
        }
        when {
            defaultCategory != null -> {
                favorite = true
                date_added = Date().time
                autoAddTrack(db, onMangaMoved)
                db.insertManga(this).executeAsBlocking()
                val mc = MangaCategory.create(this, defaultCategory)
                db.setMangaCategories(listOf(mc), listOf(this))
                (activity as? MainActivity)?.showNotificationPermissionPrompt()
                onMangaMoved()
                return view.snack(activity.getString(MR.strings.added_to_, defaultCategory.name)) {
                    setAction(MR.strings.change) {
                        moveCategories(db, activity, onMangaMoved)
                    }
                }
            }
            defaultCategoryId == -2 && (
                lastUsedCategories.isNotEmpty() ||
                    Category.lastCategoriesAddedTo.firstOrNull() == 0
                ) -> { // last used category(s)
                favorite = true
                date_added = Date().time
                autoAddTrack(db, onMangaMoved)
                db.insertManga(this).executeAsBlocking()
                db.setMangaCategories(
                    lastUsedCategories.map { MangaCategory.create(this, it) },
                    listOf(this),
                )
                (activity as? MainActivity)?.showNotificationPermissionPrompt()
                onMangaMoved()
                return view.snack(
                    activity.getString(
                        MR.strings.added_to_,
                        when (lastUsedCategories.size) {
                            0 -> activity.getString(MR.strings.default_category).lowercase(Locale.ROOT)
                            1 -> lastUsedCategories.firstOrNull()?.name ?: ""
                            else -> activity.getString(
                                MR.plurals.category_plural,
                                lastUsedCategories.size,
                                lastUsedCategories.size,
                            )
                        },
                    ),
                ) {
                    setAction(MR.strings.change) {
                        moveCategories(db, activity, onMangaMoved)
                    }
                }
            }
            defaultCategoryId == 0 || categories.isEmpty() -> { // 'Default' or no category
                favorite = true
                date_added = Date().time
                autoAddTrack(db, onMangaMoved)
                db.insertManga(this).executeAsBlocking()
                db.setMangaCategories(emptyList(), listOf(this))
                onMangaMoved()
                (activity as? MainActivity)?.showNotificationPermissionPrompt()
                return if (categories.isNotEmpty()) {
                    view.snack(activity.getString(MR.strings.added_to_, activity.getString(MR.strings.default_value))) {
                        setAction(MR.strings.change) {
                            moveCategories(db, activity, onMangaMoved)
                        }
                    }
                } else {
                    view.snack(MR.strings.added_to_library)
                }
            }
            else -> { // Always ask
                showSetCategoriesSheet(db, activity, categories, onMangaAdded, onMangaMoved)
            }
        }
    } else {
        val lastAddedDate = date_added
        favorite = false
        date_added = 0
        db.insertManga(this).executeAsBlocking()
        onMangaMoved()
        return view.snack(view.context.getString(MR.strings.removed_from_library), Snackbar.LENGTH_INDEFINITE) {
            setAction(MR.strings.undo) {
                favorite = true
                date_added = lastAddedDate
                db.insertManga(this@addOrRemoveToFavorites).executeAsBlocking()
                onMangaMoved()
            }
            addCallback(
                object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        super.onDismissed(transientBottomBar, event)
                        if (!favorite) {
                            onMangaDeleted()
                        }
                    }
                },
            )
        }
    }
    return null
}

private fun Manga.showSetCategoriesSheet(
    db: DatabaseHelper,
    activity: Activity,
    categories: List<Category>,
    onMangaAdded: (Pair<Long, Boolean>?) -> Unit,
    onMangaMoved: () -> Unit,
) {
    val categoriesForManga = db.getCategoriesForManga(this).executeAsBlocking()
    val ids = categoriesForManga.mapNotNull { it.id }.toTypedArray()

    SetCategoriesSheet(
        activity,
        this,
        categories.toMutableList(),
        ids,
        true,
    ) {
        (activity as? MainActivity)?.showNotificationPermissionPrompt()
        onMangaAdded(null)
        autoAddTrack(db, onMangaMoved)
    }.show()
}

private fun showAddDuplicateDialog(
    newManga: Manga,
    libraryManga: Manga,
    activity: Activity,
    db: DatabaseHelper,
    sourceManager: SourceManager,
    controller: Controller,
    addManga: () -> Unit,
    migrateManga: (Long, Boolean) -> Unit,
) {
    val source = sourceManager.getOrStub(libraryManga.source)

    val titles by lazy { MigrationFlags.titles(activity, libraryManga) }
    fun migrateManga(mDialog: DialogInterface, replace: Boolean) {
        val listView = (mDialog as AlertDialog).listView
        val enabled = titles.indices.map { listView.isItemChecked(it) }.toTypedArray()
        val flags = MigrationFlags.getFlagsFromPositions(enabled, libraryManga)
        val enhancedServices by lazy { Injekt.get<TrackManager>().services.filterIsInstance<EnhancedTrackService>() }
        MigrationProcessAdapter.migrateMangaInternal(
            flags,
            db,
            enhancedServices,
            Injekt.get(),
            Injekt.get(),
            source,
            sourceManager.getOrStub(newManga.source),
            libraryManga,
            newManga,
            replace,
        )
        migrateManga(libraryManga.source, !replace)
    }

    activity.materialAlertDialog().apply {
        setCustomTitleAndMessage(0, activity.getString(MR.strings.confirm_manga_add_duplicate, source.name))
        setItems(
            arrayOf(
                activity.getString(MR.strings.show_, libraryManga.seriesType(activity, sourceManager)).asButton(activity),
                activity.getString(MR.strings.add_to_library).asButton(activity),
                activity.getString(MR.strings.migrate).asButton(activity, !newManga.initialized),
            ),
        ) { dialog, i ->
            when (i) {
                0 -> controller.router.pushController(
                    MangaDetailsController(libraryManga)
                        .withFadeTransaction(),
                )
                1 -> addManga()
                2 -> {
                    if (!newManga.initialized) {
                        activity.toast(MR.strings.must_view_details_before_migration, Toast.LENGTH_LONG)
                        return@setItems
                    }
                    activity.materialAlertDialog().apply {
                        setTitle(MR.strings.migration)
                        setMultiChoiceItems(
                            titles,
                            titles.map { true }.toBooleanArray(),
                            null,
                        )
                        setPositiveButton(MR.strings.migrate) { mDialog, _ ->
                            migrateManga(mDialog, true)
                        }
                        setNegativeButton(AR.string.copy) { mDialog, _ ->
                            migrateManga(mDialog, false)
                        }
                        setNeutralButton(AR.string.cancel, null)
                        setCancelable(true)
                    }.show()
                }
                else -> {}
            }
            dialog.dismiss()
        }
        setNegativeButton(activity.getString(AR.string.cancel)) { _, _ -> }
        setCancelable(true)
    }.create().apply {
        setOnShowListener {
            if (!newManga.initialized) {
                val listView = (it as AlertDialog).listView
                val view = listView.getChildAt(2)
                view?.setOnClickListener {
                    if (!newManga.initialized) {
                        activity.toast(
                            MR.strings.must_view_details_before_migration,
                            Toast.LENGTH_LONG,
                        )
                    }
                }
            }
        }
    }.show()
}

fun Manga.autoAddTrack(db: DatabaseHelper, onMangaMoved: () -> Unit) {
    val loggedServices = Injekt.get<TrackManager>().services.filter { it.isLogged }
    val source = Injekt.get<SourceManager>().getOrStub(this.source)
    val getChapter = Injekt.get<GetChapter>()
    loggedServices
        .filterIsInstance<EnhancedTrackService>()
        .filter { it.accept(source) }
        .forEach { service ->
            launchIO {
                try {
                    service.match(this@autoAddTrack)?.let { track ->
                        val mangaId = this@autoAddTrack.id!!
                        track.manga_id = mangaId
                        (service as TrackService).bind(track)
                        db.insertTrack(track).executeAsBlocking()

                        syncChaptersWithTrackServiceTwoWay(db, getChapter.awaitAll(mangaId, false), track, service as TrackService)
                        withUIContext {
                            onMangaMoved()
                        }
                    }
                } catch (e: Exception) {
                    Logger.w(e) { "Could not match manga: ${this@autoAddTrack.title} with service $service" }
                }
            }
        }
}

fun Context.mapStatus(status: Int): String {
    return getString(
        when (status) {
            SManga.ONGOING -> MR.strings.ongoing
            SManga.COMPLETED -> MR.strings.completed
            SManga.LICENSED -> MR.strings.licensed
            SManga.PUBLISHING_FINISHED -> MR.strings.publishing_finished
            SManga.CANCELLED -> MR.strings.cancelled
            SManga.ON_HIATUS -> MR.strings.on_hiatus
            else -> MR.strings.unknown
        },
    )
}

fun Context.mapSeriesType(seriesType: Int): String {
    return getString(
        when (seriesType) {
            Manga.TYPE_MANGA -> MR.strings.manga
            Manga.TYPE_MANHWA -> MR.strings.manhwa
            Manga.TYPE_MANHUA -> MR.strings.manhua
            Manga.TYPE_COMIC -> MR.strings.comic
            Manga.TYPE_WEBTOON -> MR.strings.webtoon
            else -> MR.strings.unknown
        },
    )
}
