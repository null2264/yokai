package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.ReaderErrorBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.settings.ReaderBackgroundColor
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderErrorView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressBar
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig.ZoomType
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.ImageUtil.isPagePadded
import eu.kanade.tachiyomi.util.system.ThemeUtil
import eu.kanade.tachiyomi.util.system.bottomCutoutInset
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.topCutoutInset
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.util.view.backgroundColor
import eu.kanade.tachiyomi.util.view.isVisibleOnScreen
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.BufferedSource
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.io.InputStream
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * View of the ViewPager that contains a page of a chapter.
 */
@SuppressLint("ViewConstructor")
class PagerPageHolder(
    val viewer: PagerViewer,
    val page: ReaderPage,
    private var extraPage: ReaderPage? = null,
) : ReaderPageImageView(viewer.activity), ViewPagerAdapter.PositionableView {

    /**
     * Item that identifies this view. Needed by the adapter to not recreate views.
     */
    override val item
        get() = page to extraPage

    /**
     * Loading progress bar to indicate the current progress.
     */
    private val progressBar = createProgressBar()

    /**
     * Error layout to show when the image fails to load.
     */
    private var errorLayout: ReaderErrorView? = null

    /**
     * Job for loading the page.
     */
    private var loadJob: Job? = null

    /**
     * Job for status changes of the page.
     */
    private var statusJob: Job? = null

    /**
     * Job for progress changes of the page.
     */
    private var progressJob: Job? = null

    /**
     * Job for loading the page.
     */
    private var extraLoadJob: Job? = null

    /**
     * Job for status changes of the page.
     */
    private var extraStatusJob: Job? = null

    /**
     * Job for progress changes of the page.
     */
    private var extraProgressJob: Job? = null

    /**
     * Job used to read the header of the image. This is needed in order to instantiate
     * the appropriate image view depending if the image is animated (GIF).
     */
    private var readImageHeaderJob: Job? = null

    private var status = Page.State.READY
    private var extraStatus = Page.State.READY
    private var progress: Int = 0
    private var extraProgress: Int = 0

    private var scope = MainScope()

    init {
        addView(progressBar)
        if (viewer.config.hingeGapSize > 0) {
            progressBar.updateLayoutParams<MarginLayoutParams> {
                marginStart = ((context.resources.displayMetrics.widthPixels) / 2 + viewer.config.hingeGapSize) / 2
            }
        }
        launchLoadJob()
        setBackgroundColor(
            when (val theme = viewer.config.readerTheme) {
                ReaderBackgroundColor.SMART_THEME.prefValue -> Color.TRANSPARENT
                else -> ThemeUtil.readerBackgroundColor(theme)
            },
        )
        progressBar.foregroundTintList = ColorStateList.valueOf(
            context.getResourceColor(
                if (isInvertedFromTheme()) {
                    R.attr.colorPrimaryInverse
                } else {
                    R.attr.colorPrimary
                },
            ),
        )
    }

    override fun onImageLoaded() {
        super.onImageLoaded()
        (pageView as? SubsamplingScaleImageView)?.apply {
            if (this@PagerPageHolder.extraPage == null &&
                this@PagerPageHolder.page.longPage == null &&
                sHeight < sWidth
            ) {
                this@PagerPageHolder.page.longPage = true
            }
        }
        onImageDecoded()
    }

    override fun onNeedsLandscapeZoom() {
        (pageView as? SubsamplingScaleImageView)?.apply {
            if (viewer.heldForwardZoom?.first == page.index) {
                landscapeZoom(viewer.heldForwardZoom?.second)
                viewer.heldForwardZoom = null
            } else if (isVisibleOnScreen()) {
                landscapeZoom(true)
            }
        }
    }

    override fun onScaleChanged(newScale: Float) {
        super.onScaleChanged(newScale)
        viewer.hideMenuIfVisible(item)
    }

    override fun onImageLoadError() {
        super.onImageLoadError()
        onImageDecodeError()
    }

    /**
     * Called when this view is detached from the window. Unsubscribes any active subscription.
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelProgressJob(1)
        cancelLoadJob(1)
        cancelProgressJob(2)
        cancelLoadJob(2)
        cancelReadImageHeader()
        (pageView as? SubsamplingScaleImageView)?.setOnImageEventListener(null)
    }

    /**
     * Starts loading the page and processing changes to the page's status.
     *
     * @see processStatus
     */
    private fun launchLoadJob() {
        loadJob?.cancel()
        statusJob?.cancel()

        val loader = page.chapter.pageLoader ?: return
        loadJob = scope.launch {
            loader.loadPage(page)
        }
        statusJob = scope.launch {
            page.statusFlow.collectLatest { processStatus(it) }
        }
        val extraPage = extraPage ?: return
        extraLoadJob = scope.launch {
            loader.loadPage(extraPage)
        }
        extraStatusJob = scope.launch {
            extraPage.statusFlow.collectLatest { processStatus2(it) }
        }
    }

    private fun launchProgressJob() {
        progressJob?.cancel()
        progressJob = scope.launch {
            page.progressFlow.collectLatest { value ->
                progress = value
                if (extraPage == null) {
                    progressBar.setProgress(progress)
                } else {
                    progressBar.setProgress(((progress + extraProgress) / 2 * 0.95f).roundToInt())
                }
            }
        }
    }

    private fun launchProgressJob2() {
        val extraPage = extraPage ?: return
        extraProgressJob?.cancel()
        extraProgressJob = scope.launch {
            extraPage.progressFlow.collectLatest { value ->
                extraProgress = value
                progressBar.setProgress(((progress + extraProgress) / 2 * 0.95f).roundToInt())
            }
        }
    }

    fun onPageSelected(forward: Boolean?) {
        (pageView as? SubsamplingScaleImageView)?.apply {
            if (isReady) {
                landscapeZoom(forward)
            } else {
                forward ?: return@apply
                setOnImageEventListener(
                    object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                        override fun onReady() {
                            setupZoom(imageConfig)
                            landscapeZoom(forward)
                            this@PagerPageHolder.onImageLoaded()
                        }

                        override fun onImageLoadError(e: Exception) {
                            onImageDecodeError()
                        }
                    },
                )
            }
        }
    }

    /**
     * Check if the image can be panned to the left
     */
    fun canPanLeft(): Boolean = canPan { it.left }

    /**
     * Check if the image can be panned to the right
     */
    fun canPanRight(): Boolean = canPan { it.right }

    /**
     * Check whether the image can be panned.
     * @param fn a function that returns the direction to check for
     */
    private fun canPan(fn: (RectF) -> Float): Boolean {
        (pageView as? SubsamplingScaleImageView)?.let { view ->
            RectF().let {
                view.getPanRemaining(it)
                return fn(it) > 0.01f
            }
        }
        return false
    }

    /**
     * Pans the image to the left by a screen's width worth.
     */
    fun panLeft() {
        pan { center, view -> center.also { it.x -= view.width / view.scale } }
    }

    /**
     * Pans the image to the right by a screen's width worth.
     */
    fun panRight() {
        pan { center, view -> center.also { it.x += view.width / view.scale } }
    }

    /**
     * Pans the image.
     * @param fn a function that computes the new center of the image
     */
    private fun pan(fn: (PointF, SubsamplingScaleImageView) -> PointF) {
        (pageView as? SubsamplingScaleImageView)?.let { view ->
            val target = fn(view.center ?: return, view)
            view.animateCenter(target)!!
                .withEasing(SubsamplingScaleImageView.EASE_OUT_QUAD)
                .withDuration(250)
                .withInterruptible(true)
                .start()
        }
    }

    private fun SubsamplingScaleImageView.landscapeZoom(forward: Boolean?) {
        forward ?: return
        if (viewer.config.landscapeZoom && viewer.config.imageScaleType == SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE && sWidth > sHeight && scale == minScale) {
            handler.postDelayed(
                {
                    val point = when (viewer.config.imageZoomType) {
                        ZoomType.Left -> if (forward) PointF(0F, 0F) else PointF(sWidth.toFloat(), 0F)
                        ZoomType.Right -> if (forward) PointF(sWidth.toFloat(), 0F) else PointF(0F, 0F)
                        ZoomType.Center -> center.also { it?.y = 0F }
                    }

                    val rootInsets = viewer.activity.window.decorView.rootWindowInsets
                    val topInsets = if (viewer.activity.isSplitScreen) {
                        0f
                    } else {
                        rootInsets?.topCutoutInset()?.toFloat() ?: 0f
                    }
                    val bottomInsets = if (viewer.activity.isSplitScreen) {
                        0f
                    } else {
                        rootInsets?.bottomCutoutInset()?.toFloat() ?: 0f
                    }
                    val targetScale = (height.toFloat() - topInsets - bottomInsets) / sHeight.toFloat()
                    animateScaleAndCenter(min(targetScale, minScale * 2), point)!!
                        .withDuration(500)
                        .withEasing(SubsamplingScaleImageView.EASE_IN_OUT_QUAD)
                        .withInterruptible(true)
                        .start()
                },
                500,
            )
        }
    }

    /**
     * Called when the status of the page changes.
     *
     * @param status the new status of the page.
     */
    private fun processStatus(status: Page.State) {
        when (status) {
            Page.State.QUEUE -> setQueued()
            Page.State.LOAD_PAGE -> setLoading()
            Page.State.DOWNLOAD_IMAGE -> {
                launchProgressJob()
                setDownloading()
            }
            Page.State.READY -> {
                if (extraStatus == Page.State.READY || extraPage == null) {
                    setImage()
                }
                cancelProgressJob(1)
            }
            Page.State.ERROR -> {
                setError()
                cancelProgressJob(1)
            }
        }
    }

    /**
     * Called when the status of the page changes.
     *
     * @param status the new status of the page.
     */
    private fun processStatus2(status: Page.State) {
        when (status) {
            Page.State.QUEUE -> setQueued()
            Page.State.LOAD_PAGE -> setLoading()
            Page.State.DOWNLOAD_IMAGE -> {
                launchProgressJob2()
                setDownloading()
            }
            Page.State.READY -> {
                if (this.status == Page.State.READY) {
                    setImage()
                }
                cancelProgressJob(2)
            }
            Page.State.ERROR -> {
                setError()
                cancelProgressJob(2)
            }
        }
    }

    /**
     * Cancels loading the page and processing changes to the page's status.
     */
    private fun cancelLoadJob(page: Int) {
        if (page == 1) {
            loadJob?.cancel()
            loadJob = null
            statusJob?.cancel()
            statusJob = null
        } else {
            extraLoadJob?.cancel()
            extraLoadJob = null
            extraStatusJob?.cancel()
            extraStatusJob = null
        }
    }

    private fun cancelProgressJob(page: Int) {
        (if (page == 1) progressJob else extraProgressJob)?.cancel()
        if (page == 1) {
            progressJob = null
        } else {
            extraProgressJob = null
        }
    }

    /**
     * Unsubscribes from the read image header subscription.
     */
    private fun cancelReadImageHeader() {
        readImageHeaderJob?.cancel()
        readImageHeaderJob = null
    }

    /**
     * Called when the page is queued.
     */
    private fun setQueued() {
        progressBar.isVisible = true
        errorLayout?.isVisible = false
    }

    /**
     * Called when the page is loading.
     */
    private fun setLoading() {
        progressBar.isVisible = true
        errorLayout?.isVisible = false
    }

    /**
     * Called when the page is downloading.
     */
    private fun setDownloading() {
        progressBar.isVisible = true
        errorLayout?.isVisible = false
    }

    /**
     * Called when the page is ready.
     */
    private fun setImage() {
        progressBar.isVisible = true
        if (extraPage == null) {
            progressBar.completeAndFadeOut()
        } else {
            progressBar.setProgress(95)
        }
        errorLayout?.isVisible = false

        cancelReadImageHeader()

        readImageHeaderJob = scope.launchIO {
            val streamFn = page.stream ?: return@launchIO
            val streamFn2 = extraPage?.stream

            var actualSource: BufferedSource? = null
            try {
                val source = streamFn().buffered(16).use { Buffer().readFrom(it) }
                val source2 = streamFn2?.invoke()?.buffered(16)?.use { Buffer().readFrom(it) }

                actualSource = this@PagerPageHolder.mergeOrSplitPages(source, source2)
                val isAnimated = ImageUtil.isAnimatedAndSupported(source) ||
                    (source2?.let { ImageUtil.isAnimatedAndSupported(source2) } ?: false)

                withUIContext {
                    val bgColor = ReaderBackgroundColor.fromPreference(viewer.config.readerTheme)
                    if (!isAnimated) {
                        if (bgColor.isSmartColor) {
                            val bgType = getBGType(viewer.config.readerTheme, context)
                            if (page.bg != null && page.bgType == bgType) {
                                setImage(actualSource, false, imageConfig)
                                pageView?.background = page.bg
                            }
                            // if the user switches to automatic when pages are already cached, the bg needs to be loaded
                            else {
                                setImage(actualSource, false, imageConfig)
                                closeSources(actualSource)

                                try {
                                    pageView?.background = setBG(actualSource.readByteArray())
                                } catch (e: Exception) {
                                    Timber.e(e.localizedMessage)
                                    pageView?.background = ColorDrawable(Color.WHITE)
                                } finally {
                                    page.bg = pageView?.background
                                    page.bgType = bgType
                                }
                            }
                        } else {
                            setImage(actualSource, false, imageConfig)
                        }
                    } else {
                        setImage(actualSource, true, imageConfig)
                        if (bgColor.isSmartColor && page.bg != null) {
                            pageView?.background = page.bg
                        }
                    }
                }
            } catch (_: Exception) {
                try {
                    actualSource?.let { closeSources(it) }
                } catch (_: Exception) {
                }
            }
        }
    }

    private val imageConfig: Config
        get() = Config(
            zoomDuration = viewer.config.doubleTapAnimDuration,
            minimumScaleType = viewer.config.imageScaleType,
            cropBorders = viewer.config.imageCropBorders,
            zoomStartPosition = viewer.config.imageZoomType,
            landscapeZoom = viewer.config.landscapeZoom,
            insetInfo = InsetInfo(
                cutoutSupport = DeviceUtil.hasCutout(viewer.activity),
                cutoutBehavior = viewer.config.cutoutBehavior,
                topCutoutInset = viewer.activity.window.decorView.rootWindowInsets?.topCutoutInset()?.toFloat() ?: 0f,
                bottomCutoutInset = viewer.activity.window.decorView.rootWindowInsets?.bottomCutoutInset()?.toFloat() ?: 0f,
                scaleTypeIsFullFit = viewer.config.scaleTypeIsFullFit(),
                isFullscreen = viewer.config.isFullscreen
                    && if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) !viewer.activity.isInMultiWindowMode else true,
                isSplitScreen = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && viewer.activity.isInMultiWindowMode,
                insets = viewer.activity.window.decorView.rootWindowInsets,
            ),
            hingeGapSize = viewer.config.hingeGapSize,
        )

    private suspend fun setBG(bytesArray: ByteArray): Drawable {
        return withContext(Default) {
            val preferences by injectLazy<PreferencesHelper>()
            ImageUtil.autoSetBackground(
                BitmapFactory.decodeByteArray(
                    bytesArray,
                    0,
                    bytesArray.size,
                ),
                preferences.readerTheme().get() == 2,
                context,
            )
        }
    }

    /**
     * Called when the page has an error.
     */
    private fun setError() {
        progressBar.isVisible = false
        showErrorLayout(false)
    }

    /**
     * Called when the image is decoded and going to be displayed.
     */
    private fun onImageDecoded() {
        progressBar.isVisible = false
    }

    /**
     * Called when an image fails to decode.
     */
    private fun onImageDecodeError() {
        progressBar.isVisible = false
        showErrorLayout(true)
    }

    /**
     * Creates a new progress bar.
     */
    private fun createProgressBar(): ReaderProgressBar {
        return ReaderProgressBar(context, null).apply {
            val size = 48.dpToPx
            layoutParams = LayoutParams(size, size).apply {
                gravity = Gravity.CENTER
            }
        }
    }

    private fun isInvertedFromTheme(): Boolean {
        return when (backgroundColor) {
            Color.WHITE -> context.isInNightMode()
            Color.BLACK -> !context.isInNightMode()
            else -> false
        }
    }

    private fun showErrorLayout(withOpenInWebView: Boolean): ReaderErrorBinding {
        if (errorLayout == null) {
            errorLayout = ReaderErrorBinding.inflate(LayoutInflater.from(context), this, true).root
            errorLayout?.viewer = viewer
            errorLayout?.binding?.actionRetry?.setOnClickListener {
                page.chapter.pageLoader?.retryPage(page)
            }
        }
        val imageUrl = if (withOpenInWebView) {
            page.imageUrl
        } else {
            viewer.activity.viewModel.getChapterUrl(page.chapter.chapter)
        }
        return errorLayout!!.configureView(imageUrl)
    }

    private suspend fun mergeOrSplitPages(source: BufferedSource, source2: BufferedSource?): BufferedSource {
        if (ImageUtil.isAnimatedAndSupported(source)) {
            if (page.longPage == null) {
                page.longPage = true
                if (viewer.config.splitPages || source2 != null) {
                    splitDoublePages()
                }
            }
            scope.launchUI { progressBar.completeAndFadeOut() }
            return source
        }
        if (page.longPage == true && viewer.config.splitPages) {
            val imageBitmap = try {
                BitmapFactory.decodeStream(source.peek().inputStream())
            } catch (e: Exception) {
                //closeSources(source)
                Timber.e("Cannot split page ${e.message}")
                return source
            }
            val isLTR = (viewer !is R2LPagerViewer).xor(viewer.config.invertDoublePages)
            return ImageUtil.splitBitmap(imageBitmap, (page.firstHalf == false).xor(!isLTR)) {
                scope.launchUI {
                    if (it == 100) {
                        progressBar.completeAndFadeOut()
                    } else {
                        progressBar.setProgress(it)
                    }
                }
            }
        }
        if (source2 == null) {
            if (viewer.config.splitPages && page.longPage == null) {
                val imageBitmap = try {
                    BitmapFactory.decodeStream(source.peek().inputStream())
                } catch (e: Exception) {
                    //closeSources(imageStream)
                    page.longPage = true
                    splitDoublePages()
                    Timber.e("Cannot split page ${e.message}")
                    return source
                }
                val height = imageBitmap.height
                val width = imageBitmap.width
                return if (height < width) {
                    closeSources(source)
                    page.longPage = true
                    splitDoublePages()
                    val isLTR = (viewer !is R2LPagerViewer).xor(viewer.config.invertDoublePages)
                    return ImageUtil.splitBitmap(imageBitmap, !isLTR) {
                        scope.launchUI {
                            if (it == 100) {
                                progressBar.completeAndFadeOut()
                            } else {
                                progressBar.setProgress(it)
                            }
                        }
                    }
                } else {
                    page.longPage = false
                    source
                }
            }
            return supportHingeIfThere(source)
        }
        if (page.fullPage == true) return supportHingeIfThere(source)
        val imageBitmap = try {
            BitmapFactory.decodeStream(source.peek().inputStream())
        } catch (e: Exception) {
            closeSources(source2)
            page.fullPage = true
            splitDoublePages()
            Timber.e("Cannot combine pages ${e.message}")
            return supportHingeIfThere(source)
        }
        scope.launchUI { progressBar.setProgress(96) }
        val height = imageBitmap.height
        val width = imageBitmap.width

        val isLTR = (viewer !is R2LPagerViewer).xor(viewer.config.invertDoublePages)

        val pages = page.chapter.pages
        if (height < width) {
            if (extraPage?.index == 1) {
                setExtraPageBitmap(source2.peek().inputStream(), isLTR)
            }
            closeSources(source2)
            val oldValue = page.fullPage
            page.fullPage = true
            delayPageUpdate {
                val thirdPageIsStart = pages?.getOrNull(2)?.isStartPage == true
                val extraPageIsEnd = extraPage?.isEndPage == true
                if (page.index == 0 &&
                    (
                        (viewer.config.shiftDoublePage && !thirdPageIsStart) ||
                            extraPage?.isEndPage == true
                        ) && oldValue != true
                ) {
                    viewer.activity.shiftDoublePages(extraPageIsEnd || thirdPageIsStart, extraPage)
                } else {
                    viewer.splitDoublePages(page)
                }
                extraPage = null
            }
            return supportHingeIfThere(source)
        }
        val isNotEndPage: ReaderPage.() -> Boolean =
            { isEndPage != true || (page.endPageConfidence ?: 0) > (endPageConfidence ?: 0) }
        var earlyImageBitmap2: Bitmap? = null
        val isFirstPageNotEnd by lazy { pages?.get(0)?.let { it.isNotEndPage() } != false }
        val isThirdPageNotEnd by lazy { pages?.getOrNull(2)?.let { it.isNotEndPage() } == true }
        val shouldShiftAnyway = !viewer.activity.manuallyShiftedPages && page.endPageConfidence == 3
        if (page.index <= 2 && page.isEndPage == null && page.fullPage == null) {
            page.endPageConfidence = imageBitmap.isPagePadded(rightSide = !isLTR)
            if (extraPage?.index == 1 && extraPage?.isEndPage == null) {
                earlyImageBitmap2 = setExtraPageBitmap(source2.peek().inputStream(), isLTR)
            }
            if (page.index == 1 && page.isEndPage == true && viewer.config.shiftDoublePage &&
                (isFirstPageNotEnd || isThirdPageNotEnd)
            ) {
                shiftDoublePages(false)
                return supportHingeIfThere(source)
            } else if (page.isEndPage == true &&
                when (page.index) {
                    // 3rd page shouldn't shift if the 1st page is a spread
                    2 -> pages?.get(0)?.fullPage != true
                    // 2nd page shouldn't shift if the 1st page is more likely an end page
                    1 -> isFirstPageNotEnd
                    // 1st page shouldn't shift if the 2nd page is definitely an end page
                    0 -> extraPage?.endPageConfidence != 3 || page.endPageConfidence == 3
                    else -> false
                }
            ) {
                shiftDoublePages(true)
                extraPage = null
                return supportHingeIfThere(source)
            }
        } else if (shouldShiftAnyway && (page.index == 0 || page.index == 2)) {
            // if for some reason the first page should be by itself but its not, fix that
            shiftDoublePages(true)
            extraPage = null
            return supportHingeIfThere(source)
        } else if (shouldShiftAnyway && page.index == 1 &&
            viewer.config.shiftDoublePage && (isFirstPageNotEnd && isThirdPageNotEnd)
        ) {
            shiftDoublePages(false)
            return supportHingeIfThere(source)
        }

        val imageBitmap2 = earlyImageBitmap2 ?: try {
            BitmapFactory.decodeStream(source2.peek().inputStream())
        } catch (e: Exception) {
            closeSources(source2)
            extraPage?.fullPage = true
            page.isolatedPage = true
            splitDoublePages()
            Timber.e("Cannot combine pages ${e.message}")
            return supportHingeIfThere(source)
        }
        scope.launchUI { progressBar.setProgress(97) }
        val height2 = imageBitmap2.height
        val width2 = imageBitmap2.width

        if (height2 < width2) {
            closeSources(source2)
            extraPage?.fullPage = true
            page.isolatedPage = true
            splitDoublePages()
            return supportHingeIfThere(source)
        }
        val bg = ThemeUtil.readerBackgroundColor(viewer.config.readerTheme)
        closeSources(source2)
        extraPage?.let { extraPage ->
            val shouldSubShiftAnyway = !viewer.activity.manuallyShiftedPages &&
                extraPage.isStartPage == true && extraPage.endPageConfidence == 0
            if (extraPage.index <= 2 && extraPage.endPageConfidence != 3 &&
                extraPage.isStartPage == null && extraPage.fullPage == null
            ) {
                extraPage.startPageConfidence = imageBitmap2.isPagePadded(rightSide = isLTR)
                if (extraPage.isStartPage == true) {
                    if (extraPage.endPageConfidence != null) {
                        extraPage.endPageConfidence = 0
                    }
                    shiftDoublePages(page.index == 0 || pages?.get(0)?.fullPage == true)
                    this.extraPage = null
                    return supportHingeIfThere(source)
                }
            } else if (shouldSubShiftAnyway && page.index == 1 && !viewer.config.shiftDoublePage) {
                shiftDoublePages(true)
                return supportHingeIfThere(source)
            }
        }
        // If page has been removed in another thread, don't show it
        if (extraPage == null) {
            return supportHingeIfThere(source)
        }
        return ImageUtil.mergeBitmaps(imageBitmap, imageBitmap2, isLTR, bg, viewer.config.hingeGapSize, context) {
            scope.launchUI {
                if (it == 100) {
                    progressBar.completeAndFadeOut()
                } else {
                    progressBar.setProgress(it)
                }
            }
        }
    }

    private fun setExtraPageBitmap(stream: InputStream, isLTR: Boolean): Bitmap? {
        val earlyImageBitmap2 = try {
            BitmapFactory.decodeStream(stream)
        } catch (_: Exception) {
            return null
        }
        val paddedPageConfidence = earlyImageBitmap2.isPagePadded(rightSide = !isLTR)
        if (paddedPageConfidence == 3) {
            extraPage?.endPageConfidence = paddedPageConfidence
        }
        return earlyImageBitmap2
    }

    private suspend fun supportHingeIfThere(source: BufferedSource): BufferedSource {
        if (viewer.config.hingeGapSize > 0 && !ImageUtil.isAnimatedAndSupported(source)) {
            val imageBitmap = try {
                BitmapFactory.decodeStream(source.peek().inputStream())
            } catch (e: Exception) {
                // closeSources(imageStream)
                val wasNotFullPage = page.fullPage != true
                page.fullPage = true
                if (wasNotFullPage) {
                    splitDoublePages()
                }
                return source
            }
            val isLTR = (viewer !is R2LPagerViewer).xor(viewer.config.invertDoublePages)
            val bg = ThemeUtil.readerBackgroundColor(viewer.config.readerTheme)
            return ImageUtil.padSingleImage(
                imageBitmap = imageBitmap,
                isLTR = isLTR,
                atBeginning = if (viewer.config.doublePages) page.index == 0 else null,
                background = bg,
                hingeGap = viewer.config.hingeGapSize,
                context = context,
            )
        }
        return source
    }

    private suspend fun closeSources(source1: BufferedSource?, source2: BufferedSource? = null) {
        withContext(Dispatchers.IO) {
            source1?.close()
            source2?.close()
        }
    }

    private fun shiftDoublePages(shift: Boolean) {
        delayPageUpdate { viewer.activity.shiftDoublePages(shift, page) }
    }

    private fun splitDoublePages() {
        delayPageUpdate { viewer.splitDoublePages(page) }
    }

    private fun delayPageUpdate(callback: () -> Unit) {
        scope.launchUI {
            callback()
            if (extraPage?.fullPage == true || page.fullPage == true) {
                extraPage = null
            }
        }
    }

    private fun getBGType(readerTheme: Int, context: Context): Int {
        return if (ReaderBackgroundColor.fromPreference(readerTheme) == ReaderBackgroundColor.SMART_THEME) {
            if (context.isInNightMode()) 2 else 1
        } else {
            0 + (context.resources.configuration?.orientation ?: 0) * 10
        } + item.hashCode()
    }
}
