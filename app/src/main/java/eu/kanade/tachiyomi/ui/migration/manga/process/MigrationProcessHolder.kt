package eu.kanade.tachiyomi.ui.migration.manga.process

import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.coil.useCustomCover
import eu.kanade.tachiyomi.databinding.MangaGridItemBinding
import eu.kanade.tachiyomi.databinding.MigrationProcessItemBinding
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.library.setFreeformCoverRatio
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.util.view.setCards
import eu.kanade.tachiyomi.util.view.setVectorCompat
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import java.text.DecimalFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.injectLazy
import yokai.domain.chapter.interactor.GetChapter
import yokai.domain.manga.interactor.GetManga
import yokai.domain.manga.models.cover
import yokai.i18n.MR
import yokai.util.coil.loadManga
import yokai.util.lang.getString

class MigrationProcessHolder(
    private val view: View,
    private val adapter: MigrationProcessAdapter,
) : BaseFlexibleViewHolder(view, adapter) {

    private val getChapter: GetChapter by injectLazy()
    private val getManga: GetManga by injectLazy()

    private val sourceManager: SourceManager by injectLazy()
    private var item: MigrationProcessItem? = null
    private val binding = MigrationProcessItemBinding.bind(view)

    // Each ViewHolder owns its own scope so bind() coroutines can be
    // cancelled when the holder rebinds to a different item or is recycled,
    // instead of accumulating as parked coroutines on the dispatcher.
    private val holderScope = MainScope()
    private var bindJob: Job? = null

    init {
        // We need to post a Runnable to show the popup to make sure that the PopupMenu is
        // correctly positioned. The reason being that the view may change position before the
        // PopupMenu is shown.
        binding.migrationMenu.setOnClickListener { it.post { showPopupMenu(it) } }
        binding.skipManga.setOnClickListener { it.post { adapter.removeManga(flexibleAdapterPosition) } }
        arrayOf(binding.migrationMangaCardFrom, binding.migrationMangaCardTo).forEach {
            setCards(adapter.showOutline, it.card, it.unreadDownloadBadge.badgeView)
        }
        binding.migrationMangaCardFrom.title.maxLines = 1
        binding.migrationMangaCardTo.title.maxLines = 1
    }

    fun bind(item: MigrationProcessItem) {
        this.item = item

        // Cancel any previous bind coroutine for this holder before starting a new one.
        // Without this, every notifyItemChanged() call (which sourceFinished() triggers)
        // would park a new coroutine on searchResult.get()'s mutex, exhausting the
        // dispatcher thread pool and causing the progressive UI slowdown.
        bindJob?.cancel()
        bindJob = holderScope.launch {
            binding.migrationMangaCardFrom.setFreeformCoverRatio(item.manga.manga())
            binding.migrationMangaCardTo.setFreeformCoverRatio(null)

            val manga = item.manga.manga()
            val source = item.manga.mangaSource()

            binding.migrationMenu.setVectorCompat(
                R.drawable.ic_more_vert_24dp,
                R.attr.colorOnBackground,
            )
            binding.skipManga.setVectorCompat(
                R.drawable.ic_close_24dp,
                R.attr.colorOnBackground,
            )
            binding.migrationMenu.isInvisible = true
            binding.skipManga.isVisible = true
            binding.migrationMangaCardTo.resetManga()
            if (manga != null) {
                withContext(Dispatchers.Main) {
                    binding.migrationMangaCardFrom.attachManga(manga, source)
                    binding.migrationMangaCardFrom.root.setOnClickListener {
                        adapter.controller.router.pushController(
                            MangaDetailsController(
                                manga,
                                true,
                            ).withFadeTransaction(),
                        )
                    }
                }

                // searchResult.get() suspends until the search completes.
                // This is safe here because bindJob is cancelled on every rebind,
                // so only one coroutine per holder is ever parked waiting here.
                val searchResult = item.manga.searchResult.get()?.let { getManga.awaitById(it) }
                val resultSource = searchResult?.source?.let { sourceManager.get(it) }

                // Guard: if this holder was rebound while we were suspended, bail out.
                if (!isActive || item.manga.mangaId != this@MigrationProcessHolder.item?.manga?.mangaId) {
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    if (item.manga.migrationStatus == MigrationStatus.RUNNUNG) {
                        return@withContext
                    }
                    if (searchResult != null && resultSource != null) {
                        binding.migrationMangaCardTo.attachManga(searchResult, resultSource)
                        binding.migrationMangaCardTo.root.setOnClickListener {
                            adapter.controller.router.pushController(
                                MangaDetailsController(
                                    searchResult,
                                    true,
                                ).withFadeTransaction(),
                            )
                        }
                    } else {
                        binding.migrationMangaCardTo.coverThumbnail.setImageDrawable(null)
                        binding.migrationMangaCardTo.progress.isVisible = false
                        binding.migrationMangaCardTo.title.text =
                            view.context.getString(MR.strings.no_alternatives_found)
                    }
                    binding.migrationMenu.isVisible = true
                    binding.skipManga.isVisible = false
                    adapter.sourceFinished()
                }
            }
        }
    }

    /**
     * Called when this ViewHolder is recycled. Cancels the bind coroutine so
     * no stale UI updates land on a holder that's been reused for a different item.
     */
    fun onRecycled() {
        bindJob?.cancel()
    }

    private fun MangaGridItemBinding.resetManga() {
        progress.isVisible = true
        coverThumbnail.setImageDrawable(null)
        compactTitle.text = ""
        title.text = ""
        subtitle.text = ""
        unreadDownloadBadge.badgeView.setChapters(null)
        (root.layoutParams as ConstraintLayout.LayoutParams).verticalBias = 0.5f
        subtitle.text = ""
        root.setOnClickListener(null)
    }

    private suspend fun MangaGridItemBinding.attachManga(manga: Manga, source: Source) {
        (root.layoutParams as ConstraintLayout.LayoutParams).verticalBias = 1f
        progress.isVisible = false

        coverThumbnail.loadManga(manga.cover(), progress) {
            useCustomCover(false)
        }

        compactTitle.isVisible = true
        gradient.isVisible = true
        compactTitle.text = manga.title.ifBlank {
            view.context.getString(MR.strings.unknown)
        }

        gradient.isVisible = true
        title.text = source.toString()

        val mangaChapters = getChapter.awaitAll(manga, false)
        unreadDownloadBadge.badgeView.setChapters(mangaChapters.size)
        val latestChapter = mangaChapters.maxOfOrNull { it.chapter_number } ?: -1f

        if (latestChapter > 0f) {
            subtitle.text = root.context.getString(
                MR.strings.latest_,
                DecimalFormat("#.#").format(latestChapter),
            )
        } else {
            subtitle.text = root.context.getString(
                MR.strings.latest_,
                root.context.getString(MR.strings.unknown),
            )
        }
    }

    private fun showPopupMenu(view: View) {
        val item = adapter.getItem(flexibleAdapterPosition) ?: return

        // Create a PopupMenu, giving it the clicked view for an anchor
        val popup = PopupMenu(view.context, view)

        // Inflate our menu resource into the PopupMenu's Menu
        popup.menuInflater.inflate(R.menu.migration_single, popup.menu)

        val mangas = item.manga

        popup.menu.findItem(R.id.action_search_manually).isVisible = true
        // Hide download and show delete if the chapter is downloaded
        if (mangas.searchResult.content != null) {
            popup.menu.findItem(R.id.action_migrate_now).isVisible = true
            popup.menu.findItem(R.id.action_copy_now).isVisible = true
        }

        // Set a listener so we are notified if a menu item is clicked
        popup.setOnMenuItemClickListener { menuItem ->
            adapter.menuItemListener.onMenuItemClick(flexibleAdapterPosition, menuItem)
            true
        }

        // Finally show the PopupMenu
        popup.show()
    }
}
