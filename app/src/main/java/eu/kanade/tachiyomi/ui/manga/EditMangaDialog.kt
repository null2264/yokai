package eu.kanade.tachiyomi.ui.manga

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import androidx.core.view.isVisible
import coil3.load
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.image.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.data.image.coil.loadManga
import eu.kanade.tachiyomi.databinding.EditMangaDialogBinding
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.icon
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.widget.TachiyomiTextInputEditText
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class EditMangaDialog : DialogController {

    private val manga: Manga

    private var customCoverUri: Uri? = null

    private var willResetCover = false

    lateinit var binding: EditMangaDialogBinding
    private val languages = mutableListOf<String>()

    private val infoController
        get() = targetController as MangaDetailsController

    constructor(target: MangaDetailsController, manga: Manga) : super(
        Bundle()
            .apply {
                putLong(KEY_MANGA, manga.id!!)
            },
    ) {
        targetController = target
        this.manga = manga
    }

    @Suppress("unused")
    constructor(bundle: Bundle) : super(bundle) {
        manga = Injekt.get<DatabaseHelper>().getManga(bundle.getLong(KEY_MANGA))
            .executeAsBlocking()!!
    }

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        binding = EditMangaDialogBinding.inflate(activity!!.layoutInflater)
        val dialog = activity!!.materialAlertDialog().apply {
            setView(binding.root)
            setNegativeButton(android.R.string.cancel, null)
            setPositiveButton(R.string.save) { _, _ -> onPositiveButtonClick() }
        }
        onViewCreated()
        val updateScrollIndicators = {
            binding.scrollIndicatorDown.isVisible = binding.scrollView.canScrollVertically(1)
        }
        binding.scrollView.setOnScrollChangeListener { _, _, _, _, _ ->
            updateScrollIndicators()
        }
        binding.scrollView.post {
            updateScrollIndicators()
        }
        return dialog.create()
    }

    fun onViewCreated() {
        binding.mangaCover.loadManga(manga)
        val isLocal = manga.isLocal()

        binding.mangaLang.isVisible = isLocal
        if (isLocal) {
            if (manga.title != manga.url) {
                binding.title.append(manga.title)
            }
            binding.title.hint = "${resources?.getString(R.string.title)}: ${manga.url}"
            binding.mangaAuthor.append(manga.author ?: "")
            binding.mangaArtist.append(manga.artist ?: "")
            binding.mangaDescription.append(manga.description ?: "")
            val preferences = infoController.presenter.preferences
            val extensionManager: ExtensionManager by injectLazy()
            val activeLangs = preferences.enabledLanguages().get()

            languages.add("")
            languages.addAll(
                extensionManager.availableExtensionsFlow.value.groupBy { it.lang }.keys
                    .sortedWith(
                        compareBy(
                            { it !in activeLangs },
                            { LocaleHelper.getSourceDisplayName(it, binding.root.context) },
                        ),
                    )
                    .filter { it != "all" && it != "other" },
            )
            binding.mangaLang.setEntries(
                languages.map {
                    LocaleHelper.getSourceDisplayName(it, binding.root.context)
                },
            )
            binding.mangaLang.setSelection(
                languages.indexOf(LocalSource.getMangaLang(manga, binding.root.context))
                    .takeIf { it > -1 } ?: 0,
            )
        } else {
            if (manga.title != manga.originalTitle) {
                binding.title.append(manga.title)
            }
            if (manga.author != manga.originalAuthor) {
                binding.mangaAuthor.append(manga.author ?: "")
            }
            if (manga.artist != manga.originalArtist) {
                binding.mangaArtist.append(manga.artist ?: "")
            }
            if (manga.description != manga.originalDescription) {
                binding.mangaDescription.append(manga.description ?: "")
            }
            binding.title.appendOriginalTextOnLongClick(manga.originalTitle)
            binding.mangaAuthor.appendOriginalTextOnLongClick(manga.originalAuthor)
            binding.mangaArtist.appendOriginalTextOnLongClick(manga.originalArtist)
            binding.mangaDescription.appendOriginalTextOnLongClick(manga.originalDescription)
            binding.title.hint = "${resources?.getString(R.string.title)}: ${manga.originalTitle}"
            if (manga.originalAuthor != null) {
                binding.mangaAuthor.hint = "${resources?.getString(R.string.author)}: ${manga.originalAuthor}"
            }
            if (manga.originalArtist != null) {
                binding.mangaArtist.hint = "${resources?.getString(R.string.artist)}: ${manga.originalArtist}"
            }
            if (manga.originalDescription != null) {
                binding.mangaDescription.hint =
                    "${resources?.getString(R.string.description)}: ${manga.originalDescription?.replace(
                        "\n",
                        " ",
                    )?.chop(20)}"
            }
        }
        setGenreTags(manga.getGenres().orEmpty())
        if (!isLocal) {
            binding.mangaStatus.originalPosition = manga.originalStatus
            binding.seriesType.originalPosition = manga.seriesType(true) - 1
            infoController.presenter.source.icon()?.let { icon ->
                val bitD = ImageUtil.resizeBitMapDrawable(icon, resources, 24.dpToPx)
                binding.mangaStatus.originalIcon = bitD ?: icon
                binding.seriesType.originalIcon = bitD ?: icon
            }
        }
        binding.mangaStatus.setSelection(manga.status.coerceIn(SManga.UNKNOWN, SManga.ON_HIATUS))
        val oldType = manga.seriesType()
        binding.seriesType.setSelection(oldType - 1)
        binding.seriesType.onItemSelectedListener = {
            binding.resetsReadingMode.isVisible = it + 1 != oldType
        }
        binding.mangaGenresTags.clearFocus()
        binding.coverLayout.setOnClickListener {
            infoController.changeCover()
        }
        binding.resetTags.setOnClickListener { resetTags() }
        binding.resetTags.text = resources?.getString(
            if (manga.originalGenre.isNullOrBlank() || isLocal) {
                R.string.clear_tags
            } else {
                R.string.reset_tags
            },
        )
        binding.addTagChip.setOnClickListener {
            binding.addTagChip.isVisible = false
            binding.addTagEditText.isVisible = true
            binding.addTagEditText.requestFocus()
            showKeyboard()
        }
        binding.addTagEditText.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus && v.parent != null) {
                addTags()
            }
        }
        binding.addTagEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addTags(true)
                binding.addTagEditText.clearFocus()
                hideKeyboard()
            } else {
                binding.addTagChip.isVisible = true
                binding.addTagEditText.isVisible = false
            }
            true
        }

        binding.resetCover.isVisible = !isLocal
        binding.resetCover.setOnClickListener {
            binding.mangaCover.load(
                manga,
            ) {
                extras[MangaCoverFetcher.USE_CUSTOM_COVER_KEY] = false
            }
            customCoverUri = null
            willResetCover = true
        }
    }

    private fun addTags(textCanBeBlank: Boolean = false) {
        if ((textCanBeBlank || !binding.addTagEditText.text.isNullOrBlank()) &&
            binding.addTagEditText.isVisible
        ) {
            val newTags = binding.addTagEditText.text.toString().split(",")
                .mapNotNull { tag -> tag.trim().takeUnless { it.isBlank() } }
            val tags: List<String> = binding.mangaGenresTags.tags.toList() + newTags
            binding.addTagEditText.setText("")
            setGenreTags(tags)
            binding.seriesType.setSelection(manga.seriesType(customTags = tags.joinToString(", ")) - 1)
            binding.addTagChip.isVisible = true
            binding.addTagEditText.isVisible = false
        }
    }

    private fun TachiyomiTextInputEditText.appendOriginalTextOnLongClick(originalText: String?) {
        setOnLongClickListener {
            if (this.text.isNullOrBlank()) {
                this.append(originalText ?: "")
                true
            } else {
                false
            }
        }
    }

    private fun showKeyboard() {
        val inputMethodManager: InputMethodManager =
            binding.root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showSoftInput(
            binding.addTagEditText,
            WindowManager.LayoutParams
                .SOFT_INPUT_ADJUST_PAN,
        )
    }

    private fun hideKeyboard() {
        val inputMethodManager: InputMethodManager =
            binding.root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.addTagEditText.windowToken, 0)
    }

    private fun setGenreTags(genres: List<String>) {
        with(binding.mangaGenresTags) {
            val addTagChip = binding.addTagChip
            val addTagEditText = binding.addTagEditText
            removeAllViews()
            val dark = context.isInNightMode()
            val amoled = infoController.presenter.preferences.themeDarkAmoled().get()
            val baseTagColor = context.getResourceColor(R.attr.background)
            val bgArray = FloatArray(3)
            val accentArray = FloatArray(3)

            ColorUtils.colorToHSL(baseTagColor, bgArray)
            ColorUtils.colorToHSL(context.getResourceColor(R.attr.colorSecondary), accentArray)
            val downloadedColor = ColorUtils.setAlphaComponent(
                ColorUtils.HSLToColor(
                    floatArrayOf(
                        bgArray[0],
                        bgArray[1],
                        (
                            when {
                                amoled && dark -> 0.1f
                                dark -> 0.225f
                                else -> 0.85f
                            }
                            ),
                    ),
                ),
                199,
            )
            val textColor = ColorUtils.HSLToColor(
                floatArrayOf(
                    accentArray[0],
                    accentArray[1],
                    if (dark) 0.945f else 0.175f,
                ),
            )
            genres.map { genreText ->
                val chip = LayoutInflater.from(binding.root.context).inflate(
                    R.layout.genre_chip,
                    this,
                    false,
                ) as Chip
                val id = View.generateViewId()
                chip.id = id
                chip.chipBackgroundColor = ColorStateList.valueOf(downloadedColor)
                chip.setTextColor(textColor)
                chip.text = genreText
                chip.isCloseIconVisible = true
                chip.setOnCloseIconClickListener { view ->
                    this.removeView(view)
                    val tags: List<String> = tags.toList() - (view as Chip).text.toString()
                    binding.seriesType.setSelection(
                        manga.seriesType(
                            customTags = tags.joinToString(
                                ", ",
                            ),
                        ) - 1,
                    )
                }
                this.addView(chip)
            }
            addView(addTagChip)
            addView(addTagEditText)
        }
    }

    private val ChipGroup.tags: Array<String>
        get() = children
            .toList()
            .filterIsInstance<Chip>()
            .filter { it.isCloseIconVisible }
            .map { it.text.toString() }
            .toTypedArray()

    private fun resetTags() {
        if (manga.genre.isNullOrBlank() || manga.isLocal()) {
            setGenreTags(emptyList())
        } else {
            setGenreTags(manga.getOriginalGenres().orEmpty())
            binding.seriesType.setSelection(manga.seriesType(true) - 1)
            binding.resetsReadingMode.isVisible = false
        }
    }

    fun updateCover(uri: Uri) {
        willResetCover = false
        binding.mangaCover.load(uri)
        customCoverUri = uri
    }

    private fun onPositiveButtonClick() {
        addTags()
        infoController.presenter.updateManga(
            binding.title.text.toString(),
            binding.mangaAuthor.text.toString(),
            binding.mangaArtist.text.toString(),
            customCoverUri,
            binding.mangaDescription.text.toString(),
            binding.mangaGenresTags.tags,
            binding.mangaStatus.selectedPosition,
            if (binding.resetsReadingMode.isVisible) binding.seriesType.selectedPosition + 1 else null,
            languages.getOrNull(binding.mangaLang.selectedPosition),
            willResetCover,
        )
    }

    private companion object {
        const val KEY_MANGA = "manga_id"
    }
}
