package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import eu.kanade.tachiyomi.databinding.ReaderErrorBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderErrorView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressBar
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.withIOContext
import eu.kanade.tachiyomi.util.system.withUIContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.Buffer
import okio.BufferedSource
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.InputStream

/**
 * Holder of the webtoon reader for a single page of a chapter.
 *
 * @param frame the root view for this holder.
 * @param viewer the webtoon viewer.
 * @constructor creates a new webtoon holder.
 */
class WebtoonPageHolder(
    private val frame: ReaderPageImageView,
    viewer: WebtoonViewer,
) : WebtoonBaseHolder(frame, viewer) {

    /**
     * Loading progress bar to indicate the current progress.
     */
    private val progressBar = createProgressBar()

    /**
     * Progress bar container. Needed to keep a minimum height size of the holder, otherwise the
     * adapter would create more views to fill the screen, which is not wanted.
     */
    private lateinit var progressContainer: ViewGroup

    /**
     * Error layout to show when the image fails to load.
     */
    private var errorLayout: ReaderErrorView? = null

    /**
     * Getter to retrieve the height of the recycler view.
     */
    private val parentHeight
        get() = viewer.recycler.height

    /**
     * Page of a chapter.
     */
    private var page: ReaderPage? = null

    private val scope = MainScope()

    /**
     * Job for loading the page.
     */
    private var loadJob: Job? = null

    /**
     * Job for progress changes of the page.
     */
    private var progressJob: Job? = null

    init {
        refreshLayoutParams()
        frame.setBackgroundColor(Color.BLACK)

        frame.onImageLoaded = { onImageDecoded() }
        frame.onImageLoadError = { onImageDecodeError() }
        frame.onScaleChanged = { viewer.activity.hideMenu() }
    }

    /**
     * Binds the given [page] with this view holder, subscribing to its state.
     */
    fun bind(page: ReaderPage) {
        this.page = page
        launchLoadJob()
        refreshLayoutParams()
    }

    private fun refreshLayoutParams() {
        frame.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            val margin = Resources.getSystem().displayMetrics.widthPixels * (viewer.config.sidePadding / 100f)
            marginEnd = margin.toInt()
            marginStart = margin.toInt()
        }
        if (viewer.hasMargins) {
            frame.updatePaddingRelative(bottom = 15.dpToPx)
        }
    }

    /**
     * Called when the view is recycled and added to the view pool.
     */
    override fun recycle() {
        cancelLoadJob()
        cancelProgressJob()

        removeErrorLayout()
        frame.recycle()
        progressBar.setProgress(0)
    }

    /**
     * Observes the status of the page and notify the changes.
     *
     * @see processStatus
     */
    private fun launchLoadJob() {
        cancelLoadJob()
        loadJob = scope.launch { loadPageAndProcessStatus() }
    }

    private suspend fun loadPageAndProcessStatus() {
        val page = page ?: return
        val loader = page.chapter.pageLoader ?: return
        supervisorScope {
            launchIO { loader.loadPage(page) }
            page.statusFlow.collectLatest { processStatus(it) }
        }
    }

    /**
     * Observes the progress of the page and updates view.
     */
    private fun launchProgressJob() {
        cancelProgressJob()

        val page = page ?: return
        progressJob = scope.launch {
            page.progressFlow.collectLatest { value -> progressBar.setProgress(value) }
        }
    }

    /**
     * Called when the status of the page changes.
     *
     * @param status the new status of the page.
     */
    private suspend fun processStatus(status: Page.State) {
        when (status) {
            Page.State.QUEUE -> setQueued()
            Page.State.LOAD_PAGE -> setLoading()
            Page.State.DOWNLOAD_IMAGE -> {
                launchProgressJob()
                setDownloading()
            }
            Page.State.READY -> {
                setImage()
                cancelProgressJob()
            }
            Page.State.ERROR -> {
                setError()
                cancelProgressJob()
            }
        }
    }

    /**
     * Cancels loading the page and processing changes to the page's status.
     */
    private fun cancelLoadJob() {
        loadJob?.cancel()
    }

    /**
     * Unsubscribes from the progress subscription.
     */
    private fun cancelProgressJob() {
        progressJob?.cancel()
    }

    /**
     * Called when the page is queued.
     */
    private fun setQueued() {
        progressContainer.isVisible = true
        progressBar.isVisible = true
        removeErrorLayout()
    }

    /**
     * Called when the page is loading.
     */
    private fun setLoading() {
        progressContainer.isVisible = true
        progressBar.isVisible = true
        removeErrorLayout()
    }

    /**
     * Called when the page is downloading
     */
    private fun setDownloading() {
        progressContainer.isVisible = true
        progressBar.isVisible = true
        removeErrorLayout()
    }

    /**
     * Called when the page is ready.
     */
    private suspend fun setImage() {
        progressContainer.isVisible = true
        progressBar.isVisible = true
        progressBar.completeAndFadeOut()
        removeErrorLayout()

        val streamFn = page?.stream ?: return

        val (source, isAnimated) = try {
            withIOContext {
                val source = streamFn().use { process(Buffer().readFrom(it)) }
                val isAnimated = ImageUtil.isAnimatedAndSupported(source)
                Pair(source, isAnimated)
            }
        } catch (e: Exception) {
            Timber.e(e)
            setError()
            return
        }
        withUIContext {
            frame.setImage(
                source,
                isAnimated,
                ReaderPageImageView.Config(
                    zoomDuration = viewer.config.doubleTapAnimDuration,
                    minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH,
                    cropBorders = viewer.config.run {
                        if (viewer.hasMargins) { verticalCropBorders } else { webtoonCropBorders }
                    },
                ),
            )
        }
    }

    private fun process(imageSource: BufferedSource): BufferedSource {
        if (!viewer.config.splitPages) {
            return imageSource
        }

        val isDoublePage = ImageUtil.isWideImage(imageSource)
        if (!isDoublePage) {
            return imageSource
        }

        return ImageUtil.splitAndStackBitmap(imageSource, viewer.config.invertDoublePages, viewer.hasMargins)
    }

    /**
     * Called when the page has an error.
     */
    private fun setError() {
        progressContainer.isVisible = false
        showErrorLayout(false)
    }

    /**
     * Called when the image is decoded and going to be displayed.
     */
    private fun onImageDecoded() {
        progressContainer.isVisible = false
    }

    /**
     * Called when the image fails to decode.
     */
    private fun onImageDecodeError() {
        progressContainer.isVisible = false
        showErrorLayout(true)
    }

    /**
     * Creates a new progress bar.
     */
    @SuppressLint("PrivateResource")
    private fun createProgressBar(): ReaderProgressBar {
        progressContainer = FrameLayout(context)
        frame.addView(progressContainer, MATCH_PARENT, parentHeight)

        val progress = ReaderProgressBar(context).apply {
            val size = 48.dpToPx
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, parentHeight / 4, 0, 0)
            }
        }
        progressContainer.addView(progress)
        return progress
    }

    private fun showErrorLayout(withOpenInWebView: Boolean): ReaderErrorBinding {
        if (errorLayout == null) {
            errorLayout = ReaderErrorBinding.inflate(LayoutInflater.from(context), frame, true).root
            errorLayout?.binding?.actionRetry?.setOnClickListener {
                page?.let { it.chapter.pageLoader?.retryPage(it) }
            }
        }
        val imageUrl = if (withOpenInWebView) {
            page?.imageUrl
        } else {
            viewer.activity.viewModel.getChapterUrl(page?.chapter?.chapter)
        }
        return errorLayout!!.configureView(imageUrl)
    }

    private fun removeErrorLayout() {
        errorLayout?.let {
            frame.removeView(it)
            errorLayout = null
        }
    }
}
