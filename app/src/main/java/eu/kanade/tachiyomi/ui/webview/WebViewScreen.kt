package eu.kanade.tachiyomi.ui.webview

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import eu.kanade.tachiyomi.util.compose.currentOrThrow
import yokai.presentation.webview.WebViewScreenContent
import yokai.util.AssistContentScreen
import yokai.util.Screen

class WebViewScreen(
    private val url: String,
    private val initialTitle: String? = null,
    private val sourceId: Long? = null,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { WebViewScreenModel(sourceId) }

        WebViewScreenContent(
            onNavigateUp = { navigator.pop() },
            initialTitle = initialTitle,
            url = url,
            headers = screenModel.headers,
            onUrlChange = { assistUrl = it },
            onShare = { screenModel.shareWebpage(context, it) },
            onOpenInApp = { screenModel.openInApp(context, it) },
            onOpenInBrowser = { screenModel.openInBrowser(context, it) },
            onClearCookies = screenModel::clearCookies,
        )
    }
}
