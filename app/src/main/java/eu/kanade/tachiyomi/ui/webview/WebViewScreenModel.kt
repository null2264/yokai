package eu.kanade.tachiyomi.ui.webview

import android.content.Context
import android.content.Intent
import cafe.adriel.voyager.core.model.StateScreenModel
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.extensionIntentForText
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.i18n.MR
import yokai.presentation.StatsScreenState
import yokai.util.lang.getString

class WebViewScreenModel(
    val sourceId: Long?,
    private val sourceManager: SourceManager = Injekt.get(),
    private val network: NetworkHelper = Injekt.get(),
) : StateScreenModel<StatsScreenState>(StatsScreenState.Loading) {

    var headers = emptyMap<String, String>()

    init {
        sourceId?.let { sourceManager.get(it) as? HttpSource }?.let { source ->
            try {
                headers = source.headers.toMultimap().mapValues { it.value.getOrNull(0) ?: "" }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to build headers" }
            }
        }
    }

    fun shareWebpage(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }
            context.startActivity(Intent.createChooser(intent, context.getString(MR.strings.share)))
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }

    fun openInApp(context: Context, url: String) {
        context.extensionIntentForText(url)?.let { context.startActivity(it) }
    }

    fun openInBrowser(context: Context, url: String) {
        context.openInBrowser(url, forceBrowser = true, fullBrowser = true)
    }

    fun clearCookies(url: String) {
        url.toHttpUrlOrNull()?.let {
            val cleared = network.cookieJar.remove(it)
            Logger.d { "Cleared $cleared cookies for: $url" }
        }
    }
}
