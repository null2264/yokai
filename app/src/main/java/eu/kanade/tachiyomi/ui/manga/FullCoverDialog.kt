package eu.kanade.tachiyomi.ui.manga

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.activity.BackEventCompat
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
import androidx.core.animation.addListener
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.transition.ChangeBounds
import androidx.transition.ChangeImageTransform
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.google.android.material.shape.CornerFamily
import eu.kanade.tachiyomi.R
import yokai.i18n.MR
import yokai.util.lang.getString
import dev.icerock.moko.resources.compose.stringResource
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.FullCoverDialogBinding
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.powerManager
import eu.kanade.tachiyomi.util.system.rootWindowInsetsCompat
import eu.kanade.tachiyomi.util.view.animateBlur
import uy.kohesive.injekt.injectLazy
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import android.R as AR

class FullCoverDialog(val controller: MangaDetailsController, drawable: Drawable, private val thumbView: View) :
    ComponentDialog(controller.activity!!, R.style.FullCoverDialogTheme) {

    val activity = controller.activity
    val binding = FullCoverDialogBinding.inflate(LayoutInflater.from(context), null, false)
    val preferences: PreferencesHelper by injectLazy()

    val velocityTracker: VelocityTracker by lazy { VelocityTracker.obtain() }
    private val ratio = 5f.dpToPx
    private val fullRatio = 0f
    private val shortAnimationDuration = (
        activity?.resources?.getInteger(
            AR.integer.config_shortAnimTime,
        ) ?: 0
        ).toLong()

    private val powerSaverChangeReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !this@FullCoverDialog.context.powerManager.isPowerSaveMode
            window?.setDimAmount(if (canBlur) 0.45f else 0.77f)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
            if (canBlur) {
                activity?.window?.decorView?.setRenderEffect(
                    RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP),
                )
            } else {
                activity?.window?.decorView?.setRenderEffect(null)
            }
        }
    }

    init {
        val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !context.powerManager.isPowerSaveMode
        window?.setDimAmount(if (canBlur) 0.45f else 0.77f)
        setContentView(binding.root)

        val filter = IntentFilter()
        filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.registerReceiver(context, powerSaverChangeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }

        val backPressedCallback = object : OnBackPressedCallback(enabled = true) {
            var startTime: Long = 0
            var lastX: Float = 0f
            var lastY: Float = 0f
            override fun handleOnBackPressed() {
                if (binding.mangaCoverFull.isClickable) {
                    val motionEvent = MotionEvent.obtain(startTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, lastX, lastY, 0)
                    velocityTracker.addMovement(motionEvent)
                    motionEvent.recycle()
                    animateBack()
                }
            }

            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                super.handleOnBackStarted(backEvent)
                startTime = SystemClock.uptimeMillis()
                velocityTracker.clear()
                val motionEvent = MotionEvent.obtain(startTime, startTime, MotionEvent.ACTION_DOWN, backEvent.touchX, backEvent.touchY, 0)
                velocityTracker.addMovement(motionEvent)
                motionEvent.recycle()
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                val maxProgress = min(backEvent.progress, 0.4f)
                val motionEvent = MotionEvent.obtain(startTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, backEvent.touchX, backEvent.touchY, 0)
                lastX = backEvent.touchX
                lastY = backEvent.touchY
                velocityTracker.addMovement(motionEvent)
                motionEvent.recycle()
                binding.mangaCoverZoom.scaleX = 1f - maxProgress * 0.6f
                binding.mangaCoverZoom.translationX =
                    maxProgress * 100f * (if (backEvent.swipeEdge == BackEventCompat.EDGE_LEFT) 1 else -1)
                binding.mangaCoverZoom.translationY = -maxProgress * 150f
                binding.mangaCoverZoom.scaleY = 1f - maxProgress * 0.6f
            }

            override fun handleOnBackCancelled() {
                binding.mangaCoverZoom.scaleX = 1f
                binding.mangaCoverZoom.translationX = 0f
                binding.mangaCoverZoom.translationY = 0f
                binding.mangaCoverZoom.scaleY = 1f
            }
        }
        onBackPressedDispatcher.addCallback(backPressedCallback)

        listOf(binding.touchOutside, binding.mangaCoverFull, binding.mangaCoverZoom).forEach {
            it.setOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }
        }

        binding.btnSave.setOnClickListener {
            controller.saveCover()
        }
        binding.btnShare.setOnClickListener {
            controller.shareCover()
        }

        val expandedImageView = binding.mangaCoverFull
        expandedImageView.shapeAppearanceModel =
            expandedImageView.shapeAppearanceModel.toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, ratio)
                .build()

        expandedImageView.setImageDrawable(drawable)
        binding.mangaCoverZoom.setImageDrawable(drawable)

        val rect = Rect()
        thumbView.getGlobalVisibleRect(rect)
        val systemInsets = activity?.window?.decorView?.rootWindowInsetsCompat?.getInsets(systemBars())
        val topInset = systemInsets?.top ?: 0
        val leftInset = systemInsets?.left ?: 0
        val rightInset = systemInsets?.right ?: 0
        expandedImageView.updateLayoutParams<LayoutParams> {
            height = thumbView.height
            width = thumbView.width
            topMargin = rect.top - topInset
            leftMargin = rect.left - leftInset
            rightMargin = rect.right - rightInset
            bottomMargin = rect.bottom
            horizontalBias = 0.0f
            verticalBias = 0.0f
        }
        expandedImageView.requestLayout()
        binding.btnShare.alpha = 0f
        binding.btnSave.alpha = 0f

        expandedImageView.post {
            // Hide the thumbnail and show the zoomed-in view. When the animation
            // begins, it will position the zoomed-in view in the place of the
            // thumbnail.
            thumbView.alpha = 0f
            val defMargin = 8.dpToPx
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                activity?.window?.decorView?.animateBlur(1f, 20f, 50)?.start()
            }
            listOf(expandedImageView, binding.mangaCoverZoom).forEach {
                it.updateLayoutParams<LayoutParams> {
                    height = 0
                    width = 0
                    topMargin = defMargin + 48.dpToPx
                    marginStart = defMargin
                    marginEnd = defMargin
                    bottomMargin = defMargin
                    horizontalBias = 0.5f
                    verticalBias = 0.5f
                }
            }

            // TransitionSet for the full cover because using animation for this SUCKS
            val transitionSet = TransitionSet()
            val bound = ChangeBounds()
            transitionSet.addTransition(bound)
            val changeImageTransform = ChangeImageTransform()
            transitionSet.addTransition(changeImageTransform)
            transitionSet.duration = shortAnimationDuration
            TransitionManager.beginDelayedTransition(binding.root, transitionSet)

            AnimatorSet().apply {
                val radiusAnimator = ValueAnimator.ofFloat(ratio, fullRatio).apply {
                    addUpdateListener {
                        val value = it.animatedValue as Float
                        expandedImageView.shapeAppearanceModel =
                            expandedImageView.shapeAppearanceModel.toBuilder()
                                .setAllCorners(CornerFamily.ROUNDED, value)
                                .build()
                    }
                    duration = shortAnimationDuration
                }
                val saveAnimator = ValueAnimator.ofFloat(binding.btnShare.alpha, 1f).apply {
                    addUpdateListener {
                        binding.btnShare.alpha = it.animatedValue as Float
                        binding.btnSave.alpha = it.animatedValue as Float
                    }
                }
                playTogether(radiusAnimator, saveAnimator)
                doOnEnd {
                    if (binding.touchOutside.isClickable) {
                        binding.mangaCoverFull.isInvisible = true
                        binding.mangaCoverZoom.isVisible = true
                    }
                }
                duration = shortAnimationDuration
                interpolator = DecelerateInterpolator()
                start()
            }
        }

        window?.let { window ->
            window.navigationBarColor = Color.TRANSPARENT
            window.decorView.fitsSystemWindows = true
            val wic = WindowInsetsControllerCompat(window, window.decorView)
            wic.isAppearanceLightStatusBars = false
            wic.isAppearanceLightNavigationBars = false
        }
    }

    override fun cancel() {
        animateBack()
    }

    override fun dismiss() {
        super.dismiss()
        thumbView.alpha = 1f
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try {
            context.unregisterReceiver(powerSaverChangeReceiver)
        } catch (_: Exception) { }
    }

    private fun animateBack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                context.unregisterReceiver(powerSaverChangeReceiver)
            } catch (_: Exception) { }
        }
        val rect2 = Rect()
        thumbView.getGlobalVisibleRect(rect2)
        binding.mangaCoverFull.scaleX = binding.mangaCoverZoom.scaleX
        binding.mangaCoverFull.translationX = binding.mangaCoverZoom.translationX
        binding.mangaCoverFull.translationY = binding.mangaCoverZoom.translationY
        binding.mangaCoverFull.scaleY = binding.mangaCoverZoom.scaleY
        binding.mangaCoverFull.isVisible = true
        binding.mangaCoverZoom.isVisible = false
        binding.mangaCoverZoom.isClickable = false
        binding.mangaCoverFull.isClickable = false
        binding.touchOutside.isClickable = false
        val expandedImageView = binding.mangaCoverFull
        val systemInsets = activity?.window?.decorView?.rootWindowInsetsCompat?.getInsets(systemBars())
        val topInset = systemInsets?.top ?: 0
        val leftInset = systemInsets?.left ?: 0
        val rightInset = systemInsets?.right ?: 0
        expandedImageView.updateLayoutParams<LayoutParams> {
            height = thumbView.height
            width = thumbView.width
            topMargin = rect2.top - topInset
            leftMargin = rect2.left - leftInset
            rightMargin = rect2.right - rightInset
            bottomMargin = rect2.bottom
            horizontalBias = 0.0f
            verticalBias = 0.0f
        }

        // Zoom out back to tc thumbnail
        val transitionSet = TransitionSet()
        transitionSet.addTransition(ChangeBounds())
        transitionSet.addTransition(ChangeImageTransform())
        transitionSet.duration = shortAnimationDuration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            velocityTracker.computeCurrentVelocity(1, 5f)
            val velo =
                max(0.5f, abs(velocityTracker.getAxisVelocity(MotionEvent.AXIS_X)) * 0.5f)
            transitionSet.interpolator = DecelerateInterpolator(velo)
        }
        TransitionManager.beginDelayedTransition(binding.root, transitionSet)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity?.window?.decorView?.animateBlur(20f, 0.1f, 50, true)?.apply {
                startDelay = shortAnimationDuration - 100
            }?.start()
        }
        val attrs = window?.attributes
        val ogDim = attrs?.dimAmount ?: 0.25f
        velocityTracker.recycle()

        // AnimationSet for backdrop because idk how to use TransitionSet
        AnimatorSet().apply {
            val radiusAnimator = ValueAnimator.ofFloat(fullRatio, ratio).apply {
                addUpdateListener {
                    val value = it.animatedValue as Float
                    expandedImageView.shapeAppearanceModel =
                        expandedImageView.shapeAppearanceModel.toBuilder()
                            .setAllCorners(CornerFamily.ROUNDED, value)
                            .build()
                }
            }
            val dimAnimator = ValueAnimator.ofFloat(ogDim, 0f).apply {
                addUpdateListener {
                    window?.setDimAmount(it.animatedValue as Float)
                }
            }

            val saveAnimator = ValueAnimator.ofFloat(binding.btnShare.alpha, 0f).apply {
                addUpdateListener {
                    binding.btnShare.alpha = it.animatedValue as Float
                    binding.btnSave.alpha = it.animatedValue as Float
                }
            }
            playTogether(radiusAnimator, dimAnimator, saveAnimator)

            play(ObjectAnimator.ofFloat(expandedImageView, View.SCALE_X, 1f))
            play(ObjectAnimator.ofFloat(expandedImageView, View.SCALE_Y, 1f))
            play(ObjectAnimator.ofFloat(expandedImageView, View.TRANSLATION_X, 0f))
            play(ObjectAnimator.ofFloat(expandedImageView, View.TRANSLATION_Y, 0f))

            addListener(
                onEnd = {
                    TransitionManager.endTransitions(binding.root)
                    thumbView.alpha = 1f
                    expandedImageView.post {
                        dismiss()
                    }
                },
                onCancel = {
                    TransitionManager.endTransitions(binding.root)
                    thumbView.alpha = 1f
                    expandedImageView.post {
                        dismiss()
                    }
                },
            )
            interpolator = DecelerateInterpolator()
            duration = shortAnimationDuration
        }.start()
    }
}
