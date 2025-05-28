package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.LayoutInflater
import androidx.core.os.postDelayed
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import co.touchlab.kermit.Logger
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.ReaderErrorBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.settings.ReaderBackgroundColor
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderErrorView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderPageImageView
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressIndicator
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig.ZoomType
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.ImageUtil
import eu.kanade.tachiyomi.util.system.ImageUtil.isPagePadded
import eu.kanade.tachiyomi.util.system.ThemeUtil
import eu.kanade.tachiyomi.util.system.bottomCutoutInset
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.topCutoutInset
import eu.kanade.tachiyomi.util.system.withIOContext
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.util.view.backgroundColor
import eu.kanade.tachiyomi.util.view.isVisibleOnScreen
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import java.io.InputStream
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.BufferedSource
import uy.kohesive.injekt.injectLazy

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
    private val progressIndicator = ReaderProgressIndicator(context)

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

    private var status = Page.State.Ready
    private var extraStatus = Page.State.Ready
    private var progress: Int = 0
    private var extraProgress: Int = 0

    private var scope = MainScope()

    init {
        addView(progressIndicator)
        if (viewer.config.hingeGapSize > 0) {
            progressIndicator.updateLayoutParams<MarginLayoutParams> {
                marginStart = ((context.resources.displayMetrics.widthPixels) / 2 + viewer.config.hingeGapSize) / 2
            }
        }
        launchLoadJob()
        progressIndicator.setInvertMode(isInvertedFromTheme())
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
                    progressIndicator.setProgress(progress)
                } else {
                    progressIndicator.setProgress(((progress + extraProgress) / 2 * 0.95f).roundToInt())
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
                progressIndicator.setProgress(((progress + extraProgress) / 2 * 0.95f).roundToInt())
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
            handler.postDelayed(500) {
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
                (animateScaleAndCenter(min(targetScale, minScale * 2), point) ?: return@postDelayed)
                    .withDuration(500)
                    .withEasing(SubsamplingScaleImageView.EASE_IN_OUT_QUAD)
                    .withInterruptible(true)
                    .start()
            }
        }
    }

    /**
     * Called when the status of the page changes.
     *
     * @param status the new status of the page.
     */
    private suspend fun processStatus(status: Page.State) {
        when (status) {
            is Page.State.Queue -> setQueued()
            is Page.State.LoadPage -> setLoading()
            is Page.State.DownloadImage -> {
                launchProgressJob()
                setDownloading()
            }
            is Page.State.Ready -> {
                if (extraPage == null) {
                    setImage()
                }
                cancelProgressJob(1)
            }
            is Page.State.Error -> {
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
    private suspend fun processStatus2(status: Page.State) {
        when (status) {
            is Page.State.Queue -> setQueued()
            is Page.State.LoadPage -> setLoading()
            is Page.State.DownloadImage -> {
                launchProgressJob2()
                setDownloading()
            }
            is Page.State.Ready -> {
                setImage()
                cancelProgressJob(2)
            }
            is Page.State.Error -> {
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
     * Called when the page is queued.
     */
    private fun setQueued() {
        progressIndicator.show()
        errorLayout?.isVisible = false
    }

    /**
     * Called when the page is loading.
     */
    private fun setLoading() {
        progressIndicator.show()
        errorLayout?.isVisible = false
    }

    /**
     * Called when the page is downloading.
     */
    private fun setDownloading() {
        progressIndicator.show()
        errorLayout?.isVisible = false
    }

    /**
     * Called when the page is ready.
     */
    private suspend fun setImage() {
        progressIndicator.show()
        if (extraPage == null) {
            progressIndicator.completeAndFadeOut()
        } else {
            progressIndicator.setProgress(95)
        }
        errorLayout?.isVisible = false

        val streamFn = page.stream ?: return
        val streamFn2 = extraPage?.stream

        try {
            val (source, isAnimated) = withIOContext {
                streamFn().buffered(16).use { source1 ->
                    if (extraPage != null) {
                        streamFn2?.invoke()
                            ?.buffered(16)
                    } else {
                        null
                    }.use { source2 ->
                        val actualSource = this@PagerPageHolder.mergeOrSplitPages(
                            Buffer().readFrom(source1),
                            source2?.let { Buffer().readFrom(it) },
                        )

                        val isAnimated = ImageUtil.isAnimatedAndSupported(actualSource)
                        Pair(actualSource, isAnimated)
                    }
                }
            }

            withUIContext {
                val bgColor = ReaderBackgroundColor.fromPreference(viewer.config.readerTheme)
                if (!isAnimated) {
                    if (bgColor.isSmartColor) {
                        val bgType = getBGType(viewer.config.readerTheme, context)
                        if (page.bg != null && page.bgType == bgType) {
                            setImage(source, false, imageConfig)
                            pageView?.background = page.bg
                        }
                        // if the user switches to automatic when pages are already cached, the bg needs to be loaded
                        else {
                            val background =
                                try {
                                    setBG(source.peek().inputStream())
                                } catch (e: Exception) {
                                    Logger.e(e) { e.localizedMessage?.toString() ?: "" }
                                    ColorDrawable(Color.WHITE)
                                }
                            setImage(source, false, imageConfig)

                            pageView?.background = background
                            page.bg = pageView?.background
                            page.bgType = bgType
                        }
                    } else {
                        setImage(source, false, imageConfig)
                    }
                } else {
                    setImage(source, true, imageConfig)
                    if (bgColor.isSmartColor && page.bg != null) {
                        pageView?.background = page.bg
                    }
                }
            }
        } catch (e: Throwable) {
            val logMsg = "Failed to set reader page image"
            if (e is CancellationException)
                Logger.w(e) { logMsg }  // probably user exiting the reader page before the image loads
            else
                Logger.e(e) { logMsg }
            withUIContext {
                setError()
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
            debugMode = viewer.config.debugMode,
        )

    private suspend fun setBG(stream: InputStream): Drawable {
        return withContext(Default) {
            val preferences by injectLazy<PreferencesHelper>()
            ImageUtil.autoSetBackground(
                BitmapFactory.decodeStream(stream),
                preferences.readerTheme().get() == 2,
                context,
            )
        }
    }

    /**
     * Called when the page has an error.
     */
    private fun setError() {
        progressIndicator.hide()
        showErrorLayout(false)
    }

    /**
     * Called when the image is decoded and going to be displayed.
     */
    private fun onImageDecoded() {
        progressIndicator.hide()
    }

    /**
     * Called when an image fails to decode.
     */
    private fun onImageDecodeError() {
        progressIndicator.hide()
        showErrorLayout(true)
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

    private suspend fun mergeOrSplitPages(imageSource: BufferedSource, imageSource2: BufferedSource?): BufferedSource {
        if (ImageUtil.isAnimatedAndSupported(imageSource)) {
            // FIXME: Animated images is duplicating instead of being split
            if (page.longPage == null) {
                page.longPage = true
                /*
                if (viewer.config.splitPages || imageSource2 != null) {
                    splitDoublePages()
                }
                 */
            }
            scope.launchUI { progressIndicator.completeAndFadeOut() }
            return imageSource
        }

        // Try to split
        if (page.longPage == true && viewer.config.splitPages) {
            val imageBitmap = try {
                BitmapFactory.decodeStream(imageSource.peek().inputStream())
            } catch (e: Exception) {
                Logger.e { "Cannot split page ${e.message}" }
                return imageSource
            }
            val isLTR = (viewer !is R2LPagerViewer).xor(viewer.config.invertDoublePages)
            return try {
                ImageUtil.splitBitmap(imageBitmap, (page.firstHalf == false).xor(!isLTR)) {
                    scope.launchUI {
                        if (it == 100) {
                            progressIndicator.completeAndFadeOut()
                        } else {
                            progressIndicator.setProgress(it)
                        }
                    }
                }
            } catch (e: NullPointerException) {
                Logger.e(e) { "Cannot split page" }
                imageSource
            }
        }

        if (imageSource2 == null) {
            // Detect if page is wide enough to be split
            if (viewer.config.splitPages && page.longPage == null) {
                val imageBitmap = try {
                    BitmapFactory.decodeStream(imageSource.peek().inputStream())
                } catch (e: Exception) {
                    page.longPage = true
                    splitDoublePages()
                    Logger.e { "Cannot split page ${e.message}" }
                    return imageSource
                }

                val height: Int
                val width: Int

                try {
                    height = imageBitmap.height
                    width = imageBitmap.width
                } catch (e: NullPointerException) {
                    page.longPage = true
                    splitDoublePages()
                    Logger.e(e) { "Unable to retrieve bitmap size" }
                    return imageSource
                }

                return if (height < width) {
                    closeSources(imageSource)
                    page.longPage = true
                    splitDoublePages()
                    val isLTR = (viewer !is R2LPagerViewer).xor(viewer.config.invertDoublePages)
                    return ImageUtil.splitBitmap(imageBitmap, !isLTR) {
                        scope.launchUI {
                            if (it == 100) {
                                progressIndicator.completeAndFadeOut()
                            } else {
                                progressIndicator.setProgress(it)
                            }
                        }
                    }
                } else {
                    page.longPage = false
                    imageSource
                }
            }
            return supportHingeIfThere(imageSource)
        }

        // Too wide to be merged
        if (page.fullPage == true) return supportHingeIfThere(imageSource)

        val imageBitmap = try {
            BitmapFactory.decodeStream(imageSource.peek().inputStream())
        } catch (e: Exception) {
            closeSources(imageSource2)
            page.fullPage = true
            splitDoublePages()
            Logger.e { "Cannot combine pages ${e.message}" }
            return supportHingeIfThere(imageSource)
        }
        scope.launchUI { progressIndicator.setProgress(96) }
        val height = imageBitmap.height
        val width = imageBitmap.width

        val isLTR = (viewer !is R2LPagerViewer).xor(viewer.config.invertDoublePages)

        val pages = page.chapter.pages
        if (height < width) {
            if (extraPage?.index == 1) {
                setExtraPageBitmap(imageSource2.inputStream(), isLTR)
            }
            closeSources(imageSource2)
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
            return supportHingeIfThere(imageSource)
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
                earlyImageBitmap2 = setExtraPageBitmap(imageSource2.peek().inputStream(), isLTR)
            }
            if (page.index == 1 && page.isEndPage == true && viewer.config.shiftDoublePage &&
                (isFirstPageNotEnd || isThirdPageNotEnd)
            ) {
                shiftDoublePages(false)
                return supportHingeIfThere(imageSource)
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
                return supportHingeIfThere(imageSource)
            }
        } else if (shouldShiftAnyway && (page.index == 0 || page.index == 2)) {
            // if for some reason the first page should be by itself but its not, fix that
            shiftDoublePages(true)
            extraPage = null
            return supportHingeIfThere(imageSource)
        } else if (shouldShiftAnyway && page.index == 1 &&
            viewer.config.shiftDoublePage && (isFirstPageNotEnd && isThirdPageNotEnd)
        ) {
            shiftDoublePages(false)
            return supportHingeIfThere(imageSource)
        }

        val imageBitmap2 = earlyImageBitmap2 ?: try {
            BitmapFactory.decodeStream(imageSource2.peek().inputStream())
        } catch (e: Exception) {
            closeSources(imageSource2)
            extraPage?.fullPage = true
            page.isolatedPage = true
            splitDoublePages()
            Logger.e { "Cannot combine pages ${e.message}" }
            return supportHingeIfThere(imageSource)
        }
        scope.launchUI { progressIndicator.setProgress(97) }
        val height2 = imageBitmap2.height
        val width2 = imageBitmap2.width

        if (height2 < width2) {
            closeSources(imageSource2)
            extraPage?.fullPage = true
            page.isolatedPage = true
            splitDoublePages()
            return supportHingeIfThere(imageSource)
        }
        val bg = ThemeUtil.readerBackgroundColor(viewer.config.readerTheme)
        closeSources(imageSource2)
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
                    return supportHingeIfThere(imageSource)
                }
            } else if (shouldSubShiftAnyway && page.index == 1 && !viewer.config.shiftDoublePage) {
                shiftDoublePages(true)
                return supportHingeIfThere(imageSource)
            }
        }
        // If page has been removed in another thread, don't show it
        if (extraPage == null) {
            return supportHingeIfThere(imageSource)
        }
        return ImageUtil.mergeBitmaps(imageBitmap, imageBitmap2, isLTR, bg, viewer.config.hingeGapSize, context) {
            scope.launchUI {
                if (it == 100) {
                    progressIndicator.completeAndFadeOut()
                } else {
                    progressIndicator.setProgress(it)
                }
            }
        }
    }

    private fun setExtraPageBitmap(imageStream: InputStream, isLTR: Boolean): Bitmap? {
        val earlyImageBitmap2 = try {
            BitmapFactory.decodeStream(imageStream)
        } catch (_: Exception) {
            return null
        }
        val paddedPageConfidence = earlyImageBitmap2.isPagePadded(rightSide = !isLTR)
        if (paddedPageConfidence == 3) {
            extraPage?.endPageConfidence = paddedPageConfidence
        }
        return earlyImageBitmap2
    }

    private suspend fun supportHingeIfThere(imageSource: BufferedSource): BufferedSource {
        if (viewer.config.hingeGapSize > 0 && !ImageUtil.isAnimatedAndSupported(imageSource)) {
            val imageBitmap = try {
                BitmapFactory.decodeStream(imageSource.peek().inputStream())
            } catch (e: Exception) {
                val wasNotFullPage = page.fullPage != true
                page.fullPage = true
                if (wasNotFullPage) {
                    splitDoublePages()
                }
                return imageSource
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
        return imageSource
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
