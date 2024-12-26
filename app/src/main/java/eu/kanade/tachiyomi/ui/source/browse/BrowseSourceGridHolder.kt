package eu.kanade.tachiyomi.ui.source.browse

import android.app.Activity
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.databinding.MangaGridItemBinding
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.ui.library.LibraryCategoryAdapter
import eu.kanade.tachiyomi.util.view.setCards
import yokai.domain.manga.models.cover

/**
 * Class used to hold the displayed data of a manga in the library, like the cover or the title.
 * All the elements from the layout file "item_catalogue_grid" are available in this class.
 *
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @constructor creates a new library holder.
 */
class BrowseSourceGridHolder(
    private val view: View,
    private val adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    compact: Boolean,
    showOutline: Boolean,
) : BrowseSourceHolder(view, adapter) {

    private val binding = MangaGridItemBinding.bind(view)
    init {
        if (compact) {
            binding.textLayout.isVisible = false
        } else {
            binding.compactTitle.isVisible = false
            binding.gradient.isVisible = false
        }
        setCards(showOutline, binding.card, binding.unreadDownloadBadge.badgeView)
    }

    /**
     * Method called from [LibraryCategoryAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param manga the manga item to bind.
     */
    override fun onSetValues(manga: Manga) {
        // Update the title of the manga.
        binding.title.text = manga.title
        binding.compactTitle.text = binding.title.text
        binding.unreadDownloadBadge.root.setInLibrary(manga.favorite)

        // Update the cover.
        setImage(manga)
    }

    override fun setImage(manga: Manga) {
        if ((view.context as? Activity)?.isDestroyed == true) return
        if (manga.thumbnail_url == null) {
//            binding.coverThumbnail.dispose()
        } else {
            manga.id ?: return
            binding.coverThumbnail.loadManga(manga.cover(), binding.progress)
            binding.coverThumbnail.alpha = if (manga.favorite) 0.34f else 1.0f
            binding.card.strokeColorStateList?.defaultColor?.let { color ->
                binding.card.strokeColor = ColorUtils.setAlphaComponent(color, if (manga.favorite) 87 else 255)
            }
        }
    }
}
