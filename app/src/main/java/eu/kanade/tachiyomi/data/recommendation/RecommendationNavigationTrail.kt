package eu.kanade.tachiyomi.data.recommendation

import eu.kanade.tachiyomi.source.model.SManga
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

internal object RecommendationNavigationTrail {
    private val trails = ConcurrentHashMap<Long, ArrayDeque<Entry>>()

    @Synchronized
    fun record(sourceId: Long, manga: SManga) {
        val identity = RecommendationMetadata.identity(sourceId, manga)
        val keys = identity.exposureKeys
        if (identity.canonicalUrl.isBlank() && keys.isEmpty()) return
        val trail = trails.getOrPut(sourceId) { ArrayDeque() }
        trail.removeAll { entry ->
            entry.canonicalUrl == identity.canonicalUrl ||
                entry.workKeys.intersect(keys).isNotEmpty()
        }
        trail.addFirst(Entry(identity.canonicalUrl, keys))
        while (trail.size > 8) trail.removeLast()
    }

    @Synchronized
    fun urls(sourceId: Long): Set<String> = trails[sourceId]
        .orEmpty()
        .mapTo(linkedSetOf(), Entry::canonicalUrl)
        .filterTo(linkedSetOf(), String::isNotBlank)

    @Synchronized
    fun workKeys(sourceId: Long): Set<String> = trails[sourceId]
        .orEmpty()
        .flatMapTo(linkedSetOf(), Entry::workKeys)

    private data class Entry(
        val canonicalUrl: String,
        val workKeys: Set<String>,
    )
}
