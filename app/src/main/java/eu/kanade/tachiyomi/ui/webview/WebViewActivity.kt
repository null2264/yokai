package eu.kanade.tachiyomi.ui.webview

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.core.graphics.ColorUtils
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.WebViewClientCompat
import eu.kanade.tachiyomi.util.system.extensionIntentForText
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import okhttp3.HttpUrl.Companion.toHttpUrl
import timber.log.Timber
import uy.kohesive.injekt.injectLazy

open class WebViewActivity : BaseWebViewActivity() {

    private val sourceManager by injectLazy<SourceManager>()
    private var bundle: Bundle? = null

    private val network: NetworkHelper by injectLazy()

    private var backPressedCallback: OnBackPressedCallback? = null
    private val backCallback = {
        if (binding.webview.canGoBack()) binding.webview.goBack()
        reEnableBackPressedCallBack()
    }

    companion object {
        const val SOURCE_KEY = "source_key"
        const val URL_KEY = "url_key"
        const val TITLE_KEY = "title_key"

        fun newIntent(context: Context, url: String, sourceId: Long? = null, title: String? = null): Intent {
            val intent = Intent(context, WebViewActivity::class.java)
            intent.putExtra(SOURCE_KEY, sourceId)
            intent.putExtra(URL_KEY, url)
            intent.putExtra(TITLE_KEY, title)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return intent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = intent.extras?.getString(TITLE_KEY)

        binding.swipeRefresh.isEnabled = false

        backPressedCallback = onBackPressedDispatcher.addCallback { backCallback() }
        binding.toolbar.setNavigationOnClickListener {
            backPressedCallback?.isEnabled = false
            onBackPressedDispatcher.onBackPressed()
        }
        if (bundle == null) {
            val url = intent.extras!!.getString(URL_KEY) ?: return
            var headers = emptyMap<String, String>()
            (sourceManager.get(intent.extras!!.getLong(SOURCE_KEY)) as? HttpSource)?.let { source ->
                try {
                    headers = source.headers.toMultimap().mapValues { it.value.getOrNull(0) ?: "" }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to build headers")
                }
            }

            headers["user-agent"]?.let {
                binding.webview.settings.userAgentString = it
            }

            binding.webview.webViewClient = object : WebViewClientCompat() {
                override fun shouldOverrideUrlCompat(view: WebView, url: String): Boolean {
                    // Don't attempt to open blobs as webpages
                    if (url.startsWith("blob:http")) {
                        return false
                    }

                    // Continue with request, but with custom headers
                    view.loadUrl(url, headers)
                    return true
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    invalidateOptionsMenu()
                    binding.swipeRefresh.isEnabled = true
                    binding.swipeRefresh.isRefreshing = false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    invalidateOptionsMenu()
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    super.onPageCommitVisible(view, url)
                    binding.webview.scrollTo(0, 0)
                }

                override fun doUpdateVisitedHistory(
                    view: WebView?,
                    url: String?,
                    isReload: Boolean,
                ) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    if (!isReload) {
                        invalidateOptionsMenu()
                    }
                }
            }

            binding.webview.loadUrl(url, headers)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        invalidateOptionsMenu()
    }

    /**
     * Called when the options menu of the toolbar is being created. It adds our custom menu.
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.webview, menu)
        return true
    }

    private fun reEnableBackPressedCallBack() {
        backPressedCallback?.isEnabled = binding.webview.canGoBack()
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val backItem = binding.toolbar.menu.findItem(R.id.action_web_back)
        val forwardItem = binding.toolbar.menu.findItem(R.id.action_web_forward)
        backItem?.isEnabled = binding.webview.canGoBack()
        forwardItem?.isEnabled = binding.webview.canGoForward()
        val hasHistory = binding.webview.canGoBack() || binding.webview.canGoForward()
        backItem?.isVisible = hasHistory
        forwardItem?.isVisible = hasHistory
        val tintColor = getResourceColor(R.attr.actionBarTintColor)
        val translucentWhite = ColorUtils.setAlphaComponent(tintColor, 127)
        backItem.icon?.setTint(if (binding.webview.canGoBack()) tintColor else translucentWhite)
        forwardItem?.icon?.setTint(if (binding.webview.canGoForward()) tintColor else translucentWhite)
        val extenstionCanOpenUrl = binding.webview.canGoBack() &&
            binding.webview.url?.let { extensionIntentForText(it) != null } ?: false
        binding.toolbar.menu.findItem(R.id.action_open_in_app)?.isVisible = extenstionCanOpenUrl
        reEnableBackPressedCallBack()
        return super.onPrepareOptionsMenu(menu)
    }

    /**
     * Called when an item of the options menu was clicked. Used to handle clicks on our menu
     * entries.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_web_back -> binding.webview.goBack()
            R.id.action_web_forward -> binding.webview.goForward()
            R.id.action_web_share -> shareWebpage()
            R.id.action_web_browser -> openInBrowser()
            R.id.action_open_in_app -> openUrlInApp()
            R.id.action_web_clear_cookies -> clearCookies()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun clearCookies() {
        val url = binding.webview.url ?: return
        val cleared = network.cookieJar.remove(url.toHttpUrl())
        toast("Cleared $cleared cookies for: $url")
    }

    private fun openUrlInApp() {
        val url = binding.webview.url ?: return
        extensionIntentForText(url)?.let { startActivity(it) }
    }

    private fun shareWebpage() {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, binding.webview.url)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
        } catch (e: Exception) {
            toast(e.message)
        }
    }

    private fun openInBrowser() {
        binding.webview.url?.let { openInBrowser(it, forceBrowser = true, fullBrowser = true) }
    }
}
