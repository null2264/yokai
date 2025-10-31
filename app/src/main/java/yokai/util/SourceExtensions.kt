package yokai.util

import eu.kanade.tachiyomi.network.DataSaver
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Response

suspend fun HttpSource.getImage(page: Page, dataSaver: DataSaver): Response {
    val imageUrl = page.imageUrl ?: return getImage(page)
    page.imageUrl = dataSaver.getUrl(imageUrl)
    return try {
        getImage(page)
    } finally {
        page.imageUrl = imageUrl
    }
}
