package eu.kanade.tachiyomi.ui.category

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.core.preference.Preference
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.MangaCategoryDialogBinding
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.library.LibrarySort
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.view.setPositiveButton
import eu.kanade.tachiyomi.util.view.setTitle
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import eu.kanade.tachiyomi.widget.TriStateCheckBox
import kotlinx.coroutines.runBlocking
import uy.kohesive.injekt.injectLazy
import yokai.domain.category.interactor.GetCategories
import yokai.domain.category.interactor.InsertCategories
import yokai.i18n.MR
import yokai.util.lang.getString
import android.R as AR

class ManageCategoryDialog(bundle: Bundle? = null) :
    DialogController(bundle) {

    constructor(category: Category?, updateLibrary: ((Long?) -> Unit)) : this() {
        this.updateLibrary = updateLibrary
        this.category = category
    }

    private var updateLibrary: ((Long?) -> Unit)? = null
    private var category: Category? = null

    private val preferences by injectLazy<PreferencesHelper>()
    private val getCategories by injectLazy<GetCategories>()
    private val insertCategories by injectLazy<InsertCategories>()

    lateinit var binding: MangaCategoryDialogBinding

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        val dialog = dialog(activity!!).create()
        onViewCreated()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
                if (onPositiveButtonClick()) {
                    dialog.dismiss()
                }
            }
        }
        return dialog
    }

    fun dialog(activity: Activity): MaterialAlertDialogBuilder {
        return activity.materialAlertDialog().apply {
            setTitle(if (category == null) MR.strings.new_category else MR.strings.manage_category)
            binding = MangaCategoryDialogBinding.inflate(activity.layoutInflater)
            setView(binding.root)
            setNegativeButton(AR.string.cancel, null)
            setPositiveButton(MR.strings.save) { dialog, _ ->
                if (onPositiveButtonClick()) {
                    dialog.dismiss()
                }
            }
        }
    }

    fun show(activity: Activity) {
        val dialog = dialog(activity).create()
        onViewCreated()
        dialog.setOnShowListener {
            binding.title.requestFocus()
        }
        dialog.show()
    }

    private fun onPositiveButtonClick(): Boolean {
        val text = binding.title.text.toString()
        val categoryExists = categoryExists(text)
        val category = this.category ?: Category.create(text)
        if (category.id != 0L) {
            if (text.isNotBlank() && !categoryExists &&
                !text.equals(this.category?.name ?: "", true)
            ) {
                category.name = text
                if (this.category == null) {
                    // FIXME: Don't do blocking
                    val categories = runBlocking { getCategories.await() }
                    category.order = (categories.maxOfOrNull { it.order } ?: 0) + 1
                    category.mangaSort = LibrarySort.Title.categoryValue
                    category.id = runBlocking { insertCategories.awaitOne(category) }
                    this.category = category
                } else {
                    runBlocking { insertCategories.awaitOne(category) }
                }
            } else if (categoryExists) {
                binding.categoryTextLayout.error =
                    binding.categoryTextLayout.context.getString(MR.strings.category_with_name_exists)
                return false
            } else if (text.isBlank()) {
                binding.categoryTextLayout.error =
                    binding.categoryTextLayout.context.getString(MR.strings.category_cannot_be_blank)
                return false
            }
        }
        when (
            updatePref(
                preferences.downloadNewChaptersInCategories(),
                preferences.excludeCategoriesInDownloadNew(),
                binding.downloadNew,
            )
        ) {
            true -> preferences.downloadNewChapters().set(true)
            false -> preferences.downloadNewChapters().set(false)
            else -> {}
        }
        if (preferences.libraryUpdateInterval().get() > 0 &&
            updatePref(
                    preferences.libraryUpdateCategories(),
                    preferences.libraryUpdateCategoriesExclude(),
                    binding.includeGlobal,
                ) == false
        ) {
            preferences.libraryUpdateInterval().set(0)
            LibraryUpdateJob.setupTask(preferences.context, 0)
        }
        updateLibrary?.invoke(category.id)
        return true
    }

    /**
     * Returns true if a category with the given name already exists.
     */
    private fun categoryExists(name: String): Boolean {
        // FIXME: Don't do blocking
        return runBlocking { getCategories.await() }.any {
            it.name.equals(name, true) && category?.id != it.id
        }
    }

    fun onViewCreated() {
        if ((category?.id ?: 0) <= 0 && category != null) {
            binding.categoryTextLayout.isVisible = false
        }
        binding.editCategories.isVisible = category != null
        binding.editCategories.setOnClickListener {
            router.popCurrentController()
            router.pushController(CategoryController().withFadeTransaction())
        }
        binding.title.addTextChangedListener {
            binding.categoryTextLayout.error = null
        }
        binding.title.hint =
            category?.name ?: binding.editCategories.context.getString(MR.strings.category)
        binding.title.append(category?.name ?: "")
        val downloadNew = preferences.downloadNewChapters().get()
        setCheckbox(
            binding.downloadNew,
            preferences.downloadNewChaptersInCategories(),
            preferences.excludeCategoriesInDownloadNew(),
            true,
        )
        if (downloadNew && preferences.downloadNewChaptersInCategories().get().isEmpty()) {
            binding.downloadNew.isVisible = false
        } else if (!downloadNew) {
            binding.downloadNew.isVisible = true
        }
        if (!downloadNew) {
            binding.downloadNew.isChecked = false
        }
        setCheckbox(
            binding.includeGlobal,
            preferences.libraryUpdateCategories(),
            preferences.libraryUpdateCategoriesExclude(),
            preferences.libraryUpdateInterval().get() > 0,
        )
    }

    /** Update a pref based on checkbox, and return if the pref is not empty */
    private fun updatePref(
        categories: Preference<Set<String>>,
        excludeCategories: Preference<Set<String>>,
        box: TriStateCheckBox,
    ): Boolean? {
        val categoryId = category?.id ?: return null
        if (!box.isVisible) return null
        val updateCategories = categories.get().toMutableSet()
        val excludeUpdateCategories = excludeCategories.get().toMutableSet()
        when (box.state) {
            TriStateCheckBox.State.CHECKED -> {
                updateCategories.add(categoryId.toString())
                excludeUpdateCategories.remove(categoryId.toString())
            }
            TriStateCheckBox.State.IGNORE -> {
                updateCategories.remove(categoryId.toString())
                excludeUpdateCategories.add(categoryId.toString())
            }
            TriStateCheckBox.State.UNCHECKED -> {
                updateCategories.remove(categoryId.toString())
                excludeUpdateCategories.remove(categoryId.toString())
            }
        }
        categories.set(updateCategories)
        excludeCategories.set(excludeUpdateCategories)
        return updateCategories.isNotEmpty()
    }

    private fun setCheckbox(
        box: TriStateCheckBox,
        categories: Preference<Set<String>>,
        excludeCategories: Preference<Set<String>>,
        shouldShow: Boolean,
    ) {
        val updateCategories = categories.get()
        val excludeUpdateCategories = excludeCategories.get()
        box.isVisible = (updateCategories.isNotEmpty() || excludeUpdateCategories.isNotEmpty()) && shouldShow
        if (shouldShow) {
            box.state = when {
                updateCategories.any { category?.id == it.toLongOrNull() } -> TriStateCheckBox.State.CHECKED
                excludeUpdateCategories.any { category?.id == it.toLongOrNull() } -> TriStateCheckBox.State.IGNORE
                else -> TriStateCheckBox.State.UNCHECKED
            }
        }
    }
}
