package eu.kanade.tachiyomi.ui.manga

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.chooser.ChooserAction
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.ColorUtils
import androidx.core.net.toFile
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.touchlab.kermit.Logger
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.SizeResolver
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import dev.icerock.moko.resources.StringResource
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.SelectableAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.coil.getBestColor
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.seriesType
import eu.kanade.tachiyomi.data.database.models.vibrantCoverColor
import eu.kanade.tachiyomi.data.download.DownloadJob
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.databinding.MangaDetailsControllerBinding
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.icon
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.MaterialMenuSheet
import eu.kanade.tachiyomi.ui.base.SmallToolbarInterface
import eu.kanade.tachiyomi.ui.base.controller.BaseCoroutineController
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.library.FilteredLibraryController
import eu.kanade.tachiyomi.ui.library.LibraryController
import eu.kanade.tachiyomi.ui.main.FloatingSearchInterface
import eu.kanade.tachiyomi.ui.main.HingeSupportedController
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.main.SearchActivity
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterHolder
import eu.kanade.tachiyomi.ui.manga.chapter.ChapterItem
import eu.kanade.tachiyomi.ui.manga.chapter.ChaptersSortBottomSheet
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.tachiyomi.ui.manga.track.TrackingBottomSheet
import eu.kanade.tachiyomi.ui.migration.manga.design.PreMigrationController
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.recents.RecentsController
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.source.BrowseController
import eu.kanade.tachiyomi.ui.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.addOrRemoveToFavorites
import eu.kanade.tachiyomi.util.chapter.updateTrackChapterMarkedAsRead
import eu.kanade.tachiyomi.util.isLocal
import eu.kanade.tachiyomi.util.moveCategories
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.addCheckBoxPrompt
import eu.kanade.tachiyomi.util.system.contextCompatColor
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.e
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.ignoredSystemInsets
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.system.isLandscape
import eu.kanade.tachiyomi.util.system.isOnline
import eu.kanade.tachiyomi.util.system.isPromptChecked
import eu.kanade.tachiyomi.util.system.isTablet
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.system.setCustomTitleAndMessage
import eu.kanade.tachiyomi.util.system.timeSpanFromNow
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withUIContext
import eu.kanade.tachiyomi.util.view.activityBinding
import eu.kanade.tachiyomi.util.view.copyToClipboard
import eu.kanade.tachiyomi.util.view.findChild
import eu.kanade.tachiyomi.util.view.getText
import eu.kanade.tachiyomi.util.view.isControllerVisible
import eu.kanade.tachiyomi.util.view.previousController
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.setAction
import eu.kanade.tachiyomi.util.view.setMessage
import eu.kanade.tachiyomi.util.view.setNegativeButton
import eu.kanade.tachiyomi.util.view.setOnQueryTextChangeListener
import eu.kanade.tachiyomi.util.view.setPositiveButton
import eu.kanade.tachiyomi.util.view.setStyle
import eu.kanade.tachiyomi.util.view.setTextColorAlpha
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.toolbarHeight
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import eu.kanade.tachiyomi.widget.LinearLayoutManagerAccurateOffset
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import yokai.domain.manga.models.cover
import yokai.i18n.MR
import yokai.presentation.core.Constants
import yokai.util.lang.getString
import android.R as AR

class MangaDetailsController :
    BaseCoroutineController<MangaDetailsControllerBinding, MangaDetailsPresenter>,
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener,
    ActionMode.Callback,
    MangaDetailsAdapter.MangaDetailsInterface,
    SmallToolbarInterface,
    HingeSupportedController,
    FlexibleAdapter.OnItemMoveListener {

    constructor(
        manga: Manga?,
        fromCatalogue: Boolean = false,
        smartSearchConfig: BrowseController.SmartSearchConfig? = null,
        update: Boolean = false,
        shouldLockIfNeeded: Boolean = false,
    ) : super(bundle(manga?.id, fromCatalogue, smartSearchConfig, update)) {
        this.shouldLockIfNeeded = shouldLockIfNeeded
        this.presenter = MangaDetailsPresenter(manga?.id!!).apply { setCurrentManga(manga) }
    }

    constructor(
        mangaId: Long,
        fromCatalogue: Boolean = false,
        smartSearchConfig: BrowseController.SmartSearchConfig? = null,
        update: Boolean = false,
        shouldLockIfNeeded: Boolean = false,
    ) : super(bundle(mangaId, fromCatalogue, smartSearchConfig, update)) {
        this.shouldLockIfNeeded = shouldLockIfNeeded
        this.presenter = MangaDetailsPresenter(mangaId)
    }

    constructor(bundle: Bundle) : this(bundle.getLong(Constants.MANGA_EXTRA)) {
        val notificationId = bundle.getInt("notificationId", -1)
        val context = applicationContext ?: return
        if (notificationId > -1) {
            NotificationReceiver.dismissNotification(
                context,
                notificationId,
                bundle.getInt("groupId", 0),
            )
        }
    }

    private val manga: Manga? get() = if (presenter.isMangaLateInitInitialized()) presenter.manga else null
    private var colorAnimator: ValueAnimator? = null
    override val presenter: MangaDetailsPresenter
    private var coverColor: Int? = null
    private var accentColor: Int? = null
    private var headerColor: Int? = null
    private var toolbarIsColored = false
    private var snack: Snackbar? = null
    val shouldLockIfNeeded: Boolean
    val fromCatalogue = args.getBoolean(FROM_CATALOGUE_EXTRA, false)
    private var trackingBottomSheet: TrackingBottomSheet? = null
    private var startingRangeChapterPos: Int? = null
    private var rangeMode: RangeMode? = null
    private var editMangaDialog: EditMangaDialog? = null
    var refreshTracker: Int? = null
    private var chapterPopupMenu: Pair<Int, PopupMenu>? = null
    private var isPushing = true

    // Tablet Layout
    var isTablet = false
    private var tabletAdapter: MangaDetailsAdapter? = null

    private var query = ""
    private var adapter: MangaDetailsAdapter? = null

    private var actionMode: ActionMode? = null

    private var headerHeight = 0
    private var fullCoverActive = false
    var returningFromReader = false
    private var floatingActionMode: android.view.ActionMode? = null

    override fun getTitle(): String? {
        return manga?.title
    }

    override fun createBinding(inflater: LayoutInflater) =
        MangaDetailsControllerBinding.inflate(inflater)

    //region UI Methods
    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        coverColor = null
        fullCoverActive = false
        setAccentColorValue()
        setHeaderColorValue()

        setTabletMode(view)
        setRecycler(view)
        ViewCompat.setOnApplyWindowInsetsListener(binding.fab) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.updateLayoutParams {
                if (this is CoordinatorLayout.LayoutParams) {
                    bottomMargin = systemBars.bottom + 16.dpToPx
                }
            }

            insets
        }
        setPaletteColor()
        adapter?.fastScroller = binding.fastScroller
        binding.fastScroller.addOnScrollStateChangeListener {
            activityBinding?.appBar?.y = 0f
        }
        binding.recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                if (isTablet) return

                val headerBinding = getHeader()?.binding
                if (headerBinding == null) {
                    if (binding.fab.isEnabled) {
                        binding.fab.show()
                    } else {
                        binding.fab.isVisible = false
                    }
                    return
                }

                if (!headerBinding.startReadingButton.isVisible || !headerBinding.startReadingButton.isEnabled) {
                    binding.fab.isVisible = false
                    return
                }

                val bound = Rect()
                binding.recycler.getHitRect(bound)
                if (headerBinding.startReadingButton.getLocalVisibleRect(bound)) {
                    binding.fab.hide()
                } else {
                    binding.fab.show()
                }
            }
        })
        binding.fab.transitionName = "details start reading transition"
        binding.fab.setOnClickListener { readNextChapter(it) }

        presenter.onCreateLate()
        binding.swipeRefresh.isRefreshing = presenter.isLoading
        binding.swipeRefresh.setOnRefreshListener { presenter.refreshAll() }
        updateToolbarTitleAlpha()
        if (presenter.preferences.themeMangaDetails().get()) {
            setItemColors()
        }
    }

    private fun setAccentColorValue(colorToUse: Int? = null) {
        val context = view?.context ?: return
        setCoverColorValue(colorToUse)
        accentColor = if (presenter.preferences.themeMangaDetails().get()) {
            (colorToUse ?: manga?.vibrantCoverColor)?.let {
                val luminance = ColorUtils.calculateLuminance(it).toFloat()
                if (if (!context.isInNightMode()) luminance > 0.4 else luminance <= 0.6) {
                    ColorUtils.blendARGB(
                        it,
                        context.contextCompatColor(R.color.colorOnDownloadBadgeDayNight),
                        (if (!context.isInNightMode()) luminance else -(luminance - 1))
                            .toFloat() * if (context.isInNightMode()) 0.33f else 0.5f,
                    )
                } else {
                    it
                }
            }
        } else {
            null
        }
    }

    private fun setCoverColorValue(colorToUse: Int? = null) {
        val context = view?.context ?: return
        val colorBack = context.getResourceColor(R.attr.background)
        coverColor =
            (
                if (presenter.preferences.themeMangaDetails().get()) {
                    (colorToUse ?: manga?.vibrantCoverColor)
                } else {
                    ColorUtils.blendARGB(
                        context.getResourceColor(R.attr.colorSecondary),
                        colorBack,
                        0.5f,
                    )
                }
                )?.let {
                // this makes the color more consistent regardless of theme
                val dominant = it
                val domLum = ColorUtils.calculateLuminance(dominant)
                val lumWrongForTheme =
                    (if (context.isInNightMode()) domLum > 0.8 else domLum <= 0.2)
                ColorUtils.blendARGB(
                    it,
                    colorBack,
                    if (lumWrongForTheme) 0.9f else 0.7f,
                )
            }
    }

    private fun setRefreshStyle() {
        with(binding.swipeRefresh) {
            if (presenter.preferences.themeMangaDetails().get() && accentColor != null && headerColor != null) {
                val newColor = makeColorFrom(
                    hueOf = accentColor!!,
                    satAndLumOf = context.getResourceColor(R.attr.actionBarTintColor),
                )
                setColorSchemeColors(newColor)
                setProgressBackgroundColorSchemeColor(headerColor!!)
            } else {
                setStyle()
            }
        }
    }

    private fun setHeaderColorValue(colorToUse: Int? = null) {
        val context = view?.context ?: return
        headerColor = if (presenter.preferences.themeMangaDetails().get()) {
            (colorToUse ?: manga?.vibrantCoverColor)?.let { color ->
                val newColor =
                    makeColorFrom(color, context.getResourceColor(R.attr.colorPrimaryVariant))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 || context.isInNightMode()) {
                    activity?.window?.navigationBarColor = ColorUtils.setAlphaComponent(
                        newColor,
                        Color.alpha(activity?.window?.navigationBarColor ?: Color.BLACK),
                    )
                }
                newColor
            }
        } else {
            null
        }
        setRefreshStyle()
    }

    @ColorInt
    private fun makeColorFrom(@ColorInt hueOf: Int, @ColorInt satAndLumOf: Int): Int {
        val satLumArray = FloatArray(3)
        val hueArray = FloatArray(3)
        ColorUtils.colorToHSL(satAndLumOf, satLumArray)
        ColorUtils.colorToHSL(hueOf, hueArray)
        return ColorUtils.HSLToColor(
            floatArrayOf(
                hueArray[0],
                satLumArray[1],
                satLumArray[2],
            ),
        )
    }

    private fun setItemColors() {
        getHeader()?.updateColors()

        if ((adapter?.itemCount ?: 0) > 1) {
            if (isTablet) {
                val chapterHolder = binding.recycler.findViewHolderForAdapterPosition(0) as? MangaHeaderHolder
                chapterHolder?.updateColors()
            }
            (presenter.chapters).forEach { chapter ->
                val chapterHolder =
                    binding.recycler.findViewHolderForItemId(chapter.id!!) as? ChapterHolder
                        ?: return@forEach
                chapterHolder.notifyStatus(
                    chapter.status,
                    isLocked(),
                    chapter.progress,
                )
            }
        }

        val context = view?.context ?: return
        val backgroundColor = accentColor ?: return

        val states = arrayOf(
            intArrayOf(-AR.attr.state_enabled),
            intArrayOf(),
        )
        val colors = intArrayOf(
            ColorUtils.setAlphaComponent(context.getResourceColor(R.attr.tabBarIconInactive), 43),
            backgroundColor,
        )
        binding.fab.backgroundTintList = ColorStateList(states, colors)
        val textColors = intArrayOf(
            ColorUtils.setAlphaComponent(context.getResourceColor(R.attr.colorOnSurface), 97),
            context.getResourceColor(AR.attr.textColorPrimaryInverse),
        )
        binding.fab.setTextColor(ColorStateList(states, textColors))
    }

    /** Check if device is tablet, and use a second recycler to hold the details header if so */
    private fun setTabletMode(view: View) {
        isTablet = view.context.isTablet() && view.context.isLandscape()
        binding.tabletOverlay.isVisible = isTablet
        binding.tabletRecycler.isVisible = isTablet
        binding.tabletDivider.isVisible = isTablet
        if (isTablet) {
            binding.tabletRecycler.itemAnimator = null
            binding.recycler.updateLayoutParams<ViewGroup.LayoutParams> { width = 0 }
            tabletAdapter = MangaDetailsAdapter(this)
            binding.tabletRecycler.adapter = tabletAdapter
            binding.tabletRecycler.layoutManager = LinearLayoutManager(view.context)
            updateForHinge()
        }
    }

    override fun updateForHinge() {
        if (isTablet) {
            val hingeGapSize = (activity as? MainActivity)?.hingeGapSize?.takeIf { it > 0 }
            if (hingeGapSize != null) {
                binding.tabletDivider.updateLayoutParams<ViewGroup.LayoutParams> {
                    width = hingeGapSize
                }
                binding.tabletRecycler.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    matchConstraintPercentWidth = 1f
                    width = 0
                    matchConstraintDefaultWidth =
                        ConstraintLayout.LayoutParams.MATCH_CONSTRAINT_SPREAD
                }
                val swipeCircle = binding.swipeRefresh.findChild<ImageView>()
                swipeCircle?.translationX =
                    (activity!!.window.decorView.width / 2 + hingeGapSize) /
                    2f
            } else {
                binding.tabletRecycler.updateLayoutParams<ConstraintLayout.LayoutParams> { matchConstraintPercentWidth = 0.4f }
            }
        }
    }

    override fun onDestroyView(view: View) {
        snack?.dismiss()
        adapter = null
        finishFloatingActionMode()
        trackingBottomSheet = null
        super.onDestroyView(view)
    }

    /** Set adapter, insets, and scroll listener for recycler view */
    @SuppressLint("ClickableViewAccessibility")
    private fun setRecycler(view: View) {
        adapter = MangaDetailsAdapter(this)

        binding.recycler.adapter = adapter
        adapter?.isSwipeEnabled = true
        binding.recycler.layoutManager = LinearLayoutManagerAccurateOffset(view.context)
        binding.recycler.addItemDecoration(
            MangaDetailsDivider(view.context),
        )
        binding.recycler.setHasFixedSize(true)
        val appbarHeight = activityBinding?.appBar?.attrToolbarHeight ?: 0
        val offset = 10.dpToPx
        binding.swipeRefresh.setDistanceToTriggerSync(70.dpToPx)

        if (isTablet) {
            val tHeight = toolbarHeight.takeIf { (it ?: 0) > 0 } ?: appbarHeight
            val insetsCompat =
                view.rootWindowInsetsCompat ?: activityBinding?.root?.rootWindowInsetsCompat
            headerHeight = tHeight + (insetsCompat?.getInsets(systemBars())?.top ?: 0)
            binding.recycler.updatePaddingRelative(top = headerHeight + 4.dpToPx)
        }
        scrollViewWith(
            binding.recycler,
            padBottom = true,
            customPadding = true,
            swipeRefreshLayout = binding.swipeRefresh,
            afterInsets = { insets ->
                setInsets(insets, appbarHeight, offset)
            },
            liftOnScroll = {
                colorToolbar(it)
            },
        )
        binding.recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (!isTablet) {
                        updateToolbarTitleAlpha(isScrollingDown = dy > 0 && !binding.root.context.isTablet())
                        val atTop = !recyclerView.canScrollVertically(-1)
                        val tY = getHeader()?.binding?.backdrop?.translationY ?: 0f
                        getHeader()?.binding?.backdrop?.translationY = max(0f, tY + dy * 0.25f)
                        if (atTop) getHeader()?.binding?.backdrop?.translationY = 0f
                    }
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    val atTop = !recyclerView.canScrollVertically(-1)
                    updateToolbarTitleAlpha()
                    if (atTop) getHeader()?.binding?.backdrop?.translationY = 0f
                }
            },
        )

        binding.touchView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                finishFloatingActionMode()
                val hingeGapSize = (activity as? MainActivity)?.hingeGapSize?.takeIf { it > 0 }
                if (hingeGapSize != null) {
                    val swipeCircle = binding.swipeRefresh.findChild<ImageView>()
                    swipeCircle?.translationX = (binding.root.width / 2 + hingeGapSize) / 2 *
                        (if (event.x > binding.root.width / 2) 1 else -1).toFloat()
                }
            }
            false
        }
    }

    private fun finishFloatingActionMode() {
        floatingActionMode ?: return
        floatingActionMode?.finish()
        floatingActionMode = null
    }

    private fun setInsets(insets: WindowInsetsCompat, appbarHeight: Int, offset: Int) {
        val systemInsets = insets.ignoredSystemInsets
        binding.recycler.updatePaddingRelative(bottom = systemInsets.bottom)
        binding.tabletRecycler.updatePaddingRelative(bottom = systemInsets.bottom)
        val tHeight = toolbarHeight.takeIf { it ?: 0 > 0 } ?: appbarHeight
        headerHeight = tHeight + systemInsets.top
        binding.swipeRefresh.setProgressViewOffset(false, (-40).dpToPx, headerHeight + offset)
        if (isTablet) {
            binding.tabletOverlay.updateLayoutParams<ViewGroup.LayoutParams> {
                height = headerHeight
            }
            // 4dp extra to line up chapter header and manga header
            binding.recycler.updatePaddingRelative(top = headerHeight + 4.dpToPx)
        }
        getHeader()?.setTopHeight(headerHeight)
        binding.fastScroller.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = headerHeight
            bottomMargin = systemInsets.bottom
        }
        binding.fastScroller.scrollOffset = headerHeight
    }

    /** Set the toolbar to fully transparent or colored and translucent */
    private fun colorToolbar(isColor: Boolean, animate: Boolean = true) {
        if (isColor == toolbarIsColored || (isTablet && isColor)) return
        val activity = activity ?: return
        toolbarIsColored = isColor
        if (isControllerVisible) setTitle()
        if (actionMode != null) {
            return
        }
        val scrollingColor = headerColor ?: activity.getResourceColor(R.attr.colorPrimaryVariant)
        val topColor = ColorUtils.setAlphaComponent(scrollingColor, 0)
        val scrollingStatusColor =
            ColorUtils.setAlphaComponent(scrollingColor, (0.87f * 255).roundToInt())
        colorAnimator?.cancel()
        if (animate) {
            val cA = ValueAnimator.ofFloat(
                if (toolbarIsColored) 0f else 1f,
                if (toolbarIsColored) 1f else 0f,
            )
            colorAnimator = cA
            colorAnimator?.duration = 250 // milliseconds
            colorAnimator?.addUpdateListener { animator ->
                activityBinding?.appBar?.setBackgroundColor(
                    ColorUtils.blendARGB(
                        topColor,
                        scrollingColor,
                        animator.animatedValue as Float,
                    ),
                )
                activity.window?.statusBarColor = if (toolbarIsColored) {
                    ColorUtils.blendARGB(
                        topColor,
                        scrollingStatusColor,
                        animator.animatedValue as Float,
                    )
                } else {
                    Color.TRANSPARENT
                }
            }
            cA.start()
        } else {
            activityBinding?.appBar?.setBackgroundColor(if (toolbarIsColored) scrollingColor else topColor)
            activity.window?.statusBarColor =
                if (toolbarIsColored) scrollingStatusColor else topColor
        }
    }

    /** Get the color of the manga cover*/
    fun setPaletteColor() {
        val view = view ?: return

        val request = ImageRequest.Builder(view.context)
            .data(presenter.manga.cover())
            .size(SizeResolver.ORIGINAL)
            .allowHardware(false)
            .target(
                onSuccess = { image ->
                    val drawable = image.asDrawable(view.context.resources)

                    val copy = (drawable as? BitmapDrawable)?.let {
                        BitmapDrawable(
                            view.context.resources,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                it.bitmap.copy(Bitmap.Config.HARDWARE, false)
                            else
                                it.bitmap.copy(it.bitmap.config!!, false),
                        )
                    } ?: drawable

                    // Don't use 'copy', Palette doesn't like its bitmap, could be caused by mutability is disabled,
                    // or perhaps because it's HARDWARE configured, not entirely sure why, the behaviour is not
                    // documented by Google.
                    val bitmap = (drawable as? BitmapDrawable)?.bitmap
                    // Generate the Palette on a background thread.
                    if (bitmap != null) {
                        Palette.from(bitmap).generate { palette ->
                            if (presenter.preferences.themeMangaDetails().get()) {
                                launchUI {
                                    val vibrantColor = palette?.getBestColor() ?: return@launchUI
                                    manga?.vibrantCoverColor = vibrantColor
                                    setAccentColorValue(vibrantColor)
                                    setHeaderColorValue(vibrantColor)
                                    setItemColors()
                                }
                            } else {
                                setCoverColorValue()
                                coverColor?.let { color -> getHeader()?.setBackDrop(color) }
                            }
                        }
                    }
                    binding.mangaCoverFull.setImageDrawable(copy)
                    getHeader()?.updateCover(manga!!)
                },
                onError = {
                    val file = presenter.coverCache.getCoverFile(manga!!.thumbnail_url, !manga!!.favorite)
                    if (file != null && file.exists()) {
                        file.delete()
                        setPaletteColor()
                    }
                },
            ).build()
        view.context.imageLoader.enqueue(request)
    }

    private fun setStatusBarAndToolbar() {
        val topColor = Color.TRANSPARENT
        val scrollingColor = headerColor ?: activity!!.getResourceColor(R.attr.colorPrimaryVariant)
        val scrollingStatusColor =
            ColorUtils.setAlphaComponent(scrollingColor, (0.87f * 255).roundToInt())
        activity?.window?.statusBarColor = if (toolbarIsColored) scrollingStatusColor else topColor
        activityBinding?.appBar?.setBackgroundColor(
            if (toolbarIsColored) scrollingColor else topColor,
        )
    }

    //endregion

    //region Lifecycle methods
    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        if (adapter != null && !isPushing) {
            presenter.isLockedFromSearch =
                shouldLockIfNeeded && SecureActivityDelegate.shouldBeLocked()
            presenter.headerItem.isLocked = presenter.isLockedFromSearch
            runBlocking { presenter.refreshMangaFromDb() }
            presenter.syncData()
            presenter.fetchChapters(refreshTracker == null)
            if (refreshTracker != null) {
                trackingBottomSheet?.refreshItem(refreshTracker ?: 0)
                presenter.refreshTracking(trackIndex = refreshTracker)
                refreshTracker = null
            }
            // fetch cover again in case the user set a new cover while reading
            setPaletteColor()
        }
        if (isControllerVisible) {
            setStatusBarAndToolbar()
            val searchView =
                activityBinding?.toolbar?.menu?.findItem(R.id.action_search)?.actionView as? SearchView
            searchView?.post {
                setSearchViewListener(searchView)
            }
        }
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        if (!returningFromReader) return
        returningFromReader = false
        runBlocking {
            val itemAnimator = binding.recycler.itemAnimator
            val chapters = withTimeoutOrNull(1000) { presenter.getChaptersNow() } ?: return@runBlocking
            binding.recycler.itemAnimator = null
            tabletAdapter?.notifyItemChanged(0)
            adapter?.setChapters(chapters)
            addMangaHeader()
            updateFab()
            binding.recycler.itemAnimator = itemAnimator
        }
    }

    override fun onChangeStarted(handler: ControllerChangeHandler, type: ControllerChangeType) {
        super.onChangeStarted(handler, type)
        isPushing = true
        if (type.isEnter) {
            if (isControllerVisible) {
                activityBinding?.appBar?.y = 0f
                activityBinding?.appBar?.updateAppBarAfterY(binding.recycler)
                updateToolbarTitleAlpha(0f)
                setStatusBarAndToolbar()
            }
        } else {
            if (router.backstack.lastOrNull()?.controller is DialogController) {
                return
            }
            colorAnimator?.cancel()

            getHeader()?.clearDescFocus()
            val colorSurface = activity?.getResourceColor(
                R.attr.colorSurface,
            ) ?: Color.BLACK
            if (router.backstackSize > 0 &&
                router.backstack.last().controller !is MangaDetailsController
            ) {
                if (router.backstack.last().controller !is FloatingSearchInterface) {
                    activityBinding?.appBar?.setBackgroundColor(colorSurface)
                }
                activity?.window?.statusBarColor = activity?.getResourceColor(
                    AR.attr.statusBarColor,
                ) ?: colorSurface
            }
        }
    }

    override fun onChangeEnded(
        changeHandler: ControllerChangeHandler,
        type: ControllerChangeType,
    ) {
        super.onChangeEnded(changeHandler, type)
        isPushing = false
        if (type == ControllerChangeType.PUSH_ENTER) {
            binding.swipeRefresh.isRefreshing = presenter.isLoading
        }
        if (!type.isEnter) {
            activityBinding?.root?.clearFocus()
        }
    }
    //endregion

    fun isNotOnline(showSnackbar: Boolean = true): Boolean {
        if (activity == null || !activity!!.isOnline()) {
            if (showSnackbar) view?.snack(MR.strings.no_network_connection)
            return true
        }
        return false
    }

    fun showError(message: String) {
        binding.swipeRefresh.isRefreshing = presenter.isLoading
        view?.snack(message)
    }

    fun showChaptersRemovedPopup(deletedChapters: List<ChapterItem>) {
        val context = activity ?: return
        val deleteRemovedPref = presenter.preferences.deleteRemovedChapters()
        when (deleteRemovedPref.get()) {
            2 -> {
                presenter.deleteChapters(deletedChapters, false)
                return
            }
            1 -> return
            else -> {
                val chapterNames = deletedChapters.map { it.name }
                context.materialAlertDialog()
                    .setCustomTitleAndMessage(
                        MR.strings.chapters_removed,
                        context.getString(
                            MR.plurals.deleted_chapters,
                            deletedChapters.size,
                            deletedChapters.size,
                            if (deletedChapters.size > 5) {
                                "${chapterNames.take(5 - 1).joinToString(", ")}, " +
                                    context.getString(
                                        MR.plurals.notification_and_n_more,
                                        (chapterNames.size - (4 - 1)),
                                        (chapterNames.size - (4 - 1)),
                                    )
                            } else {
                                chapterNames.joinToString(", ")
                            },
                        ),
                    )
                    .addCheckBoxPrompt(MR.strings.remember_this_choice)
                    .setPositiveButton(MR.strings.delete) { dialog, _ ->
                        presenter.deleteChapters(deletedChapters, false)
                        if (dialog.isPromptChecked) deleteRemovedPref.set(2)
                    }
                    .setNegativeButton(MR.strings.keep) { dialog, _ ->
                        if (dialog.isPromptChecked) deleteRemovedPref.set(1)
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    fun setRefresh(enabled: Boolean) {
        binding.swipeRefresh.isRefreshing = enabled
    }

    fun updateChapterDownload(download: Download) {
        getHolder(download.chapter)?.notifyStatus(
            download.status,
            presenter.isLockedFromSearch,
            download.progress,
            true,
        )
    }

    private fun getHolder(chapter: Chapter): ChapterHolder? {
        return binding.recycler.findViewHolderForItemId(chapter.id!!) as? ChapterHolder
    }

    private fun getHeader(): MangaHeaderHolder? {
        if (!isBindingInitialized) return null

        return if (isTablet) {
            binding.tabletRecycler.findViewHolderForAdapterPosition(0) as? MangaHeaderHolder
        } else {
            binding.recycler.findViewHolderForAdapterPosition(0) as? MangaHeaderHolder
        }
    }

    fun updateHeader() {
        binding.swipeRefresh.isRefreshing = presenter.isLoading
        adapter?.setChapters(presenter.chapters)
        tabletAdapter?.notifyItemChanged(0)
        addMangaHeader()
        updateMenuVisibility(activityBinding?.toolbar?.menu)
    }

    fun updateFab() {
        val context = view?.context ?: return
        val nextChapter = presenter.getNextUnreadChapter()
        binding.fab.isEnabled = nextChapter != null
        if (nextChapter != null) {
            binding.fab.text = context.getString(
                if (nextChapter.last_page_read > 0) {
                    MR.strings.resume
                } else {
                    MR.strings.read
                },
            )
        } else {
            binding.fab.isVisible = false
        }
    }

    fun updateChapters() {
        view ?: return
        binding.swipeRefresh.isRefreshing = presenter.isLoading
        tabletAdapter?.notifyItemChanged(0)
        adapter?.setChapters(presenter.chapters)
        addMangaHeader()
        updateFab()
        colorToolbar(binding.recycler.canScrollVertically(-1))
        updateMenuVisibility(activityBinding?.toolbar?.menu)
    }

    private fun addMangaHeader() {
        val tabletHeader = presenter.tabletChapterHeaderItem
        if (tabletHeader != null && tabletAdapter?.scrollableHeaders?.isEmpty() == true) {
            tabletAdapter?.removeAllScrollableHeaders()
            tabletAdapter?.addScrollableHeader(presenter.headerItem)
            adapter?.removeAllScrollableHeaders()
            adapter?.addScrollableHeader(tabletHeader)
        } else if (adapter?.scrollableHeaders?.isEmpty() == true) {
            adapter?.removeAllScrollableHeaders()
            adapter?.addScrollableHeader(presenter.headerItem)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun refreshAdapter() = adapter?.notifyDataSetChanged()

    override fun onItemClick(view: View?, position: Int): Boolean {
        val chapterItem = (adapter?.getItem(position) as? ChapterItem) ?: return false
        val chapter = chapterItem.chapter
        if (actionMode != null) {
            if (startingRangeChapterPos == null) {
                adapter?.addSelection(position)
                (binding.recycler.findViewHolderForAdapterPosition(position) as? BaseFlexibleViewHolder)
                    ?.toggleActivation()
                (binding.recycler.findViewHolderForAdapterPosition(position) as? ChapterHolder)
                    ?.notifyStatus(Download.State.CHECKED, false, 0)
                startingRangeChapterPos = position
                actionMode?.invalidate()
            } else {
                val rangeMode = rangeMode ?: return false
                val startingPosition = startingRangeChapterPos ?: return false
                var chapterList = listOf<ChapterItem>()
                when {
                    startingPosition > position ->
                        chapterList = presenter.chapters.subList(position - 1, startingPosition)
                    startingPosition <= position ->
                        chapterList = presenter.chapters.subList(startingPosition - 1, position)
                }
                when (rangeMode) {
                    RangeMode.Download -> downloadChapters(chapterList)
                    RangeMode.RemoveDownload -> massDeleteChapters(
                        chapterList.filter { it.status != Download.State.NOT_DOWNLOADED },
                        false,
                    )
                    RangeMode.Read -> markAsRead(chapterList)
                    RangeMode.Unread -> markAsUnread(chapterList)
                }
                presenter.fetchChapters(false)
                adapter?.removeSelection(startingPosition)
                (binding.recycler.findViewHolderForAdapterPosition(startingPosition) as? BaseFlexibleViewHolder)
                    ?.toggleActivation()
                startingRangeChapterPos = null
                this.rangeMode = null
                destroyActionModeIfNeeded()
            }
            return false
        }
        openChapter(chapter, view)

        return false
    }

    override fun onItemLongClick(position: Int) {
        val adapter = adapter ?: return
        val item = (adapter.getItem(position) as? ChapterItem) ?: return
        val descending = presenter.sortDescending()
        val items = mutableListOf(
            MaterialMenuSheet.MenuSheetItem(
                0,
                if (descending) R.drawable.ic_eye_down_24dp else R.drawable.ic_eye_up_24dp,
                MR.strings.mark_previous_as_read,
            ),
            MaterialMenuSheet.MenuSheetItem(
                1,
                if (descending) R.drawable.ic_eye_off_down_24dp else R.drawable.ic_eye_off_up_24dp,
                MR.strings.mark_previous_as_unread,
            ),
            MaterialMenuSheet.MenuSheetItem(
                2,
                R.drawable.ic_eye_range_24dp,
                MR.strings.mark_range_as_read,
            ),
            MaterialMenuSheet.MenuSheetItem(
                3,
                R.drawable.ic_eye_off_range_24dp,
                MR.strings.mark_range_as_unread,
            ),
        )
        if (presenter.getChapterUrl(item.chapter) != null) {
            items.add(
                0,
                MaterialMenuSheet.MenuSheetItem(
                    4,
                    R.drawable.ic_open_in_webview_24dp,
                    MR.strings.open_in_webview,
                ),
            )
        }
        val lastRead = presenter.allHistory.find { it.chapter_id == item.id }?.let {
            activity?.timeSpanFromNow(MR.strings.read_, it.last_read) + "\n"
        }
        val menuSheet =
            MaterialMenuSheet(activity!!, items, item.name, subtitle = lastRead) { _, itemPos ->
                when (itemPos) {
                    0 -> markPreviousAs(item, true)
                    1 -> markPreviousAs(item, false)
                    2 -> startReadRange(position, RangeMode.Read)
                    3 -> startReadRange(position, RangeMode.Unread)
                    4 -> openChapterInWebView(item)
                }
                true
            }
        menuSheet.show()
    }

    override fun onActionStateChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        binding.swipeRefresh.isEnabled = actionState != ItemTouchHelper.ACTION_STATE_SWIPE
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int) {
    }

    override fun shouldMoveItem(fromPosition: Int, toPosition: Int): Boolean {
        return true
    }
    //endregion

    fun dismissPopup(position: Int) {
        if (chapterPopupMenu != null && chapterPopupMenu?.first == position) {
            chapterPopupMenu?.second?.dismiss()
            chapterPopupMenu = null
        }
    }

    private fun markPreviousAs(chapter: ChapterItem, read: Boolean) {
        val adapter = adapter ?: return
        val chapters = if (presenter.sortDescending()) adapter.items.reversed() else adapter.items
        val chapterPos = chapters.indexOf(chapter)
        if (chapterPos != -1) {
            if (read) {
                markAsRead(chapters.take(chapterPos))
            } else {
                markAsUnread(chapters.take(chapterPos))
            }
        }
    }

    fun bookmarkChapter(position: Int) {
        val item = adapter?.getItem(position) as? ChapterItem ?: return
        val bookmarked = item.bookmark
        bookmarkChapters(listOf(item), !bookmarked)
        snack?.dismiss()
        snack = view?.snack(
            if (bookmarked) {
                MR.strings.removed_bookmark
            } else {
                MR.strings.bookmarked
            },
            Snackbar.LENGTH_INDEFINITE,
        ) {
            setAction(MR.strings.undo) {
                bookmarkChapters(listOf(item), bookmarked)
            }
        }
        (activity as? MainActivity)?.setUndoSnackBar(snack)
    }

    fun toggleReadChapter(position: Int) {
        val preferences = presenter.preferences
        val item = adapter?.getItem(position) as? ChapterItem ?: return
        val chapter = item.chapter
        val lastRead = chapter.last_page_read
        val pagesLeft = chapter.pages_left
        val read = item.chapter.read
        presenter.markChaptersRead(listOf(item), !read, false)
        snack?.dismiss()
        snack = view?.snack(
            if (read) {
                MR.strings.marked_as_unread
            } else {
                MR.strings.marked_as_read
            },
            Snackbar.LENGTH_INDEFINITE,
        ) {
            var undoing = false
            setAction(MR.strings.undo) {
                presenter.markChaptersRead(listOf(item), read, true, lastRead, pagesLeft)
                undoing = true
            }
            addCallback(
                object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        super.onDismissed(transientBottomBar, event)
                        if (!undoing && !read) {
                            if (preferences.removeAfterMarkedAsRead().get()) {
                                presenter.deleteChapters(listOf(item))
                            }
                            updateTrackChapterMarkedAsRead(preferences, chapter, manga?.id) {
                                presenter.fetchTracks()
                            }
                        }
                    }
                },
            )
        }
        (activity as? MainActivity)?.setUndoSnackBar(snack)
    }

    private fun bookmarkChapters(chapters: List<ChapterItem>, bookmarked: Boolean) {
        presenter.bookmarkChapters(chapters, bookmarked)
    }

    private fun markAsRead(chapters: List<ChapterItem>) {
        presenter.markChaptersRead(chapters, true)
    }

    private fun markAsUnread(chapters: List<ChapterItem>) {
        presenter.markChaptersRead(chapters, false)
    }

    private fun openChapter(chapter: Chapter, sharedElement: View? = null) {
        (activity as? AppCompatActivity)?.apply {
            if (sharedElement != null) {
                val (intent, bundle) = ReaderActivity
                    .newIntentWithTransitionOptions(this, manga!!, chapter, sharedElement)
                val firstPos = (binding.recycler.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
                val lastPos = (binding.recycler.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
                val chapterRange = if (firstPos > -1 && lastPos > -1) {
                    (firstPos..lastPos).mapNotNull {
                        (adapter?.getItem(it) as? ChapterItem)?.chapter?.id
                    }.toLongArray()
                } else {
                    longArrayOf()
                }
                returningFromReader = true
                intent.putExtra(ReaderActivity.VISIBLE_CHAPTERS, chapterRange)
                startActivity(intent, bundle)
            } else {
                startActivity(ReaderActivity.newIntent(this, manga!!, chapter))
            }
        }
    }

    //region action bar menu methods
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.manga_details, menu)
        colorToolbar(binding.recycler.canScrollVertically(-1))
        updateMenuVisibility(menu)
        menu.findItem(R.id.action_migrate).title = view?.context?.getString(
            MR.strings.migrate_,
            presenter.manga.seriesType(view!!.context),
        )
        menu.findItem(R.id.download_next).title =
            view?.context?.getString(MR.plurals.next_unread_chapters, 1, 1)
        menu.findItem(R.id.download_next_5).title =
            view?.context?.getString(MR.plurals.next_unread_chapters, 5, 5)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = activity?.getString(MR.strings.search_chapters)
        if (query.isNotEmpty() && (!searchItem.isActionViewExpanded || searchView.query != query)) {
            searchItem.expandActionView()
            setSearchViewListener(searchView)
            searchView.setQuery(query, true)
            searchView.clearFocus()
        } else {
            setSearchViewListener(searchView)
        }

        searchItem.fixExpand(onExpand = { invalidateMenuOnExpand() })
    }

    private fun setSearchViewListener(searchView: SearchView?) {
        setOnQueryTextChangeListener(searchView) {
            query = it ?: ""
            if (!isTablet) {
                if (query.isNotEmpty()) {
                    getHeader()?.collapse()
                } else {
                    getHeader()?.expand()
                }
            }

            adapter?.setFilter(query)
            adapter?.performFilter()
            true
        }
    }

    private fun updateMenuVisibility(menu: Menu?) {
        menu ?: return
        val editItem = menu.findItem(R.id.action_edit)
        editItem?.isVisible = (presenter.manga.favorite || presenter.manga.isLocal()) && !presenter.isLockedFromSearch
        menu.findItem(R.id.action_download)?.isVisible = !presenter.isLockedFromSearch &&
            !presenter.manga.isLocal()
        menu.findItem(R.id.action_mark_all_as_read)?.isVisible =
            presenter.getNextUnreadChapter() != null && !presenter.isLockedFromSearch
        menu.findItem(R.id.action_mark_all_as_unread)?.isVisible =
            presenter.anyRead() && !presenter.isLockedFromSearch
        menu.findItem(R.id.action_remove_downloads)?.isVisible =
            presenter.hasDownloads() && !presenter.isLockedFromSearch &&
            !presenter.manga.isLocal()
        menu.findItem(R.id.remove_non_bookmarked)?.isVisible =
            presenter.hasBookmark() && !presenter.isLockedFromSearch
        menu.findItem(R.id.action_migrate)?.isVisible = !presenter.isLockedFromSearch &&
            !presenter.manga.isLocal() && presenter.manga.favorite
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_edit -> {
                editMangaDialog = EditMangaDialog(
                    this,
                    presenter.manga,
                )
                editMangaDialog?.showDialog(router)
            }
            R.id.action_open_in_web_view -> openInWebView()
            R.id.action_refresh_tracking -> presenter.refreshTracking(true)
            R.id.action_migrate ->
                if (!isNotOnline()) {
                    PreMigrationController.navigateToMigration(
                        presenter.preferences.skipPreMigration().get(),
                        router,
                        listOf(manga!!.id!!),
                    )
                }
            R.id.action_mark_all_as_read -> {
                activity!!.materialAlertDialog()
                    .setMessage(MR.strings.mark_all_chapters_as_read)
                    .setPositiveButton(MR.strings.mark_as_read) { _, _ ->
                        markAsRead(presenter.chapters)
                    }
                    .setNegativeButton(AR.string.cancel, null)
                    .show()
            }
            R.id.remove_all, R.id.remove_read, R.id.remove_non_bookmarked, R.id.remove_custom -> massDeleteChapters(item.itemId)
            R.id.action_mark_all_as_unread -> {
                activity!!.materialAlertDialog()
                    .setMessage(MR.strings.mark_all_chapters_as_unread)
                    .setPositiveButton(MR.strings.mark_as_unread) { _, _ ->
                        markAsUnread(presenter.chapters)
                    }
                    .setNegativeButton(AR.string.cancel, null)
                    .show()
            }
            R.id.download_next, R.id.download_next_5, R.id.download_custom, R.id.download_unread, R.id.download_all -> downloadChapters(
                item.itemId,
            )
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
    //endregion

    fun saveCover() {
        if (presenter.saveCover()) {
            activity?.toast(MR.strings.cover_saved)
        } else {
            activity?.toast(MR.strings.error_saving_cover)
        }
    }

    fun shareCover() {
        val cover = presenter.shareCover()
        if (cover != null) {
            val stream = cover.toFile().getUriCompat(activity!!)
            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, stream)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                clipData = ClipData.newRawUri(null, stream)
                type = "image/*"
            }
            startActivity(Intent.createChooser(intent, activity?.getString(MR.strings.share)))
        } else {
            activity?.toast(MR.strings.error_sharing_cover)
        }
    }

    override fun prepareToShareManga() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            presenter.shareManga()
        } else {
            shareManga()
        }
    }

    fun shareManga(cover: File? = null) {
        val context = view?.context ?: return

        val source = presenter.source as? HttpSource ?: return
        val stream = cover?.getUriCompat(context)
        try {
            val url = source.getMangaUrl(presenter.manga)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/*"
                putExtra(Intent.EXTRA_TEXT, url)
                putExtra(Intent.EXTRA_TITLE, presenter.manga.title)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                if (stream != null) {
                    clipData = ClipData.newRawUri(null, stream)
                }
            }
            startActivity(
                Intent.createChooser(intent, context.getString(MR.strings.share)).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && stream != null) {
                        val shareCoverIntent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_STREAM, stream)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                            clipData = ClipData.newRawUri(null, stream)
                            type = "image/*"
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            context,
                            manga?.id?.hashCode() ?: 0,
                            Intent.createChooser(shareCoverIntent, context.getString(MR.strings.share)),
                            PendingIntent.FLAG_IMMUTABLE,
                        )
                        val action = ChooserAction.Builder(
                            Icon.createWithResource(context, R.drawable.ic_photo_24dp),
                            context.getString(MR.strings.share_cover),
                            pendingIntent,
                        ).build()
                        putExtra(Intent.EXTRA_CHOOSER_CUSTOM_ACTIONS, arrayOf(action))
                    }
                },
            )
        } catch (e: Exception) {
            context.toast(e.message)
        }
    }

    override fun openInWebView() {
        if (isNotOnline()) return
        val source = presenter.source as? HttpSource ?: return
        val url = try {
            source.getMangaUrl(presenter.manga)
        } catch (e: Exception) {
            return
        }

        val activity = activity ?: return
        val intent = WebViewActivity.newIntent(
            activity.applicationContext,
            url,
            source.id,
            presenter.manga
                .title,
        )
        startActivity(intent)
    }

    fun openChapterInWebView(item: ChapterItem) {
        if (isNotOnline()) return
        val source = presenter.source as? HttpSource ?: return
        val url = presenter.getChapterUrl(item.chapter) ?: return

        val activity = activity ?: return
        val intent = WebViewActivity.newIntent(
            activity.applicationContext,
            url,
            source.id,
            presenter.manga
                .title,
        )
        startActivity(intent)
    }

    private fun massDeleteChapters(choice: Int) {
        val chaptersToDelete = when (choice) {
            R.id.remove_all -> presenter.allChapters
            R.id.remove_non_bookmarked -> presenter.allChapters.filter { !it.bookmark }
            R.id.remove_read -> presenter.allChapters.filter { it.read }
            R.id.remove_custom -> {
                createActionModeIfNeeded()
                rangeMode = RangeMode.RemoveDownload
                return
            }
            else -> emptyList()
        }.filter { it.isDownloaded }
        if (chaptersToDelete.isNotEmpty() || choice == R.id.remove_all) {
            massDeleteChapters(chaptersToDelete, choice == R.id.remove_all)
        } else {
            snack?.dismiss()
            snack = view?.snack(MR.strings.no_chapters_to_delete)
        }
    }

    private fun massDeleteChapters(chapters: List<ChapterItem>, isEverything: Boolean) {
        val context = view?.context ?: return
        context.materialAlertDialog()
            .setMessage(
                if (isEverything) {
                    context.getString(MR.strings.remove_all_downloads)
                } else {
                    context.getString(
                        MR.plurals.remove_n_chapters,
                        chapters.size,
                        chapters.size,
                    )
                },
            )
            .setPositiveButton(MR.strings.remove) { _, _ ->
                presenter.deleteChapters(chapters, isEverything = isEverything)
            }
            .setNegativeButton(AR.string.cancel, null)
            .show()
    }

    private fun updateToolbarTitleAlpha(@FloatRange(from = 0.0, to = 1.0) alpha: Float? = null, isScrollingDown: Boolean = false) {
        if ((!isControllerVisible && alpha == null) || isScrollingDown) return
        val scrolledList = binding.recycler
        val toolbarTextView = activityBinding?.toolbar?.toolbarTitle ?: return
        val tbAlpha = when {
            isTablet -> 0f
            // Specific alpha provided
            alpha != null -> alpha

            // First item isn't in view, full opacity
            ((scrolledList.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() > 0) -> 1f
            ((scrolledList.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition() == 0) -> 0f

            // Based on scroll amount when first item is in view
            else -> (scrolledList.computeVerticalScrollOffset() - (20.dpToPx))
                .coerceIn(0, 255) / 255f
        }
        toolbarTextView.setTextColorAlpha((tbAlpha * 255).roundToInt())
    }

    private fun downloadChapters(choice: Int) {
        val chaptersToDownload = when (choice) {
            R.id.download_next -> presenter.getUnreadChaptersSorted().take(1)
            R.id.download_next_5 -> presenter.getUnreadChaptersSorted().take(5)
            R.id.download_custom -> {
                createActionModeIfNeeded()
                rangeMode = RangeMode.Download
                return
            }
            R.id.download_unread -> presenter.chapters.filter { !it.read }
            R.id.download_all -> presenter.allChapters
            else -> emptyList()
        }
        if (chaptersToDownload.isNotEmpty()) {
            downloadChapters(chaptersToDownload)
        }
    }

    private fun isLocked(): Boolean {
        if (presenter.isLockedFromSearch) {
            return SecureActivityDelegate.shouldBeLocked()
        }
        return false
    }

    private fun needsToBeUnlocked(): Boolean {
        if (presenter.isLockedFromSearch) {
            SecureActivityDelegate.promptLockIfNeeded(activity)
            return SecureActivityDelegate.shouldBeLocked()
        }
        return false
    }

    //region Interface methods
    override fun coverColor(): Int? = coverColor
    override fun accentColor(): Int? = accentColor
    override fun topCoverHeight(): Int = headerHeight

    override fun startDownloadNow(position: Int) {
        val chapter = (adapter?.getItem(position) as? ChapterItem) ?: return
        presenter.startDownloadingNow(chapter)
    }

    // In case the recycler is at the bottom and collapsing the header makes it unscrollable
    override fun updateScroll() {
        updateToolbarTitleAlpha()
        if (!binding.recycler.canScrollVertically(-1)) {
            getHeader()?.binding?.backdrop?.translationY = 0f
            activityBinding?.appBar?.y = 0f
            colorToolbar(isColor = false, animate = false)
        }
    }

    private fun downloadChapters(chapters: List<ChapterItem>) {
        val view = view ?: return
        presenter.downloadChapters(chapters)
        val text = view.context.getString(
            MR.strings.add_x_to_library,
            presenter.manga.seriesType(view.context).lowercase(Locale.ROOT),
        )
        if (!presenter.manga.favorite && (
            snack == null ||
                snack?.getText() != text
            )
        ) {
            snack = view.snack(text, Snackbar.LENGTH_INDEFINITE) {
                setAction(MR.strings.add) {
                    if (!presenter.manga.favorite) {
                        toggleMangaFavorite()
                    }
                }
                addCallback(
                    object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            super.onDismissed(transientBottomBar, event)
                            if (snack == transientBottomBar) snack = null
                        }
                    },
                )
            }
            (activity as? MainActivity)?.setUndoSnackBar(snack)
        }
    }

    override fun startDownloadRange(position: Int) {
        createActionModeIfNeeded()
        val chapterItem = (adapter?.getItem(position) as? ChapterItem) ?: return
        rangeMode = if (chapterItem.status in listOf(Download.State.NOT_DOWNLOADED, Download.State.ERROR)) {
            RangeMode.Download
        } else {
            RangeMode.RemoveDownload
        }
        onItemClick(null, position)
    }

    private fun startReadRange(position: Int, mode: RangeMode) {
        createActionModeIfNeeded()
        rangeMode = mode
        onItemClick(null, position)
    }

    override fun readNextChapter(readingButton: View) {
        if (activity is SearchActivity && presenter.isLockedFromSearch) {
            SecureActivityDelegate.promptLockIfNeeded(activity)
            return
        }
        val item = presenter.getNextUnreadChapter()
        if (item != null) {
            openChapter(item.chapter, readingButton)
        } else if (snack == null ||
            snack?.getText() != view?.context?.getString(MR.strings.next_chapter_not_found)
        ) {
            snack = view?.snack(MR.strings.next_chapter_not_found, Snackbar.LENGTH_LONG) {
                addCallback(
                    object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            super.onDismissed(transientBottomBar, event)
                            if (snack == transientBottomBar) snack = null
                        }
                    },
                )
            }
        }
    }

    override fun downloadChapter(position: Int) {
        val view = view ?: return
        val chapter = (adapter?.getItem(position) as? ChapterItem) ?: return
        if (actionMode != null) {
            onItemClick(null, position)
            return
        }
        if (chapter.status != Download.State.NOT_DOWNLOADED && chapter.status != Download.State.ERROR) {
            presenter.deleteChapter(chapter)
        } else {
            if (chapter.status == Download.State.ERROR) {
                DownloadJob.start(view.context)
            } else {
                downloadChapters(listOf(chapter))
            }
        }
    }

    fun localSearch(text: String, isTag: Boolean) {
        router.pushController(
            FilteredLibraryController(
                text,
                queryText = text.takeIf { !isTag },
                filterTags = arrayOf(text).takeIf { isTag } ?: emptyArray(),
            ).withFadeTransaction(),
        )
    }

    fun sourceSearch(text: String) {
        when (
            val previousController =
                router.backstack.getOrNull(router.backstackSize - 2)?.controller
        ) {
            is BrowseSourceController -> {
                if (presenter.source is HttpSource) {
                    router.handleBack()
                    previousController.searchWithGenre(text)
                }
            }
            else -> {
                if (presenter.source is CatalogueSource) {
                    val controller = BrowseSourceController(presenter.source as CatalogueSource)
                    router.pushController(controller.withFadeTransaction())
                    controller.searchWithGenre(text)
                }
            }
        }
    }

    fun globalSearch(text: String) {
        if (isNotOnline()) return
        router.pushController(GlobalSearchController(text).withFadeTransaction())
    }

    override fun showFloatingActionMode(view: TextView, content: String?, isTag: Boolean) {
        finishFloatingActionMode()
        val previousController = previousController
        val hasDifferentAuthors = view.id == R.id.manga_author &&
            manga?.hasSameAuthorAndArtist == false && manga?.author != null
        val isInSource = !isTag && previousController !is LibraryController &&
            previousController !is RecentsController
        if (!hasDifferentAuthors && isInSource) {
            globalSearch(content ?: view.text.toString())
            return
        }
        val actionModeCallback = if (content != null) {
            FloatingMangaDetailsActionModeCallback(
                content,
                showCopy = view is Chip,
                searchSource = isTag,
            )
        } else {
            FloatingMangaDetailsActionModeCallback(view, isTag = isTag)
        }
        if (hasDifferentAuthors) {
            actionModeCallback.authorText = manga?.author
            actionModeCallback.artistText = manga?.artist
            if (isInSource) {
                actionModeCallback.isGlobalSearch = true
            }
        }
        if (view is Chip) {
            view.isActivated = true
        }
        floatingActionMode =
            view.startActionMode(actionModeCallback, android.view.ActionMode.TYPE_FLOATING)
    }

    override fun customActionMode(view: TextView): android.view.ActionMode.Callback {
        return FloatingMangaDetailsActionModeCallback(view, false, closeMode = false)
    }

    override fun showChapterFilter() {
        ChaptersSortBottomSheet(this).show()
    }

    override fun favoriteManga(longPress: Boolean) {
        if (needsToBeUnlocked()) return
        val manga = presenter.manga
        if (longPress) {
            showCategoriesSheet()
        } else if (!manga.favorite) {
            toggleMangaFavorite()
        } else {
            val categories = presenter.getCategories()
            val favButton = getHeader()?.binding?.favoriteButton ?: return
            val popup = makeFavPopup(favButton, categories)
            popup?.show()
        }
    }

    override fun setFavButtonPopup(popupView: View) {
        if (presenter.isLockedFromSearch) {
            return
        }
        val manga = presenter.manga
        if (!manga.favorite) {
            popupView.setOnTouchListener(null)
            return
        }
        val popup = makeFavPopup(popupView, presenter.getCategories())
        popupView.setOnTouchListener(popup?.dragToOpenListener)
    }

    private fun makeFavPopup(popupView: View, categories: List<Category>): PopupMenu? {
        val view = view ?: return null
        val popup = PopupMenu(view.context, popupView)
        popup.menu.add(0, 1, 0, view.context.getString(MR.strings.remove_from_library))
        if (categories.isNotEmpty()) {
            popup.menu.add(0, 0, 1, view.context.getString(MR.strings.edit_categories))
        }

        // Set a listener so we are notified if a menu item is clicked
        popup.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == 0) {
                showCategoriesSheet()
            } else {
                toggleMangaFavorite()
            }
            true
        }
        return popup
    }

    private fun showCategoriesSheet() {
        val adding = !presenter.manga.favorite
        viewScope.launchIO {
            presenter.manga.moveCategories(activity!!, adding) {
                updateHeader()
                if (adding) {
                    showAddedSnack()
                }
            }
        }
    }

    private fun toggleMangaFavorite() {
        val view = view ?: return
        val activity = activity ?: return
        viewScope.launchIO {
            withUIContext { snack?.dismiss() }
            snack = presenter.manga.addOrRemoveToFavorites(
                presenter.preferences,
                view,
                activity,
                presenter.sourceManager,
                this@MangaDetailsController,
                onMangaAdded = { migrationInfo ->
                    migrationInfo?.let {
                        presenter.fetchChapters(andTracking = true)
                    }
                    updateHeader()
                    showAddedSnack()
                },
                onMangaMoved = {
                    updateHeader()
                    presenter.fetchChapters(andTracking = true)
                },
                onMangaDeleted = {
                    updateHeader()
                    presenter.confirmDeletion()
                },
                scope = viewScope,
            )
            if (snack?.duration == Snackbar.LENGTH_INDEFINITE) {
                withUIContext {
                    val favButton = getHeader()?.binding?.favoriteButton
                    (activity as? MainActivity)?.setUndoSnackBar(snack, favButton)
                }
            }
        }
    }

    private fun showAddedSnack() {
        val view = view ?: return
        snack?.dismiss()
        snack = view.snack(view.context.getString(MR.strings.added_to_library))
    }

    override fun mangaPresenter(): MangaDetailsPresenter = presenter

    /**
     * Copies a string to clipboard
     *
     * @param content the actual text to copy to the board
     * @param label Label to show to the user describing the content
     */
    override fun copyContentToClipboard(content: String, label: StringResource, useToast: Boolean) {
        val view = view ?: return
        val contentType = if (label.resourceId != 0) view.context.getString(label) else null
        copyContentToClipboard(content, contentType, useToast)
    }

    /**
     * Copies a string to clipboard
     *
     * @param content the actual text to copy to the board
     * @param label Label to show to the user describing the content
     */
    override fun copyContentToClipboard(content: String, label: Int, useToast: Boolean) {
        val view = view ?: return
        val contentType = if (label != 0) view.context.getString(label) else null
        copyContentToClipboard(content, contentType, useToast)
    }

    /**
     * Copies a string to clipboard
     *
     * @param content the actual text to copy to the board
     * @param label Label to show to the user describing the content
     */
    override fun copyContentToClipboard(content: String, label: String?, useToast: Boolean) {
        snack = copyToClipboard(content, label, useToast)
    }

    override fun showTrackingSheet() {
        if (needsToBeUnlocked()) return
        trackingBottomSheet =
            TrackingBottomSheet(this)
        trackingBottomSheet?.show()
    }
    //endregion

    //region Tracking methods
    fun refreshTracking(trackings: List<TrackItem>) {
        trackingBottomSheet?.onNextTrackersUpdate(trackings)
    }

    fun onTrackSearchResults(results: List<TrackSearch>) {
        trackingBottomSheet?.onSearchResults(results)
    }

    fun refreshTracker() {
        getHeader()?.updateTracking()
    }

    fun trackRefreshDone() {
        trackingBottomSheet?.onRefreshDone()
    }

    fun trackRefreshError(error: Exception) {
        Logger.e(error)
        trackingBottomSheet?.onRefreshError(error)
    }

    fun trackSearchError(error: Exception) {
        trackingBottomSheet?.onSearchResultsError(error)
    }
    //endregion

    //region Action mode methods
    private fun createActionModeIfNeeded() {
        if (actionMode == null) {
            actionMode = (activity as AppCompatActivity).startSupportActionMode(this)
            val view = activity?.window?.currentFocus ?: return
            val imm = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                ?: return
            imm.hideSoftInputFromWindow(view.windowToken, 0)
            if (adapter?.mode != SelectableAdapter.Mode.MULTI) {
                adapter?.mode = SelectableAdapter.Mode.MULTI
            }
        }
    }

    /**
     * Destroys the action mode.
     */
    private fun destroyActionModeIfNeeded() {
        actionMode?.finish()
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        return true
    }

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        mode?.title = view?.context?.getString(
            if (startingRangeChapterPos == null) {
                MR.strings.select_starting_chapter
            } else {
                MR.strings.select_ending_chapter
            },
        )
        return false
    }

    override fun onDestroyActionMode(mode: ActionMode?) {
        actionMode = null
        setStatusBarAndToolbar()
        if (startingRangeChapterPos != null && rangeMode in setOf(RangeMode.Download, RangeMode.RemoveDownload)) {
            val item = adapter?.getItem(startingRangeChapterPos!!) as? ChapterItem
            (binding.recycler.findViewHolderForAdapterPosition(startingRangeChapterPos!!) as? ChapterHolder)?.notifyStatus(
                item?.status ?: Download.State.NOT_DOWNLOADED,
                false,
                0,
            )
        }
        rangeMode = null
        startingRangeChapterPos = null
        adapter?.mode = SelectableAdapter.Mode.IDLE
        adapter?.clearSelection()
        return
    }
    //endregion

    fun changeCover() {
        if (manga?.favorite == true) {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            startActivityForResult(
                Intent.createChooser(
                    intent,
                    activity?.getString(MR.strings.select_cover_image),
                ),
                101,
            )
        } else {
            activity?.toast(MR.strings.must_be_in_library_to_edit)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 101) {
            if (data == null || resultCode != Activity.RESULT_OK) return
            val activity = activity ?: return
            try {
                val uri = data.data ?: return
                if (editMangaDialog != null) {
                    editMangaDialog?.updateCover(uri)
                } else {
                    presenter.editCoverWithStream(uri)
                }
            } catch (error: IOException) {
                activity.toast(MR.strings.failed_to_update_cover)
                Logger.e(error)
            }
        }
    }

    override fun zoomImageFromThumb(thumbView: View) {
        if (fullCoverActive) return
        val drawable = binding.mangaCoverFull.drawable ?: return
        fullCoverActive = true
        drawable.alpha = 255
        val fullCoverDialog = FullCoverDialog(this, drawable, thumbView)
        fullCoverDialog.setOnDismissListener {
            fullCoverActive = false
        }
        fullCoverDialog.setOnCancelListener {
            fullCoverActive = false
        }
        fullCoverDialog.show()
    }

    companion object {
        const val UPDATE_EXTRA = "update"
        const val SMART_SEARCH_CONFIG_EXTRA = "smartSearchConfig"

        const val FROM_CATALOGUE_EXTRA = "from_catalogue"

        private enum class RangeMode {
            Download,
            RemoveDownload,
            Read,
            Unread,
        }

        fun bundle(
            mangaId: Long? = null,
            fromCatalogue: Boolean = false,
            smartSearchConfig: BrowseController.SmartSearchConfig? = null,
            update: Boolean = false,
        ) = Bundle().apply {
            putLong(Constants.MANGA_EXTRA, mangaId ?: 0)
            putBoolean(FROM_CATALOGUE_EXTRA, fromCatalogue)
            putParcelable(SMART_SEARCH_CONFIG_EXTRA, smartSearchConfig)
            putBoolean(UPDATE_EXTRA, update)
        }
    }

    inner class FloatingMangaDetailsActionModeCallback(
        private val textView: TextView?,
        private val showCopy: Boolean = true,
        private val isTag: Boolean = false,
        private val closeMode: Boolean = true,
    ) : android.view.ActionMode.Callback {
        constructor(
            text: String,
            showCopy: Boolean = true,
            searchSource: Boolean = false,
            closeMode: Boolean = true,
        ) : this(null, showCopy, searchSource, closeMode) {
            customText = text
        }

        private var customText: String? = null
        var authorText: String? = null
        var artistText: String? = null
        var isGlobalSearch: Boolean? = null
        val text: String
            get() {
                return customText ?: if (textView?.isTextSelectable == true) {
                    textView.text.subSequence(textView.selectionStart, textView.selectionEnd)
                        .toString()
                } else {
                    textView?.text?.toString() ?: ""
                }
            }
        override fun onCreateActionMode(mode: android.view.ActionMode?, menu: Menu?): Boolean {
            mode?.menuInflater?.inflate(
                if (isTag) R.menu.manga_details_tag else R.menu.manga_details_title,
                menu,
            )
            menu?.findItem(R.id.action_copy)?.isVisible = showCopy
            var sourceMenuItem = menu?.findItem(R.id.action_source_search)
            sourceMenuItem?.isVisible = isTag && presenter.source is CatalogueSource
            val context = view?.context ?: return false
            val localItem = menu?.findItem(R.id.action_local_search) ?: return true
            localItem.isVisible = previousController !is FilteredLibraryController
            val library = context.getString(MR.strings.library).lowercase(Locale.getDefault())
            localItem.title = context.getString(MR.strings.search_, library)
            sourceMenuItem?.title = context.getString(MR.strings.search_, presenter.source.name)
            menu.findItem(R.id.action_search_author)?.title = context.getString(
                MR.strings.search_,
                context.getString(MR.strings.author).lowercase(Locale.getDefault()),
            )
            menu.findItem(R.id.action_search_artist)?.title = context.getString(
                MR.strings.search_,
                context.getString(MR.strings.artist).lowercase(Locale.getDefault()),
            )
            if (isTag) {
                if (previousController is BrowseSourceController) {
                    menu.removeItem(R.id.action_source_search)
                    sourceMenuItem = menu.add(0, R.id.action_source_search, 1, sourceMenuItem?.title)
                    sourceMenuItem?.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                }
                sourceMenuItem?.icon = presenter.source.icon()
            }
            return true
        }

        override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: Menu?): Boolean {
            return false
        }

        override fun onActionItemClicked(
            mode: android.view.ActionMode?,
            item: MenuItem?,
        ): Boolean {
            when (item?.itemId) {
                R.id.action_copy -> copyContentToClipboard(text, null)
                R.id.action_source_search -> sourceSearch(text)
                R.id.action_global_search, R.id.action_local_search -> {
                    if (authorText != null) {
                        mode?.menu?.findItem(R.id.action_copy)?.isVisible = false
                        mode?.menu?.findItem(R.id.action_local_search)?.isVisible = false
                        mode?.menu?.findItem(R.id.action_source_search)?.isVisible = false
                        mode?.menu?.findItem(R.id.action_global_search)?.isVisible = false
                        mode?.menu?.findItem(R.id.action_search_author)?.isVisible = true
                        mode?.menu?.findItem(R.id.action_search_artist)?.isVisible = true
                        isGlobalSearch = item.itemId == R.id.action_global_search
                        mode?.invalidate()
                        return true
                    } else if (item.itemId == R.id.action_global_search) {
                        globalSearch(text)
                    } else {
                        localSearch(text, isTag)
                    }
                }
                R.id.action_search_artist, R.id.action_search_author -> {
                    val subText =
                        (if (item.itemId == R.id.action_search_author) authorText else artistText)
                            ?: return false
                    if (isGlobalSearch == true) {
                        globalSearch(subText)
                    } else {
                        localSearch(subText, isTag)
                    }
                }
                else -> return false
            }
            if (closeMode) {
                mode?.finish()
            }
            return true
        }

        override fun onDestroyActionMode(mode: android.view.ActionMode?) {
            if (showCopy) {
                floatingActionMode = null
            }
            if (textView is Chip) {
                textView.isActivated = false
            }
        }
    }
}
