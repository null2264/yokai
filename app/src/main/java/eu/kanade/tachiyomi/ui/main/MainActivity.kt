package eu.kanade.tachiyomi.ui.main

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.app.assist.AssistContent
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.IdRes
import androidx.appcompat.view.ActionMode
import androidx.appcompat.view.menu.ActionMenuItemView
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import androidx.core.animation.doOnEnd
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.DisplayFeature
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.Router
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetView
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import com.google.common.primitives.Floats.max
import com.google.common.primitives.Ints.max
import dev.yokai.domain.base.BasePreferences
import dev.yokai.presentation.extension.repo.ExtensionRepoController
import dev.yokai.presentation.onboarding.OnboardingController
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.Migrations
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadJob
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.changesIn
import eu.kanade.tachiyomi.data.updater.AppUpdateChecker
import eu.kanade.tachiyomi.data.updater.AppUpdateNotifier
import eu.kanade.tachiyomi.data.updater.AppUpdateResult
import eu.kanade.tachiyomi.data.updater.RELEASE_URL
import eu.kanade.tachiyomi.databinding.MainActivityBinding
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.api.ExtensionApi
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.MaterialMenuSheet
import eu.kanade.tachiyomi.ui.base.SmallToolbarInterface
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.base.controller.BaseLegacyController
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.library.LibraryController
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.more.AboutController
import eu.kanade.tachiyomi.ui.more.OverflowDialog
import eu.kanade.tachiyomi.ui.more.stats.StatsController
import eu.kanade.tachiyomi.ui.recents.RecentsController
import eu.kanade.tachiyomi.ui.recents.RecentsViewType
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.setting.SettingsLegacyController
import eu.kanade.tachiyomi.ui.setting.controllers.SettingsMainController
import eu.kanade.tachiyomi.ui.source.BrowseController
import eu.kanade.tachiyomi.ui.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.util.manga.MangaCoverMetadata
import eu.kanade.tachiyomi.util.manga.MangaShortcutManager
import eu.kanade.tachiyomi.util.system.contextCompatDrawable
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.hasSideNavBar
import eu.kanade.tachiyomi.util.system.ignoredSystemInsets
import eu.kanade.tachiyomi.util.system.isBottomTappable
import eu.kanade.tachiyomi.util.system.isInNightMode
import eu.kanade.tachiyomi.util.system.launchIO
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.prepareSideNavContext
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.tryTakePersistableUriPermission
import eu.kanade.tachiyomi.util.view.BackHandlerControllerInterface
import eu.kanade.tachiyomi.util.view.backgroundColor
import eu.kanade.tachiyomi.util.view.blurBehindWindow
import eu.kanade.tachiyomi.util.view.canStillGoBack
import eu.kanade.tachiyomi.util.view.doOnApplyWindowInsetsCompat
import eu.kanade.tachiyomi.util.view.findChild
import eu.kanade.tachiyomi.util.view.getItemView
import eu.kanade.tachiyomi.util.view.isCompose
import eu.kanade.tachiyomi.util.view.mainRecyclerView
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.withFadeInTransaction
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToLong

@SuppressLint("ResourceType")
open class MainActivity : BaseActivity<MainActivityBinding>() {

    protected lateinit var router: Router

    protected val searchDrawable by lazy { contextCompatDrawable(R.drawable.ic_search_24dp) }
    protected val backDrawable by lazy { contextCompatDrawable(R.drawable.ic_arrow_back_24dp) }
    private var gestureDetector: GestureDetector? = null

    private var snackBar: Snackbar? = null
    private var extraViewForUndo: View? = null
    private var canDismissSnackBar = false

    private var animationSet: AnimatorSet? = null
    private val downloadManager: DownloadManager by injectLazy()
    private val mangaShortcutManager: MangaShortcutManager by injectLazy()
    private val extensionManager: ExtensionManager by injectLazy()
    private val hideBottomNav
        get() = router.backstackSize > 1 && router.backstack[1].controller !is DialogController
    private val hideAppBar
        get() = router.isCompose

    private val updateChecker by lazy { AppUpdateChecker() }
    private val isUpdaterEnabled = BuildConfig.INCLUDE_UPDATER
    private var tabAnimation: ValueAnimator? = null
    private var searchBarAnimation: ValueAnimator? = null
    private var overflowDialog: Dialog? = null
    var currentToolbar: Toolbar? = null
    var ogWidth: Int = Int.MAX_VALUE
    var hingeGapSize = 0
        private set

    val velocityTracker: VelocityTracker by lazy { VelocityTracker.obtain() }
    private val actionButtonSize: Pair<Int, Int> by lazy {
        val attrs = intArrayOf(android.R.attr.minWidth, android.R.attr.minHeight)
        val ta = obtainStyledAttributes(androidx.appcompat.R.style.Widget_AppCompat_ActionButton, attrs)
        val dimenW = ta.getDimensionPixelSize(0, 0.dpToPx)
        val dimenH = ta.getDimensionPixelSize(1, 0.dpToPx)
        ta.recycle()
        dimenW to dimenH
    }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                materialAlertDialog()
                    .setTitle(R.string.warning)
                    .setMessage(R.string.allow_notifications_recommended)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }

    private val basePreferences: BasePreferences by injectLazy()

    // Ideally we want this to be inside the controller itself, but Conductor doesn't support the new ActivityResult API
    // Should be fine once we moved completely to Compose..... someday....
    // REF: https://github.com/bluelinelabs/Conductor/issues/612
    private val requestColourProfile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                applicationContext.tryTakePersistableUriPermission(uri, flags)
                basePreferences.displayProfile().set(uri.toString())
            }
        }

    fun setUndoSnackBar(snackBar: Snackbar?, extraViewToCheck: View? = null) {
        this.snackBar = snackBar
        canDismissSnackBar = false
        launchUI {
            delay(1000)
            if (this@MainActivity.snackBar == snackBar) {
                canDismissSnackBar = true
            }
        }
        extraViewForUndo = extraViewToCheck
    }

    override fun attachBaseContext(newBase: Context?) {
        ogWidth = min(newBase?.resources?.configuration?.screenWidthDp ?: Int.MAX_VALUE, ogWidth)
        super.attachBaseContext(newBase?.prepareSideNavContext())
    }

    val toolbarHeight: Int
        get() = max(binding.toolbar.height, binding.cardFrame.height, binding.appBar.attrToolbarHeight)

    private var actionMode: ActionMode? = null
    private var backPressedCallback: OnBackPressedCallback? = null
    private val backCallback = {
        pressingBack()
        reEnableBackPressedCallBack()
    }

    fun bigToolbarHeight(includeSearchToolbar: Boolean, includeTabs: Boolean, includeLargeToolbar: Boolean): Int {
        return if (!includeLargeToolbar || !binding.appBar.useLargeToolbar) {
            toolbarHeight + if (includeTabs) 48.dpToPx else 0
        } else {
            binding.appBar.getEstimatedLayout(includeSearchToolbar, includeTabs, includeLargeToolbar)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = maybeInstallSplashScreen(savedInstanceState)

        // Set up shared element transition and disable overlay so views don't show above system bars
        window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        setExitSharedElementCallback(
            object : MaterialContainerTransformSharedElementCallback() {
                override fun onMapSharedElements(
                    names: MutableList<String>,
                    sharedElements: MutableMap<String, View>,
                ) {
                    val mangaController =
                        router.backstack.lastOrNull()?.controller as? MangaDetailsController
                    if (mangaController == null || chapterIdToExitTo == 0L) {
                        super.onMapSharedElements(names, sharedElements)
                        return
                    }
                    val recyclerView = mangaController.binding.recycler
                    val selectedViewHolder =
                        recyclerView.findViewHolderForItemId(chapterIdToExitTo) ?: return
                    sharedElements[names[0]] = selectedViewHolder.itemView
                    chapterIdToExitTo = 0L
                }
            },
        )
        window.sharedElementsUseOverlay = false

        super.onCreate(savedInstanceState)

        backPressedCallback = object : OnBackPressedCallback(enabled = true) {
            var startTime: Long = 0
            var lastX: Float = 0f
            var lastY: Float = 0f
            var controllerHandlesBackPress = false
            override fun handleOnBackPressed() {
                if (controllerHandlesBackPress &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    lastX != 0f && lastY != 0f
                ) {
                    val motionEvent = MotionEvent.obtain(
                        startTime,
                        SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_UP,
                        lastX,
                        lastY,
                        0,
                    )
                    velocityTracker.addMovement(motionEvent)
                    motionEvent.recycle()
                    velocityTracker.computeCurrentVelocity(1, 5f)
                    backVelocity =
                        max(0.5f, abs(velocityTracker.getAxisVelocity(MotionEvent.AXIS_X)) * 0.5f)
                }
                lastX = 0f
                lastY = 0f
                backCallback()
            }

            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                controllerHandlesBackPress = false
                val controller by lazy { router.backstack.lastOrNull()?.controller }
                if (!(
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ViewCompat.getRootWindowInsets(window.decorView)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                    ) &&
                    actionMode == null &&
                    !(
                        binding.searchToolbar.hasExpandedActionView() && binding.cardFrame.isVisible &&
                            controller !is SearchControllerInterface
                        )
                ) {
                    controllerHandlesBackPress = true
                }
                if (controllerHandlesBackPress) {
                    startTime = SystemClock.uptimeMillis()
                    velocityTracker.clear()
                    val motionEvent = MotionEvent.obtain(startTime, startTime, MotionEvent.ACTION_DOWN, backEvent.touchX, backEvent.touchY, 0)
                    velocityTracker.addMovement(motionEvent)
                    motionEvent.recycle()
                    (controller as? BackHandlerControllerInterface)?.handleOnBackStarted(backEvent)
                }
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                if (controllerHandlesBackPress) {
                    val motionEvent = MotionEvent.obtain(startTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, backEvent.touchX, backEvent.touchY, 0)
                    lastX = backEvent.touchX
                    lastY = backEvent.touchY
                    velocityTracker.addMovement(motionEvent)
                    motionEvent.recycle()
                    val controller = router.backstack.lastOrNull()?.controller as? BackHandlerControllerInterface
                    controller?.handleOnBackProgressed(backEvent)
                }
            }

            override fun handleOnBackCancelled() {
                if (controllerHandlesBackPress) {
                    val controller = router.backstack.lastOrNull()?.controller as? BackHandlerControllerInterface
                    controller?.handleOnBackCancelled()
                }
            }
        }
        onBackPressedDispatcher.addCallback(backPressedCallback!!)
        // Do not let the launcher create a new activity http://stackoverflow.com/questions/16283079
        if (!isTaskRoot && this !is SearchActivity) {
            finish()
            return
        }
        gestureDetector = GestureDetector(this, GestureListener())
        binding = MainActivityBinding.inflate(layoutInflater)

        setContentView(binding.root)

        binding.toolbar.overflowIcon?.setTint(getResourceColor(R.attr.actionBarTintColor))

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        }
        var continueSwitchingTabs = false
        nav.getItemView(R.id.nav_library)?.setOnLongClickListener {
            if (!LibraryUpdateJob.isRunning(this)) {
                LibraryUpdateJob.startNow(this)
                binding.mainContent.snack(R.string.updating_library) {
                    anchorView = binding.bottomNav
                    setAction(R.string.cancel) {
                        LibraryUpdateJob.stop(context)
                        lifecycleScope.launchUI {
                            NotificationReceiver.dismissNotification(
                                context,
                                Notifications.ID_LIBRARY_PROGRESS,
                            )
                        }
                    }
                }
            }
            true
        }
        for (id in listOf(R.id.nav_recents, R.id.nav_browse)) {
            nav.getItemView(id)?.setOnLongClickListener {
                nav.selectedItemId = id
                nav.post {
                    val controller =
                        router.backstack.firstOrNull()?.controller as? BottomSheetController
                    controller?.showSheet()
                }
                true
            }
        }

        val container: ViewGroup = binding.controllerContainer

        val content: ViewGroup = binding.mainContent

        if (savedInstanceState == null && this !is SearchActivity) {
            // Reset Incognito Mode on relaunch
            preferences.incognitoMode().set(false)

            // Show changelog if needed
            if (Migrations.upgrade(preferences, Injekt.get(), lifecycleScope)) {
                if (!BuildConfig.DEBUG) {
                    content.post {
                        whatsNewSheet().show()
                    }
                }
            }
        }

        DownloadJob.downloadFlow.onEach(::downloadStatusChanged).launchIn(lifecycleScope)
        lifecycleScope
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowCustomEnabled(true)

        setNavBarColor(content.rootWindowInsetsCompat)
        binding.appBar.mainActivity = this
        nav.isVisible = false
        content.doOnApplyWindowInsetsCompat { v, insets, _ ->
            setNavBarColor(insets)
            val systemInsets = insets.ignoredSystemInsets
            val contextView = window?.decorView?.findViewById<View>(R.id.action_mode_bar)
            contextView?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = systemInsets.left
                rightMargin = systemInsets.right
            }
            // Consume any horizontal insets and pad all content in. There's not much we can do
            // with horizontal insets
            v.updatePadding(
                left = systemInsets.left,
                right = systemInsets.right,
            )
            binding.appBar.updatePadding(
                top = systemInsets.top,
            )
            binding.bottomNav?.updatePadding(
                bottom = systemInsets.bottom,
            )
            binding.sideNav?.updatePadding(
                left = 0,
                right = 0,
                bottom = systemInsets.bottom,
                top = systemInsets.top,
            )
            binding.bottomView?.isVisible = systemInsets.bottom > 0
            binding.bottomView?.updateLayoutParams<ViewGroup.LayoutParams> {
                height = systemInsets.bottom
            }
        }
        // Set this as nav view will try to set its own insets and they're hilariously bad
        ViewCompat.setOnApplyWindowInsetsListener(nav) { _, insets -> insets }

        router = Conductor.attachRouter(this, container, savedInstanceState)

        arrayOf(binding.toolbar, binding.searchToolbar).forEach { toolbar ->
            toolbar.setNavigationIconTint(getResourceColor(R.attr.actionBarTintColor))
            toolbar.router = router
        }
        if (router.hasRootController()) {
            nav.selectedItemId =
                when (router.backstack.firstOrNull()?.controller) {
                    is RecentsController -> R.id.nav_recents
                    is BrowseController -> R.id.nav_browse
                    else -> R.id.nav_library
                }
        }

        nav.setOnItemSelectedListener { item ->
            val id = item.itemId
            val currentController = router.backstack.lastOrNull()?.controller
            if (!continueSwitchingTabs && currentController is BottomNavBarInterface) {
                if (!currentController.canChangeTabs {
                    continueSwitchingTabs = true
                    this@MainActivity.nav.selectedItemId = id
                }
                ) {
                    return@setOnItemSelectedListener false
                }
            }
            continueSwitchingTabs = false
            val currentRoot = router.backstack.firstOrNull()
            if (currentRoot?.tag()?.toIntOrNull() != id) {
                setRoot(
                    when (id) {
                        R.id.nav_library -> LibraryController()
                        R.id.nav_recents -> RecentsController()
                        else -> BrowseController()
                    },
                    id,
                )
            } else if (currentRoot.tag()?.toIntOrNull() == id) {
                if (router.backstackSize == 1) {
                    val controller =
                        router.getControllerWithTag(id.toString()) as? BottomSheetController
                    controller?.toggleSheet()
                }
            }
            true
        }

        if (!router.hasRootController()) {
            // Set start screen
            if (!handleIntentAction(intent)) {
                goToStartingTab()
                if (!basePreferences.hasShownOnboarding().get()) {
                    router.pushController(OnboardingController().withFadeInTransaction())
                }
            }
        }

        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.searchToolbar.setNavigationOnClickListener {
            val rootSearchController = router.backstack.lastOrNull()?.controller
            if ((
                rootSearchController is RootSearchInterface ||
                    (currentToolbar != binding.searchToolbar && binding.appBar.useLargeToolbar)
                ) &&
                rootSearchController !is SmallToolbarInterface
            ) {
                binding.searchToolbar.menu.findItem(R.id.action_search)?.expandActionView()
            } else {
                onBackPressedDispatcher.onBackPressed()
            }
        }

        binding.searchToolbar.searchItem?.setOnActionExpandListener(
            object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    val controller = router.backstack.lastOrNull()?.controller
                    binding.appBar.compactSearchMode =
                        binding.appBar.useLargeToolbar && resources.configuration.screenHeightDp < 600
                    if (binding.appBar.compactSearchMode) {
                        setFloatingToolbar(true)
                        controller?.mainRecyclerView?.requestApplyInsets()
                        binding.appBar.y = 0f
                        binding.appBar.updateAppBarAfterY(controller?.mainRecyclerView)
                    }
                    binding.searchToolbar.menu.forEach { it.isVisible = false }
                    lifecycleScope.launchUI {
                        (controller as? BaseLegacyController<*>)?.onActionViewExpand(item)
                        (controller as? SettingsLegacyController)?.onActionViewExpand(item)
                        reEnableBackPressedCallBack()
                    }
                    return true
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    val controller = router.backstack.lastOrNull()?.controller
                    binding.appBar.compactSearchMode = false
                    controller?.mainRecyclerView?.requestApplyInsets()
                    setupSearchTBMenu(binding.toolbar.menu, true)
                    lifecycleScope.launchUI {
                        (controller as? BaseLegacyController<*>)?.onActionViewCollapse(item)
                        (controller as? SettingsLegacyController)?.onActionViewCollapse(item)
                        reEnableBackPressedCallBack()
                    }
                    return true
                }
            },
        )

        binding.appBar.alpha = 1f

        binding.searchToolbar.setOnClickListener {
            binding.searchToolbar.menu.findItem(R.id.action_search)?.expandActionView()
        }

        binding.searchToolbar.setOnMenuItemClickListener {
            if (router.backstack.lastOrNull()?.controller?.onOptionsItemSelected(it) == true) {
                return@setOnMenuItemClickListener true
            } else {
                return@setOnMenuItemClickListener onOptionsItemSelected(it)
            }
        }

        nav.isVisible = !hideBottomNav
        updateControllersWithSideNavChanges()
        binding.bottomView?.visibility = if (hideBottomNav) View.GONE else binding.bottomView?.visibility ?: View.GONE
        nav.alpha = if (hideBottomNav) 0f else 1f
        router.addChangeListener(
            object : ControllerChangeHandler.ControllerChangeListener {
                override fun onChangeStarted(
                    to: Controller?,
                    from: Controller?,
                    isPush: Boolean,
                    container: ViewGroup,
                    handler: ControllerChangeHandler,
                ) {
                    to?.view?.alpha = 1f
                    syncActivityViewWithController(to, from, isPush)
                    binding.appBar.isVisible = !hideAppBar
                    binding.appBar.alpha = 1f
                    if (binding.backShadow.isVisible && !isPush) {
                        val bA = ObjectAnimator.ofFloat(binding.backShadow, View.ALPHA, 0f)
                        from?.view?.let { view ->
                            bA.addUpdateListener {
                                binding.backShadow.x = view.x - binding.backShadow.width
                                if (router.backstackSize == 1) {
                                    to?.view?.let { toView ->
                                        nav.x = toView.x
                                    }
                                }
                            }
                        }
                        bA.doOnEnd {
                            binding.backShadow.alpha = 0.25f
                            binding.backShadow.isVisible = false
                            nav.x = 0f
                        }
                        bA.duration = 150
                        bA.interpolator = DecelerateInterpolator(backVelocity.takeIf { it != 0f } ?: 1f)
                        bA.start()
                    }
                    if (!isPush || router.backstackSize == 1) {
                        nav.translationY = 0f
                    }
                    snackBar?.dismiss()
                }

                override fun onChangeCompleted(
                    to: Controller?,
                    from: Controller?,
                    isPush: Boolean,
                    container: ViewGroup,
                    handler: ControllerChangeHandler,
                ) {
                    to?.view?.x = 0f
                    nav.translationY = 0f
                    backVelocity = 0f
                    showDLQueueTutorial()
                    if (!(from is DialogController || to is DialogController) && from != null) {
                        from.view?.alpha = 0f
                    }
                    if (router.backstackSize == 1) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !isPush) {
                            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
                        }
                    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        @Suppress("DEPRECATION")
                        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                    }
                }
            },
        )

        syncActivityViewWithController(router.backstack.lastOrNull()?.controller)

        val navIcon = if (router.backstackSize > 1) backDrawable else null
        binding.toolbar.navigationIcon = navIcon
        (router.backstack.lastOrNull()?.controller as? BaseLegacyController<*>)?.setTitle()
        (router.backstack.lastOrNull()?.controller as? SettingsLegacyController)?.setTitle()

        splashScreen?.configure()

        getExtensionUpdates(true)

        preferences.extensionUpdatesCount()
            .changesIn(lifecycleScope) {
                setExtensionsBadge()
            }
        preferences.incognitoMode()
            .changesIn(lifecycleScope) {
                binding.toolbar.setIncognitoMode(it)
                binding.searchToolbar.setIncognitoMode(it)
                SecureActivityDelegate.setSecure(this)
            }
        preferences.sideNavIconAlignment()
            .changesIn(lifecycleScope) {
                binding.sideNav?.menuGravity = when (it) {
                    1 -> Gravity.CENTER
                    2 -> Gravity.BOTTOM
                    else -> Gravity.TOP
                }
            }
        setFloatingToolbar(canShowFloatingToolbar(router.backstack.lastOrNull()?.controller), changeBG = false)

        lifecycleScope.launchUI {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@MainActivity).windowLayoutInfo(this@MainActivity)
                    .collect { newLayoutInfo ->
                        hingeGapSize = 0
                        for (displayFeature: DisplayFeature in newLayoutInfo.displayFeatures) {
                            if (displayFeature is FoldingFeature && displayFeature.occlusionType == FoldingFeature.OcclusionType.FULL &&
                                displayFeature.isSeparating && displayFeature.orientation == FoldingFeature.Orientation.VERTICAL
                            ) {
                                hingeGapSize = displayFeature.bounds.width()
                            }
                        }
                        if (hingeGapSize > 0) {
                            (router.backstack.lastOrNull()?.controller as? HingeSupportedController)?.updateForHinge()
                        }
                    }
            }
        }
    }

    fun reEnableBackPressedCallBack() {
        val returnToStart = preferences.backReturnsToStart().get() && this !is SearchActivity
        backPressedCallback?.isEnabled = actionMode != null ||
            (binding.searchToolbar.hasExpandedActionView() && binding.cardFrame.isVisible) ||
            router.canStillGoBack() || (returnToStart && startingTab() != nav.selectedItemId)
    }

    override fun onTitleChanged(title: CharSequence?, color: Int) {
        super.onTitleChanged(title, color)
        binding.searchToolbar.title = searchTitle
        val onExpandedController = if (this::router.isInitialized) router.backstack.lastOrNull()?.controller !is SmallToolbarInterface else false
        binding.appBar.setTitle(title, onExpandedController)
    }

    var searchTitle: String?
        get() {
            return try {
                (router.backstack.lastOrNull()?.controller as? BaseLegacyController<*>)?.getSearchTitle()
                    ?: (router.backstack.lastOrNull()?.controller as? SettingsLegacyController)?.getSearchTitle()
            } catch (_: Exception) {
                binding.searchToolbar.title?.toString()
            }
        }
        set(title) {
            binding.searchToolbar.title = title
        }

    open fun setFloatingToolbar(show: Boolean, solidBG: Boolean = false, changeBG: Boolean = true, showSearchAnyway: Boolean = false) {
        val controller = if (this::router.isInitialized) router.backstack.lastOrNull()?.controller else null
        val useLargeTB = binding.appBar.useLargeToolbar
        val onSearchController = canShowFloatingToolbar(controller)
        val onSmallerController = controller is SmallToolbarInterface || !useLargeTB
        currentToolbar = if (show && ((showSearchAnyway && onSearchController) || onSmallerController)) {
            binding.searchToolbar
        } else {
            binding.toolbar
        }
        binding.toolbar.isVisible = !(onSmallerController && onSearchController)
        setSearchTBLongClick()
        val showSearchBar = (show || showSearchAnyway) && onSearchController
        val isAppBarVisible = binding.appBar.isVisible
        val needsAnim = if (showSearchBar) {
            !binding.cardFrame.isVisible || binding.cardFrame.alpha < 1f
        } else {
            binding.cardFrame.isVisible || binding.cardFrame.alpha > 0f
        }
        if (this::router.isInitialized && needsAnim && binding.appBar.useLargeToolbar && !onSmallerController &&
            (showSearchAnyway || isAppBarVisible)
        ) {
            binding.appBar.background = null
            searchBarAnimation?.cancel()
            if (showSearchBar && !binding.cardFrame.isVisible) {
                binding.cardFrame.alpha = 0f
                binding.cardFrame.isVisible = true
            }
            val endValue = if (showSearchBar) 1f else 0f
            val tA = ValueAnimator.ofFloat(binding.cardFrame.alpha, endValue)
            tA.addUpdateListener { binding.cardFrame.alpha = it.animatedValue as Float }
            tA.doOnEnd { binding.cardFrame.isVisible = showSearchBar }
            tA.duration = (abs(binding.cardFrame.alpha - endValue) * 150).roundToLong()
            searchBarAnimation = tA
            tA.start()
        } else if (this::router.isInitialized &&
            (!binding.appBar.useLargeToolbar || onSmallerController || !isAppBarVisible)
        ) {
            binding.cardFrame.alpha = 1f
            binding.cardFrame.isVisible = showSearchBar
        }
        val bgColor = binding.appBar.backgroundColor ?: Color.TRANSPARENT
        if (changeBG && (if (solidBG) bgColor == Color.TRANSPARENT else false)) {
            binding.appBar.setBackgroundColor(
                if (show && !solidBG) Color.TRANSPARENT else getResourceColor(R.attr.colorSurface),
            )
        }
        setupSearchTBMenu(binding.toolbar.menu)
        if (currentToolbar != binding.searchToolbar) {
            binding.searchToolbar.menu?.children?.toList()?.forEach {
                it.isVisible = false
            }
        }
        val onRoot = !this::router.isInitialized || router.backstackSize == 1
        if (!useLargeTB) {
            binding.searchToolbar.navigationIcon = if (onRoot) searchDrawable else backDrawable
        } else if (showSearchAnyway) {
            binding.searchToolbar.navigationIcon = if (!show || onRoot) searchDrawable else backDrawable
        }
        binding.searchToolbar.title = searchTitle
    }

    private fun setSearchTBLongClick() {
        binding.searchToolbar.setOnLongClickListener {
            binding.searchToolbar.menu.findItem(R.id.action_search)?.expandActionView()
            val visibleController = router.backstack.lastOrNull()?.controller as? BaseLegacyController<*>
            val longClickQuery = visibleController?.onSearchActionViewLongClickQuery()
            if (longClickQuery != null) {
                binding.searchToolbar.searchView?.setQuery(longClickQuery, true)
                return@setOnLongClickListener true
            }
            val clipboard: ClipboardManager? = getSystemService()
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                clipboard.primaryClip?.getItemAt(0)?.text?.let { text ->
                    binding.searchToolbar.searchView?.setQuery(text, true)
                }
            }
            true
        }
    }

    private fun setNavBarColor(insets: WindowInsetsCompat?) {
        if (insets == null) return
        window.navigationBarColor = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1 -> {
                // basically if in landscape on a phone, solid black bar
                // otherwise translucent dark theme or black if light theme
                when {
                    insets.hasSideNavBar() -> Color.BLACK
                    isInNightMode() -> ColorUtils.setAlphaComponent(
                        getResourceColor(R.attr.colorPrimaryVariant),
                        179,
                    )
                    else -> Color.argb(179, 0, 0, 0)
                }
            }
            // if the android q+ device has gesture nav, transparent nav bar
            // this is here in case some crazy with a notch uses landscape
            insets.isBottomTappable() -> {
                getColor(android.R.color.transparent)
            }
            // if in landscape with 2/3 button mode, fully opaque nav bar
            insets.hasSideNavBar() -> {
                getResourceColor(R.attr.colorPrimaryVariant)
            }
            // if in portrait with 2/3 button mode, translucent nav bar
            else -> {
                ColorUtils.setAlphaComponent(
                    getResourceColor(R.attr.colorPrimaryVariant),
                    179,
                )
            }
        }
    }

    override fun startSupportActionMode(callback: ActionMode.Callback): ActionMode? {
        window?.statusBarColor = getResourceColor(R.attr.colorPrimaryVariant)
        actionMode = super.startSupportActionMode(callback)
        reEnableBackPressedCallBack()
        return actionMode
    }

    override fun onSupportActionModeFinished(mode: ActionMode) {
        actionMode = null
        reEnableBackPressedCallBack()
        launchUI {
            val scale = Settings.Global.getFloat(
                contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f,
            )
            val duration = resources.getInteger(android.R.integer.config_mediumAnimTime) * scale
            delay(duration.toLong())
            delay(100)
            if (Color.alpha(window?.statusBarColor ?: Color.BLACK) >= 255) {
                window?.statusBarColor =
                    getResourceColor(
                        android.R.attr.statusBarColor,
                    )
            }
        }
        super.onSupportActionModeFinished(mode)
    }

    fun setStatusBarColorTransparent(show: Boolean) {
        window?.statusBarColor = if (show) {
            ColorUtils.setAlphaComponent(window?.statusBarColor ?: Color.TRANSPARENT, 0)
        } else {
            val color = getResourceColor(android.R.attr.statusBarColor)
            ColorUtils.setAlphaComponent(window?.statusBarColor ?: color, Color.alpha(color))
        }
    }

    private fun setExtensionsBadge() {
        val updates = preferences.extensionUpdatesCount().get()
        if (updates > 0) {
            val badge = nav.getOrCreateBadge(R.id.nav_browse)
            badge.number = updates
        } else {
            nav.removeBadge(R.id.nav_browse)
        }
    }

    override fun onResume() {
        super.onResume()
        checkForAppUpdates()
        getExtensionUpdates(false)
        setExtensionsBadge()
        DownloadJob.callListeners(downloadManager = downloadManager)
        showDLQueueTutorial()
        reEnableBackPressedCallBack()
    }

    private fun showDLQueueTutorial() {
        if (router.backstackSize == 1 && this !is SearchActivity &&
            downloadManager.hasQueue() && !preferences.shownDownloadQueueTutorial().get()
        ) {
            if (!isBindingInitialized) return
            val recentsItem = nav.getItemView(R.id.nav_recents) ?: return
            preferences.shownDownloadQueueTutorial().set(true)
            TapTargetView.showFor(
                this,
                TapTarget.forView(
                    recentsItem,
                    getString(R.string.manage_whats_downloading),
                    getString(R.string.visit_recents_for_download_queue),
                ).outerCircleColorInt(getResourceColor(R.attr.colorSecondary)).outerCircleAlpha(0.95f)
                    .titleTextSize(
                        20,
                    )
                    .titleTextColorInt(getResourceColor(R.attr.colorOnSecondary)).descriptionTextSize(16)
                    .descriptionTextColorInt(getResourceColor(R.attr.colorOnSecondary))
                    .icon(contextCompatDrawable(R.drawable.ic_recent_read_32dp))
                    .targetCircleColor(android.R.color.white)
                    .targetRadius(45),
                object : TapTargetView.Listener() {
                    override fun onTargetClick(view: TapTargetView) {
                        super.onTargetClick(view)
                        nav.selectedItemId = R.id.nav_recents
                    }
                },
            )
        }
    }

    override fun onPause() {
        super.onPause()
        snackBar?.dismiss()
        setStartingTab()
        saveExtras()
    }

    private fun saveExtras() {
        mangaShortcutManager.updateShortcuts(this)
        MangaCoverMetadata.savePrefs()
    }

    private fun checkForAppUpdates() {
        if (isUpdaterEnabled) {
            lifecycleScope.launchIO {
                try {
                    val result = updateChecker.checkForUpdate(this@MainActivity)
                    if (result is AppUpdateResult.NewUpdate) {
                        val body = result.release.info
                        val url = result.release.downloadLink
                        val isBeta = result.release.preRelease == true

                        // Create confirmation window
                        withContext(Dispatchers.Main) {
                            showNotificationPermissionPrompt()
                            AppUpdateNotifier.releasePageUrl = result.release.releaseLink
                            AboutController.NewUpdateDialogController(body, url, isBeta).showDialog(router)
                        }
                    }
                } catch (error: Exception) {
                    Timber.e(error)
                }
            }
        }
    }

    fun getExtensionUpdates(force: Boolean) {
        if ((force && extensionManager.availableExtensionsFlow.value.isEmpty()) ||
            Date().time >= preferences.lastExtCheck().get() + TimeUnit.HOURS.toMillis(6)
        ) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    extensionManager.findAvailableExtensions()
                    val pendingUpdates = ExtensionApi().checkForUpdates(
                        this@MainActivity,
                        extensionManager.availableExtensionsFlow.value.takeIf { it.isNotEmpty() },
                    )
                    preferences.extensionUpdatesCount().set(pendingUpdates.size)
                    preferences.lastExtCheck().set(Date().time)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun showNotificationPermissionPrompt(showAnyway: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val notificationPermission = Manifest.permission.POST_NOTIFICATIONS
        val hasPermission = ActivityCompat.checkSelfPermission(this, notificationPermission)
        if (hasPermission != PackageManager.PERMISSION_GRANTED &&
            (!preferences.hasShownNotifPermission().get() || showAnyway)
        ) {
            preferences.hasShownNotifPermission().set(true)
            requestNotificationPermissionLauncher.launch((notificationPermission))
        }
    }

    fun showColourProfilePicker() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        requestColourProfile.launch(arrayOf("*/*"))
    }

    override fun onNewIntent(intent: Intent) {
        if (!handleIntentAction(intent)) {
            super.onNewIntent(intent)
        }
    }

    protected open fun handleIntentAction(intent: Intent): Boolean {
        val notificationId = intent.getIntExtra("notificationId", -1)
        if (notificationId > -1) {
            NotificationReceiver.dismissNotification(
                applicationContext,
                notificationId,
                intent.getIntExtra("groupId", 0),
            )
        }
        when (intent.action) {
            SHORTCUT_LIBRARY -> nav.selectedItemId = R.id.nav_library
            SHORTCUT_RECENTLY_UPDATED, SHORTCUT_RECENTLY_READ, SHORTCUT_RECENTS -> {
                if (nav.selectedItemId != R.id.nav_recents) {
                    nav.selectedItemId = R.id.nav_recents
                } else {
                    router.popToRoot()
                }
                if (intent.action == SHORTCUT_RECENTS) return true
                nav.post {
                    val controller =
                        router.backstack.firstOrNull()?.controller as? RecentsController
                    controller?.tempJumpTo(
                        when (intent.action) {
                            SHORTCUT_RECENTLY_UPDATED -> RecentsViewType.Updates
                            else -> RecentsViewType.History
                        },
                    )
                }
            }
            SHORTCUT_BROWSE -> nav.selectedItemId = R.id.nav_browse
            SHORTCUT_EXTENSIONS -> {
                if (nav.selectedItemId != R.id.nav_browse) {
                    nav.selectedItemId = R.id.nav_browse
                } else {
                    router.popToRoot()
                }
                nav.post {
                    val controller =
                        router.backstack.firstOrNull()?.controller as? BrowseController
                    controller?.showSheet()
                }
            }
            SHORTCUT_MANGA -> {
                val extras = intent.extras ?: return false
                if (router.backstack.isEmpty()) nav.selectedItemId = R.id.nav_library
                router.pushController(MangaDetailsController(extras).withFadeTransaction())
            }
            SHORTCUT_UPDATE_NOTES -> {
                val extras = intent.extras ?: return false
                if (router.backstack.isEmpty()) nav.selectedItemId = R.id.nav_library
                if (router.backstack.lastOrNull()?.controller !is AboutController.NewUpdateDialogController) {
                    AboutController.NewUpdateDialogController(extras).showDialog(router)
                }
            }
            SHORTCUT_SOURCE -> {
                val extras = intent.extras ?: return false
                if (router.backstack.isEmpty()) nav.selectedItemId = R.id.nav_library
                router.pushController(BrowseSourceController(extras).withFadeTransaction())
            }
            SHORTCUT_DOWNLOADS -> {
                nav.selectedItemId = R.id.nav_recents
                router.popToRoot()
                nav.post {
                    val controller =
                        router.backstack.firstOrNull()?.controller as? RecentsController
                    controller?.showSheet()
                }
            }
            Intent.ACTION_VIEW -> {
                if (router.backstack.isEmpty()) nav.selectedItemId = R.id.nav_library
                if (intent.scheme == "tachiyomi" && intent.data?.host == "add-repo") {
                    intent.data?.getQueryParameter("url")?.let { repoUrl ->
                        router.popToRoot()
                        router.pushController(ExtensionRepoController(repoUrl).withFadeTransaction())
                    }
                }
            }
            else -> return false
        }

        splashState.ready = true
        return true
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        when (val controller = router.backstack.lastOrNull()?.controller) {
            is MangaDetailsController -> {
                val source = controller.presenter.source as? HttpSource ?: return
                val url = try {
                    source.getMangaUrl(controller.presenter.manga)
                } catch (e: Exception) {
                    return
                }
                outContent.webUri = Uri.parse(url)
            }
            is BrowseSourceController -> {
                val source = controller.presenter.source as? HttpSource ?: return
                outContent.webUri = Uri.parse(source.baseUrl)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overflowDialog?.dismiss()
        overflowDialog = null
        if (isBindingInitialized) {
            binding.appBar.mainActivity = null
            binding.toolbar.setNavigationOnClickListener(null)
            binding.searchToolbar.setNavigationOnClickListener(null)
        }
    }

    private fun pressingBack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ViewCompat.getRootWindowInsets(window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        ) {
            WindowInsetsControllerCompat(window, binding.root).hide(WindowInsetsCompat.Type.ime())
        } else if (actionMode != null) {
            actionMode?.finish()
        } else if (binding.searchToolbar.hasExpandedActionView() && binding.cardFrame.isVisible) {
            binding.searchToolbar.collapseActionView()
        } else {
            backPress()
        }
    }

    override fun finish() {
        if (!preferences.backReturnsToStart().get() && this !is SearchActivity) {
            setStartingTab()
        }
        if (this !is SearchActivity) {
            SecureActivityDelegate.locked = true
        }
        saveExtras()
        super.finish()
    }

    protected open fun backPress() {
        val controller = router.backstack.lastOrNull()?.controller
        if (if (router.backstackSize == 1) controller?.handleBack() != true else !router.handleBack()) {
            if (preferences.backReturnsToStart().get() && this !is SearchActivity &&
                startingTab() != nav.selectedItemId
            ) {
                goToStartingTab()
            }
        }
    }

    protected val nav: NavigationBarView
        get() = binding.bottomNav ?: binding.sideNav!!

    private fun setStartingTab() {
        if (this is SearchActivity || !isBindingInitialized) return
        if (nav.selectedItemId != R.id.nav_browse &&
            preferences.startingTab().get() >= 0
        ) {
            preferences.startingTab().set(
                when (nav.selectedItemId) {
                    R.id.nav_library -> 0
                    else -> 1
                },
            )
        }
    }

    @IdRes
    private fun startingTab(): Int {
        return when (preferences.startingTab().get()) {
            0, -1 -> R.id.nav_library
            1, -2 -> R.id.nav_recents
            -3 -> R.id.nav_browse
            else -> R.id.nav_library
        }
    }

    private fun goToStartingTab() {
        nav.selectedItemId = startingTab()
    }

    fun goToTab(@IdRes id: Int) {
        nav.selectedItemId = id
    }

    private fun setRoot(controller: Controller, id: Int) {
        router.setRoot(controller.withFadeInTransaction().tag(id.toString()))
    }

    override fun onPreparePanel(featureId: Int, view: View?, menu: Menu): Boolean {
        val prepare = super.onPreparePanel(featureId, view, menu)
        if (canShowFloatingToolbar(router.backstack.lastOrNull()?.controller)) {
            val searchItem = menu.findItem(R.id.action_search)
            searchItem?.isVisible = false
        }
        setupSearchTBMenu(menu)
        return prepare
    }

    fun setSearchTBMenuIfInvalid() = setupSearchTBMenu(binding.toolbar.menu)

    private fun setupSearchTBMenu(menu: Menu?, showAnyway: Boolean = false) {
        val toolbar = binding.searchToolbar
        val currentItemsId = toolbar.menu.children.toList().map { it.itemId }
        val newMenuIds = menu?.children?.toList()?.map { it.itemId }.orEmpty()
        menu?.children?.toList()?.let { menuItems ->
            val searchActive = toolbar.isSearchExpanded
            menuItems.forEachIndexed { index, oldMenuItem ->
                if (oldMenuItem.itemId == R.id.action_search) return@forEachIndexed
                val isVisible = oldMenuItem.isVisible &&
                    (currentToolbar == toolbar || !binding.appBar.useLargeToolbar) && (!searchActive || showAnyway)
                addOrUpdateMenuItem(oldMenuItem, toolbar.menu, isVisible, currentItemsId, index)
            }
        }
        toolbar.menu.children.toList().forEach {
            if (it.itemId != R.id.action_search && !newMenuIds.contains(it.itemId)) {
                toolbar.menu.removeItem(it.itemId)
            }
        }

        // Done because sometimes ActionMenuItemViews have a width/height of 0 and never update
        val actionMenuView = toolbar.findChild<ActionMenuView>()
        if (binding.appBar.isVisible && toolbar.isVisible &&
            toolbar.width > 0 && actionMenuView?.children?.any { it.width == 0 } == true
        ) {
            actionMenuView.children.forEach {
                if (it !is ActionMenuItemView) return@forEach
                it.updateLayoutParams<ViewGroup.LayoutParams> {
                    width = actionButtonSize.first
                    height = actionButtonSize.second
                }
            }
            actionMenuView.requestLayout()
        }

        val controller = if (this::router.isInitialized) router.backstack.lastOrNull()?.controller else null
        if (canShowFloatingToolbar(controller)) {
            binding.toolbar.menu.removeItem(R.id.action_search)
        }
    }

    private fun addOrUpdateMenuItem(oldMenuItem: MenuItem, menu: Menu, isVisible: Boolean, currentItemsId: List<Int>, index: Int) {
        if (currentItemsId.contains(oldMenuItem.itemId)) {
            val newItem = menu.findItem(oldMenuItem.itemId) ?: return
            if (newItem.icon != oldMenuItem.icon) {
                newItem.icon = oldMenuItem.icon
            }
            if (newItem.isVisible != isVisible) {
                newItem.isVisible = isVisible
            }
            updateSubMenu(oldMenuItem, newItem)
            return
        }
        val menuItem = if (oldMenuItem.hasSubMenu()) {
            menu.addSubMenu(
                oldMenuItem.groupId,
                oldMenuItem.itemId,
                index,
                oldMenuItem.title,
            ).item
        } else {
            menu.add(
                oldMenuItem.groupId,
                oldMenuItem.itemId,
                index,
                oldMenuItem.title,
            )
        }
        menuItem.isVisible = isVisible
        menuItem.actionView = oldMenuItem.actionView
        menuItem.icon = oldMenuItem.icon
        menuItem.isChecked = oldMenuItem.isChecked
        updateSubMenu(oldMenuItem, menuItem)
        menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
    }

    @SuppressLint("RestrictedApi")
    private fun updateSubMenu(oldMenuItem: MenuItem, menuItem: MenuItem) {
        if (oldMenuItem.hasSubMenu()) {
            val oldSubMenu = oldMenuItem.subMenu ?: return
            val newMenuIds = oldSubMenu.children.toList().map { it.itemId }
            val currentItemsId = menuItem.subMenu?.children?.toList()?.map { it.itemId } ?: return
            var isExclusiveCheckable = false
            var isCheckable = false
            oldSubMenu.children.toList().forEachIndexed { index, oldSubMenuItem ->
                val isSubVisible = oldSubMenuItem.isVisible
                addOrUpdateMenuItem(oldSubMenuItem, menuItem.subMenu!!, isSubVisible, currentItemsId, index)
                if (!isExclusiveCheckable) {
                    isExclusiveCheckable = (oldSubMenuItem as? MenuItemImpl)?.isExclusiveCheckable ?: false
                }
                if (!isCheckable) {
                    isCheckable = oldSubMenuItem.isCheckable
                }
            }
            menuItem.subMenu?.setGroupCheckable(oldSubMenu.children.first().groupId, isCheckable, isExclusiveCheckable)
            menuItem.subMenu?.children?.toList()?.forEach {
                if (!newMenuIds.contains(it.itemId)) {
                    menuItem.subMenu?.removeItem(it.itemId)
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // Initialize option to open catalogue settings.
            R.id.action_more -> {
                if (overflowDialog != null) return false
                val overflowDialog = OverflowDialog(this)
                this.overflowDialog = overflowDialog
                overflowDialog.blurBehindWindow(
                    window,
                    onDismiss = {
                        this.overflowDialog = null
                    },
                )
                overflowDialog.show()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return super.onOptionsItemSelected(item)
    }

    fun showSettings() {
        router.pushController(SettingsMainController().withFadeTransaction())
    }

    fun showAbout() {
        router.pushController(AboutController().withFadeTransaction())
    }

    fun showStats() {
        router.pushController(StatsController().withFadeTransaction())
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            gestureDetector?.onTouchEvent(it)
            (router.backstack.lastOrNull()?.controller as? LibraryController)?.handleGeneralEvent(it)
        }
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            if (snackBar != null && snackBar!!.isShown) {
                val sRect = Rect()
                snackBar!!.view.getGlobalVisibleRect(sRect)

                val extRect: Rect? = if (extraViewForUndo != null) Rect() else null
                extraViewForUndo?.getGlobalVisibleRect(extRect)
                // This way the snackbar will only be dismissed if
                // the user clicks outside it.
                if (canDismissSnackBar &&
                    !sRect.contains(ev.x.toInt(), ev.y.toInt()) &&
                    (extRect == null || !extRect.contains(ev.x.toInt(), ev.y.toInt()))
                ) {
                    snackBar?.dismiss()
                    snackBar = null
                    extraViewForUndo = null
                }
            } else if (snackBar != null) {
                snackBar = null
                extraViewForUndo = null
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    protected fun canShowFloatingToolbar(controller: Controller?) =
        (controller is FloatingSearchInterface && controller.showFloatingBar())

    protected open fun syncActivityViewWithController(
        to: Controller?,
        from: Controller? = null,
        isPush: Boolean = false,
    ) {
        if (from is DialogController || to is DialogController) {
            return
        }
        reEnableBackPressedCallBack()
        setFloatingToolbar(canShowFloatingToolbar(to))
        val onRoot = router.backstackSize == 1
        val navIcon = if (onRoot) searchDrawable else backDrawable
        binding.toolbar.navigationIcon = if (onRoot) null else backDrawable
        binding.searchToolbar.navigationIcon = if (binding.appBar.useLargeToolbar) searchDrawable else navIcon
        binding.searchToolbar.subtitle = null

        nav.visibility = if (!hideBottomNav) View.VISIBLE else nav.visibility
        if (nav == binding.sideNav) {
            nav.isVisible = !hideBottomNav
            updateControllersWithSideNavChanges(from)
            nav.alpha = 1f
        } else {
            animationSet?.cancel()
            animationSet = AnimatorSet()
            val alphaAnimation = ValueAnimator.ofFloat(
                nav.alpha,
                if (hideBottomNav) 0f else 1f,
            )
            alphaAnimation.addUpdateListener { valueAnimator ->
                nav.alpha = valueAnimator.animatedValue as Float
            }
            alphaAnimation.doOnEnd {
                nav.isVisible = !hideBottomNav
                binding.bottomView?.visibility =
                    if (hideBottomNav) {
                        View.GONE
                    } else {
                        binding.bottomView?.visibility
                            ?: View.GONE
                    }
            }
            alphaAnimation.duration = 150
            animationSet?.playTogether(alphaAnimation)
            animationSet?.start()
        }
    }

    private fun updateControllersWithSideNavChanges(extraController: Controller? = null) {
        if (!isBindingInitialized || !this::router.isInitialized || this is SearchActivity) return
        binding.sideNav?.let { sideNav ->
            val controllers = (router.backstack.map { it?.controller } + extraController)
                .filterNotNull()
                .distinct()
            val navWidth = sideNav.width.takeIf { it != 0 } ?: 80.dpToPx
            controllers.forEach { controller ->
                val isRootController = controller is RootSearchInterface
                if (controller.view?.layoutParams !is ViewGroup.MarginLayoutParams) return@forEach
                controller.view?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    marginStart = if (sideNav.isVisible) {
                        if (isRootController) 0 else -navWidth
                    } else {
                        if (isRootController) navWidth else 0
                    }
                }
            }
        }
    }

    fun showTabBar(show: Boolean, animate: Boolean = true) {
        tabAnimation?.cancel()
        if (animate) {
            if (show && !binding.tabsFrameLayout.isVisible) {
                binding.tabsFrameLayout.alpha = 0f
                binding.tabsFrameLayout.isVisible = true
            }
            val tA = ValueAnimator.ofFloat(
                binding.tabsFrameLayout.alpha,
                if (show) 1f else 0f,
            )
            tA.addUpdateListener { valueAnimator ->
                binding.tabsFrameLayout.alpha = valueAnimator.animatedValue as Float
            }
            tA.doOnEnd {
                binding.tabsFrameLayout.isVisible = show
                if (!show) {
                    binding.mainTabs.clearOnTabSelectedListeners()
                    binding.mainTabs.removeAllTabs()
                }
            }
            tA.duration = 100
            tabAnimation = tA
            tA.start()
        } else {
            binding.tabsFrameLayout.isVisible = show
        }
    }

    fun downloadStatusChanged(downloading: Boolean) {
        lifecycleScope.launchUI {
            val hasQueue = downloading || downloadManager.hasQueue()
            if (hasQueue) {
                val badge = nav.getOrCreateBadge(R.id.nav_recents)
                badge.number = downloadManager.queue.size
                if (downloading) badge.backgroundColor = -870219 else badge.backgroundColor = Color.GRAY
                showDLQueueTutorial()
            } else {
                nav.removeBadge(R.id.nav_recents)
            }
        }
    }

    private fun whatsNewSheet() = MaterialMenuSheet(
        this,
        listOf(
            MaterialMenuSheet.MenuSheetItem(
                0,
                textRes = R.string.whats_new_this_release,
                drawable = R.drawable.ic_new_releases_24dp,
            ),
            MaterialMenuSheet.MenuSheetItem(
                1,
                textRes = R.string.close,
                drawable = R.drawable.ic_close_24dp,
            ),
        ),
        title = getString(R.string.updated_to_, BuildConfig.VERSION_NAME),
        showDivider = true,
        selectedId = 0,
        onMenuItemClicked = { _, item ->
            if (item == 0) {
                try {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        RELEASE_URL.toUri(),
                    )
                    startActivity(intent)
                } catch (e: Throwable) {
                    toast(e.message)
                }
            }
            true
        },
    )

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        private var startingX = 0f
        private var startingY = 0f
        override fun onDown(e: MotionEvent): Boolean {
            startingX = e.x
            startingY = e.y
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            var result = false
            val diffY = e2.y - startingY
            val diffX = e2.x - startingX
            if (abs(diffX) <= abs(diffY)) {
                val sheetRect = Rect()
                nav.getGlobalVisibleRect(sheetRect)
                if (sheetRect.contains(startingX.toInt(), startingY.toInt()) &&
                    abs(diffY) > Companion.SWIPE_THRESHOLD &&
                    abs(velocityY) > Companion.SWIPE_VELOCITY_THRESHOLD &&
                    diffY <= 0
                ) {
                    val bottomSheetController =
                        router.backstack.lastOrNull()?.controller as? BottomSheetController
                    bottomSheetController?.showSheet()
                } else if (nav == binding.sideNav &&
                    sheetRect.contains(startingX.toInt(), startingY.toInt()) &&
                    abs(diffY) > Companion.SWIPE_THRESHOLD &&
                    abs(velocityY) > Companion.SWIPE_VELOCITY_THRESHOLD &&
                    diffY > 0
                ) {
                    val bottomSheetController =
                        router.backstack.lastOrNull()?.controller as? BottomSheetController
                    bottomSheetController?.hideSheet()
                }
                result = true
            }
            return result
        }
    }

    companion object {

        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100

        const val MAIN_ACTIVITY = "eu.kanade.tachiyomi.ui.main.MainActivity"

        // Shortcut actions
        const val SHORTCUT_LIBRARY = "eu.kanade.tachiyomi.SHOW_LIBRARY"
        const val SHORTCUT_RECENTS = "eu.kanade.tachiyomi.SHOW_RECENTS"
        const val SHORTCUT_RECENTLY_UPDATED = "eu.kanade.tachiyomi.SHOW_RECENTLY_UPDATED"
        const val SHORTCUT_RECENTLY_READ = "eu.kanade.tachiyomi.SHOW_RECENTLY_READ"
        const val SHORTCUT_BROWSE = "eu.kanade.tachiyomi.SHOW_BROWSE"
        const val SHORTCUT_DOWNLOADS = "eu.kanade.tachiyomi.SHOW_DOWNLOADS"
        const val SHORTCUT_MANGA = "eu.kanade.tachiyomi.SHOW_MANGA"
        const val SHORTCUT_MANGA_BACK = "eu.kanade.tachiyomi.SHOW_MANGA_BACK"
        const val SHORTCUT_UPDATE_NOTES = "eu.kanade.tachiyomi.SHOW_UPDATE_NOTES"
        const val SHORTCUT_SOURCE = "eu.kanade.tachiyomi.SHOW_SOURCE"
        const val SHORTCUT_READER_SETTINGS = "eu.kanade.tachiyomi.READER_SETTINGS"
        const val SHORTCUT_EXTENSIONS = "eu.kanade.tachiyomi.EXTENSIONS"

        const val INTENT_SEARCH = "eu.kanade.tachiyomi.SEARCH"
        const val INTENT_SEARCH_QUERY = "query"
        const val INTENT_SEARCH_FILTER = "filter"

        var chapterIdToExitTo = 0L
        var backVelocity = 0f
    }
}

interface BottomNavBarInterface {
    fun canChangeTabs(block: () -> Unit): Boolean
}

interface RootSearchInterface {
    fun expandSearch() {
        if (this is Controller) {
            val mainActivity = activity as? MainActivity ?: return
            mainActivity.binding.searchToolbar.menu.findItem(R.id.action_search)?.expandActionView()
        }
    }
}

interface TabbedInterface

interface HingeSupportedController {
    fun updateForHinge()
}

interface SearchControllerInterface : FloatingSearchInterface, SmallToolbarInterface

interface FloatingSearchInterface {
    fun searchTitle(title: String?): String? {
        if (this is Controller) {
            return activity?.getString(R.string.search_, title)
        }
        return title
    }

    fun showFloatingBar() = true
}

interface BottomSheetController {
    fun showSheet()
    fun hideSheet()
    fun toggleSheet()
}
