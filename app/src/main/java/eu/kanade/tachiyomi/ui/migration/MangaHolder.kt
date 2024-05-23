package eu.kanade.tachiyomi.ui.migration

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import coil3.dispose
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.data.image.coil.loadManga
import eu.kanade.tachiyomi.databinding.MangaListItemBinding
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.view.setCards

class MangaHolder(
    view: View,
    adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
    showOutline: Boolean,
) : BaseFlexibleViewHolder(view, adapter) {

    private val binding = MangaListItemBinding.bind(view)

    init {
        setCards(showOutline, binding.card, null)
    }

    fun bind(item: MangaItem) {
        // Update the title of the manga.
        binding.title.text = item.manga.title
        binding.subtitle.text = ""

        // Update the cover.
        binding.coverThumbnail.dispose()
        binding.coverThumbnail.loadManga(item.manga)
    }
}
