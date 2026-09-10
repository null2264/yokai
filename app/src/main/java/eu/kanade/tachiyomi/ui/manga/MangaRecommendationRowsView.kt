package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.dispose
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.create
import eu.kanade.tachiyomi.data.recommendation.RecommendationCard
import eu.kanade.tachiyomi.databinding.SourceGlobalSearchControllerCardItemBinding
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.model.SManga
import yokai.domain.manga.models.cover
import yokai.util.coil.loadManga

class MangaRecommendationRowsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    private val creatorRow: View
    private val similarRow: View
    private val creatorAdapter = RecommendationCardAdapter()
    private val similarAdapter = RecommendationCardAdapter()

    var callbacks: Callbacks? = null
        set(value) {
            field = value
            creatorAdapter.callbacks = value
            similarAdapter.callbacks = value
        }

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.manga_recommendation_rows, this, true)
        creatorRow = findViewById(R.id.creator_recommendation_row)
        similarRow = findViewById(R.id.similar_recommendation_row)
        findViewById<RecyclerView>(R.id.creator_recommendation_list).apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            adapter = creatorAdapter
            itemAnimator = null
        }
        findViewById<RecyclerView>(R.id.similar_recommendation_list).apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            adapter = similarAdapter
            itemAnimator = null
        }
        isVisible = false
    }

    fun bind(
        sourceId: Long,
        creatorWorks: List<RecommendationCard>,
        similarManga: List<RecommendationCard>,
        favoriteUrls: Set<String>,
    ) {
        creatorAdapter.submit(sourceId, creatorWorks, favoriteUrls)
        similarAdapter.submit(sourceId, similarManga, favoriteUrls)
        creatorRow.isVisible = creatorWorks.isNotEmpty()
        similarRow.isVisible = similarManga.isNotEmpty()
        isVisible = creatorWorks.isNotEmpty() || similarManga.isNotEmpty()
    }

    interface Callbacks {
        fun onRecommendationClick(manga: SManga)
    }

    private class RecommendationCardAdapter : RecyclerView.Adapter<CardHolder>() {
        private var sourceId = 0L
        private var items = emptyList<RecommendationCard>()
        private var favoriteUrls = emptySet<String>()
        var callbacks: Callbacks? = null

        init {
            setHasStableIds(true)
        }

        fun submit(sourceId: Long, next: List<RecommendationCard>, favorites: Set<String>) {
            val previous = items
            this.sourceId = sourceId
            items = next
            favoriteUrls = favorites
            DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = previous.size
                override fun getNewListSize() = next.size
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                    previous[oldItemPosition].identity.exposureKey == next[newItemPosition].identity.exposureKey
                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                    previous[oldItemPosition].manga.title == next[newItemPosition].manga.title &&
                        previous[oldItemPosition].manga.thumbnail_url == next[newItemPosition].manga.thumbnail_url &&
                        isFavorite(previous[oldItemPosition]) == isFavorite(next[newItemPosition])
            }).dispatchUpdatesTo(this)
        }

        override fun getItemId(position: Int): Long =
            31L * sourceId + items[position].identity.exposureKey.hashCode()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardHolder {
            val binding = SourceGlobalSearchControllerCardItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
            return CardHolder(binding)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: CardHolder, position: Int) {
            val item = items[position]
            holder.bind(sourceId, item.manga, isFavorite(item))
            holder.itemView.setOnClickListener { callbacks?.onRecommendationClick(item.manga) }
        }

        private fun isFavorite(item: RecommendationCard): Boolean =
            item.favorite || item.manga.url in favoriteUrls
    }

    private class CardHolder(
        private val binding: SourceGlobalSearchControllerCardItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(sourceId: Long, item: SManga, favorite: Boolean) {
            binding.title.text = item.title
            binding.favoriteButton.isVisible = favorite
            binding.itemImage.dispose()
            binding.itemImage.setImageDrawable(null)
            if (!item.thumbnail_url.isNullOrBlank()) {
                val coverManga = Manga.create(item.url, item.title, sourceId).apply {
                    copyFrom(item)
                }
                binding.itemImage.loadManga(coverManga.cover(), binding.progress)
            }
        }
    }
}
