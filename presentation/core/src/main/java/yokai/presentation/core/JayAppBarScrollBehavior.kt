package yokai.presentation.core

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.annotation.FrequentlyChangingValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.util.fastCoerceIn
import kotlin.math.abs
import kotlin.math.roundToInt

interface JayAppBarScrollBehavior {
    /**
     * The app bar's current offset due to scrolling, in pixels. This offset is applied to the
     * fixed size of the search bar to control the displayed size when content is being scrolled.
     *
     * This value is typically negative.
     *
     * Updates to the [scrollOffset] value are coerced between [scrollOffsetLimit] and 0.
     */
    @get:FrequentlyChangingValue var scrollOffset: Float

    /**
     * The limit that an app bar can be offset due to scrolling, in pixels.
     *
     * This value is typically negative.
     *
     * Use this limit to coerce the [scrollOffset] value when it's updated.
     */
    var scrollOffsetLimit: Float

    /**
     * The total offset of the content scrolled under the app bar.
     *
     * The content offset is used to compute the [overlappedFraction], which can later be read by an
     * implementation.
     *
     * This value is updated by a [JayAppBarScrollBehavior] whenever a nested scroll connection
     * consumes scroll events. A common implementation would update the value to be the sum of all
     * [NestedScrollConnection.onPostScroll] `consumed.y` values.
     */
    @get:FrequentlyChangingValue var contentOffset: Float

    /**
     * A [NestedScrollConnection] that should be attached to a [Modifier.nestedScroll] in order to
     * keep track of scroll events.
     */
    val nestedScrollConnection: NestedScrollConnection

    /**
     * The modifier that adds scrolling behavior to the app bar component.
     */
    fun Modifier.appBarScrollBehavior(): Modifier

    // Bunch of workarounds to make scroll behavior to work properly with LargeTopAppBar
    var topHeightPx: Float
        get() = 0f
        set(value) { throw NotImplementedError() }
    var bottomHeightPx: Float
        get() = 0f
        set(value) { throw NotImplementedError() }
    var searchHeightPx: Float
        get() = 0f
        set(value) { throw NotImplementedError() }
    val totalHeightPx: Float
        get() = topHeightPx + bottomHeightPx + searchHeightPx

    fun Modifier.smallAppBarScrollBehavior(): Modifier = appBarScrollBehavior()
    fun Modifier.largeAppBarScrollBehavior(): Modifier = appBarScrollBehavior()
    fun Modifier.searchAppBarScrollBehavior(): Modifier = appBarScrollBehavior()
}

/**
 * Probably not the best way to do it, but fuck it anyway, it's a remnant code from when I try to make the custom AppBar
 * to work with Google Material's [androidx.compose.material3.TopAppBarScrollBehavior] but their LargeTopAppBar was too
 * laggy, turns out they have a separate TopAppBar called [androidx.compose.material3.AppBarWithSearch] that more
 * optimized by offsetting [androidx.compose.material3.Surface] position instead of resizing the container and
 * offsetting the entire children elements. And I'm too lazy to redo the whole thing :P
 *
 * Why don't they use similar implementation to [androidx.compose.material3.AppBarWithSearch] for
 * [androidx.compose.material3.LargeTopAppBar] themselves???
 * Yeah, I have no idea since it works just fine here. Just thinking about it pisses me off.
 *
 * This works by splitting the [scrollOffset] into 3 and calculating offset for each row of top app bar from there.
 *
 * Imaginary values:
 * - (Total) Offset Limit = -100f
 * - Search app bar height: 20px
 * - Top app bar height: 40px (20px if Search app bar exists)
 * - Bottom app bar height: 60px
 * - Total app bar height: 100px
 * - Top offset limit range: (-Total, -Bottom) = (-100f, -60f) -> Clamped to relative (-40f, 0f) -> Halved if Search app bar exists
 * - Search offset limit: Half of top offset (-20f, 0f)
 * - Bottom offset limit range: (-Bottom, 0f) = (-60f, 0f)
 *
 * ┌────────────────────┐  -> -100f
 * │                    │  -> Search (Relative Offset Limit: -20f)
 * ├────────────────────┤
 * │                    │  -> Top (Relative Offset Limit: -20f)
 * ├────────────────────┤
 * │                    │  -> Bottom (Relative Offset Limit: -60f)
 * │                    │
 * │                    │
 * └────────────────────┘  -> 0f
 */
internal val JayAppBarScrollBehavior.rawTopScrollOffset: Float
    get() = scrollOffset + bottomHeightPx

internal fun JayAppBarScrollBehavior.overallTopScrollOffset(): Float
    = rawTopScrollOffset.fastCoerceIn(-(topHeightPx + searchHeightPx), 0f)

internal fun JayAppBarScrollBehavior.searchScrollOffset(): Float
    = (rawTopScrollOffset + topHeightPx).fastCoerceIn(-searchHeightPx, 0f)

internal fun JayAppBarScrollBehavior.topScrollOffset(): Float
    = rawTopScrollOffset.fastCoerceIn(-topHeightPx, 0f)

internal fun JayAppBarScrollBehavior.bottomScrollOffset(): Float
    = scrollOffset.fastCoerceIn(-bottomHeightPx, 0f)

internal fun JayAppBarScrollBehavior.searchCollapsedFraction(): Float
    = searchScrollOffset() / -searchHeightPx

internal fun JayAppBarScrollBehavior.topCollapsedFraction(): Float
    = topScrollOffset() / -topHeightPx

internal fun JayAppBarScrollBehavior.bottomCollapsedFraction(offset: Float = 0f): Float
    = bottomScrollOffset() / (-bottomHeightPx + offset)

internal fun JayAppBarScrollBehavior.overlappedFraction(): Float =
    if (scrollOffsetLimit != 0f) {
        1 -
            ((scrollOffsetLimit - contentOffset).coerceIn(
                minimumValue = scrollOffsetLimit,
                maximumValue = 0f,
            ) / scrollOffsetLimit)
    } else {
        0f
    }

val JayAppBarScrollBehavior.collapsedFraction: Float
    get() =
        if (scrollOffsetLimit != 0f) {
            scrollOffset / scrollOffsetLimit
        } else {
            0f
        }

interface SettlingAppBarScrollBehavior : JayAppBarScrollBehavior {
    val snapAnimationSpec: AnimationSpec<Float>
    val flingAnimationSpec: DecayAnimationSpec<Float>

    val isTopAndTotalPxValid: Boolean
        get() = false

    suspend fun settleAppBar(
        velocity: Float,
        flingAnimationSpec: DecayAnimationSpec<Float>?,
        snapAnimationSpec: AnimationSpec<Float>?,
    ): Velocity {
        // Check if the app bar is completely collapsed/expanded. If so, no need to settle the app bar,
        // and just return Zero Velocity.
        // Note that we don't check for 0f due to float precision with the collapsedFraction
        // calculation.
        val collapsedFraction by lazy {
            when {
                !isTopAndTotalPxValid -> collapsedFraction
                searchHeightPx > 0f -> searchCollapsedFraction()
                else -> topCollapsedFraction()
            }
        }
        if (collapsedFraction < 0.01f || collapsedFraction == 1f) {
            return Velocity.Zero
        }
        var remainingVelocity = velocity
        // In case there is an initial velocity that was left after a previous user fling, animate to
        // continue the motion to expand or collapse the app bar.
        if (flingAnimationSpec != null && abs(velocity) > 1f) {
            var lastValue = 0f
            AnimationState(
                initialValue = 0f,
                initialVelocity = velocity,
            )
                .animateDecay(flingAnimationSpec) {
                    val delta = value - lastValue
                    val initialScrollOffset = scrollOffset
                    scrollOffset = initialScrollOffset + delta
                    val consumed = abs(initialScrollOffset - scrollOffset)
                    lastValue = value
                    remainingVelocity = this.velocity
                    // avoid rounding errors and stop if anything is unconsumed
                    if (abs(delta - consumed) > 0.5f) this.cancelAnimation()
                }
        }
        val actualScrollOffsetLimit by lazy {
            when {
                !isTopAndTotalPxValid -> scrollOffsetLimit
                searchHeightPx > 0f -> -searchHeightPx
                else -> -topHeightPx
            }
        }
        val actualScrollOffset by lazy {
            when {
                !isTopAndTotalPxValid -> scrollOffset
                searchHeightPx > 0f -> searchScrollOffset()
                else -> topScrollOffset()
            }
        }
        // Snap if animation specs were provided.
        if (snapAnimationSpec != null) {
            if (actualScrollOffset < 0 && actualScrollOffset > actualScrollOffsetLimit) {
                AnimationState(initialValue = actualScrollOffset).animateTo(
                    if (collapsedFraction < 0.5f) {
                        0f
                    } else {
                        actualScrollOffsetLimit
                    },
                    animationSpec = snapAnimationSpec
                ) {
                    scrollOffset = value - when {
                        !isTopAndTotalPxValid -> abs(scrollOffsetLimit)
                        searchHeightPx > 0f -> bottomHeightPx + topHeightPx
                        else -> bottomHeightPx
                    }
                }
            }
        }

        return Velocity(0f, remainingVelocity)
    }
}

/**
 * Mimic [com.google.android.material.appbar.AppBarLayout]'s enterAlwaysCollapsed behavior because Google didn't
 * implement it for their LargeTopAppBar, if [topHeightPx] and [totalHeightPx] is invalid it'll mimic exitUntilCollapsed instead.
 */
private class EnterAlwaysCollapsedAppBarScrollBehavior(
    initialOffset: Float,
    initialOffsetLimit: Float,
    initialContentOffset: Float,
    override val snapAnimationSpec: AnimationSpec<Float>,
    override val flingAnimationSpec: DecayAnimationSpec<Float>,
    val canScroll: () -> Boolean = { true },
    val isAtTop: () -> Boolean = { true },
    initialTopHeightPx: Float = 0f,
    initialBottomHeightPx: Float = 0f,
    initialSearchHeightPx: Float = 0f,
) : SettlingAppBarScrollBehavior {

    private var _topHeightPx by mutableFloatStateOf(initialTopHeightPx)
    private var _bottomHeightPx by mutableFloatStateOf(initialBottomHeightPx)
    private var _searchHeightPx by mutableFloatStateOf(initialSearchHeightPx)
    private var _scrollOffset by mutableFloatStateOf(initialOffset)
    private var _scrollOffsetLimit by mutableFloatStateOf(initialOffsetLimit)
    private var _contentOffset by mutableFloatStateOf(initialContentOffset)

    override var topHeightPx: Float
        get() = _topHeightPx
        set(value) {
            _topHeightPx = value
            _scrollOffsetLimit = -totalHeightPx
        }

    override var bottomHeightPx: Float
        get() = _bottomHeightPx
        set(value) {
            _bottomHeightPx = value
            _scrollOffsetLimit = -totalHeightPx
        }

    override var searchHeightPx: Float
        get() = _searchHeightPx
        set(value) {
            _searchHeightPx = value
            _scrollOffsetLimit = -totalHeightPx
        }

    /**
     * Use [scrollOffsetLimit] instead of [topHeightPx] or [bottomHeightPx] if one of them is 0f!
     */
    override val isTopAndTotalPxValid
        get() = topHeightPx > 0f && bottomHeightPx > 0f

    override var scrollOffset: Float
        @FrequentlyChangingValue get() = _scrollOffset
        set(newOffset) {
            _scrollOffset = if (isAtTop() || !isTopAndTotalPxValid) {
                newOffset.fastCoerceIn(scrollOffsetLimit, 0f)
            } else {
                newOffset.fastCoerceIn(-totalHeightPx, -bottomHeightPx)
            }
        }

    override var scrollOffsetLimit: Float
        get() = _scrollOffsetLimit
        set(newOffset) {
            _scrollOffsetLimit = newOffset
        }

    override var contentOffset: Float
        @FrequentlyChangingValue get() = _contentOffset
        set(newOffset) {
            _contentOffset = newOffset
        }

    private fun Modifier.internalScrollBehavior(scrollOffset: () -> Float): Modifier {
        return this
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val scrollOffset = scrollOffset().roundToInt()
                val scrolledHeight = (placeable.height + scrollOffset).coerceAtLeast(0)
                layout(placeable.width, scrolledHeight) {
                    placeable.placeWithLayer(0, scrollOffset)
                }
            }

    }

    override fun Modifier.appBarScrollBehavior(): Modifier {
        return this
            .draggable(
                orientation = Orientation.Vertical,
                state = DraggableState { delta -> scrollOffset += delta },
                onDragStopped = { velocity ->
                    settleAppBar(velocity, flingAnimationSpec, snapAnimationSpec)
                },
                enabled = canScroll(),
            )
            .internalScrollBehavior { scrollOffset }
    }

    override fun Modifier.smallAppBarScrollBehavior(): Modifier {
        return this.internalScrollBehavior { topScrollOffset() }
    }

    override fun Modifier.largeAppBarScrollBehavior(): Modifier {
        return this.internalScrollBehavior { bottomScrollOffset() }
    }

    override fun Modifier.searchAppBarScrollBehavior(): Modifier {
        return this.internalScrollBehavior { searchScrollOffset() }
    }

    override var nestedScrollConnection =
        object : NestedScrollConnection {

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Don't intercept if scrolling down.
                val scrollCheck = {
                    available.y > 0f &&
                        when {
                            !isTopAndTotalPxValid -> true
                            searchHeightPx > 0f -> rawTopScrollOffset + searchHeightPx >= 0f
                            else -> rawTopScrollOffset >= 0f
                        }
                }
                if (!canScroll() || scrollCheck())
                    return Offset.Zero

                val prevHeightOffset = scrollOffset
                scrollOffset += available.y
                return if (prevHeightOffset != scrollOffset) {
                    // We're in the middle of top app bar collapse or expand.
                    // Consume only the scroll on the Y axis.
                    available.copy(x = 0f)
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!canScroll()) return Offset.Zero
                contentOffset += consumed.y

                if (available.y < 0f || consumed.y < 0f) {
                    // When scrolling up, just update the state's height offset.
                    val oldScrollOffset = scrollOffset
                    scrollOffset += consumed.y
                    return Offset(0f, scrollOffset - oldScrollOffset)
                }

                if (consumed.y == 0f && available.y > 0) {
                    // Reset the total content offset to zero when scrolling all the way down. This
                    // will eliminate some float precision inaccuracies.
                    contentOffset = 0f
                }

                if (available.y > 0f) {
                    // Adjust the height offset in case the consumed delta Y is less than what was
                    // recorded as available delta Y in the pre-scroll.
                    val oldScrollOffset = scrollOffset
                    scrollOffset += available.y
                    return Offset(0f, scrollOffset - oldScrollOffset)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val superConsumed = super.onPostFling(consumed, available)
                return superConsumed +
                    settleAppBar(available.y, flingAnimationSpec, snapAnimationSpec)
            }
        }


    companion object {
        fun Saver(
            canScroll: () -> Boolean,
            isAtTop: () -> Boolean,
            snapAnimationSpec: AnimationSpec<Float>,
            flingAnimationSpec: DecayAnimationSpec<Float>,
        ): Saver<EnterAlwaysCollapsedAppBarScrollBehavior, *> =
            listSaver(
                save = {
                    listOf(
                        it.scrollOffset,
                        it.scrollOffsetLimit,
                        it.contentOffset,
                        it.topHeightPx,
                        it.bottomHeightPx,
                        it.searchHeightPx,
                    )
                },
                restore = {
                    EnterAlwaysCollapsedAppBarScrollBehavior(
                        initialOffset = it[0],
                        initialOffsetLimit = it[1],
                        initialContentOffset = it[2],
                        canScroll = canScroll,
                        isAtTop = isAtTop,
                        initialTopHeightPx = it[3],
                        initialBottomHeightPx = it[4],
                        initialSearchHeightPx = it[5],
                        snapAnimationSpec = snapAnimationSpec,
                        flingAnimationSpec = flingAnimationSpec,
                    )
                },
            )
    }
}
private class EnterAlwaysAppBarScrollBehavior(
    initialOffset: Float,
    initialOffsetLimit: Float,
    initialContentOffset: Float,
    override val snapAnimationSpec: AnimationSpec<Float>,
    override val flingAnimationSpec: DecayAnimationSpec<Float>,
    val canScroll: () -> Boolean = { true },
) : SettlingAppBarScrollBehavior {

    private var _scrollOffset by mutableFloatStateOf(initialOffset)
    private var _scrollOffsetLimit by mutableFloatStateOf(initialOffsetLimit)
    private var _contentOffset by mutableFloatStateOf(initialContentOffset)

    override var scrollOffset: Float
        @FrequentlyChangingValue get() = _scrollOffset
        set(newOffset) {
            _scrollOffset = newOffset.fastCoerceIn(scrollOffsetLimit, 0f)
        }

    override var scrollOffsetLimit: Float
        get() = _scrollOffsetLimit
        set(newOffset) {
            _scrollOffsetLimit = newOffset
        }

    override var contentOffset: Float
        @FrequentlyChangingValue get() = _contentOffset
        set(newOffset) {
            _contentOffset = newOffset
        }

    override fun Modifier.appBarScrollBehavior(): Modifier {
        return this
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val actualScrollOffset = scrollOffset.roundToInt()
                val scrolledHeight = (placeable.height + actualScrollOffset).coerceAtLeast(0)
                layout(placeable.width, scrolledHeight) {
                    placeable.placeWithLayer(0, actualScrollOffset)
                }
            }
    }

    override var nestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!canScroll()) return Offset.Zero
                val prevScrollOffset = scrollOffset
                scrollOffset += available.y
                // The state's heightOffset is coerce in a minimum value of heightOffsetLimit and a
                // maximum value 0f, so we check if its value was actually changed after the
                // available.y was added to it in order to tell if the top app bar is currently
                // collapsing or expanding.
                // Note that when the content was set with a revered layout, we always return a
                // zero offset.
                return if (prevScrollOffset != scrollOffset) {
                    // We're in the middle of top app bar collapse or expand.
                    // Consume only the scroll on the Y axis.
                    available.copy(x = 0f)
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!canScroll()) return Offset.Zero
                contentOffset += consumed.y
                scrollOffset += consumed.y
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (
                    available.y > 0f &&
                    (scrollOffset == 0f || scrollOffset == scrollOffsetLimit)
                ) {
                    // Reset the total content offset to zero when scrolling all the way down.
                    // This will eliminate some float precision inaccuracies.
                    contentOffset = 0f
                }
                val superConsumed = super.onPostFling(consumed, available)
                return superConsumed +
                    settleAppBar(available.y, flingAnimationSpec, snapAnimationSpec)
            }
        }

    companion object {
        fun Saver(
            canScroll: () -> Boolean,
            snapAnimationSpec: AnimationSpec<Float>,
            flingAnimationSpec: DecayAnimationSpec<Float>,
        ): Saver<EnterAlwaysAppBarScrollBehavior, *> =
            listSaver(
                save = {
                    listOf(
                        it.scrollOffset,
                        it.scrollOffsetLimit,
                        it.contentOffset,
                    )
                },
                restore = {
                    EnterAlwaysAppBarScrollBehavior(
                        initialOffset = it[0],
                        initialOffsetLimit = it[1],
                        initialContentOffset = it[2],
                        canScroll = canScroll,
                        snapAnimationSpec = snapAnimationSpec,
                        flingAnimationSpec = flingAnimationSpec,
                    )
                },
            )
    }
}

private class PinnedAppBarScrollBehavior(
    initialContentOffset: Float,
    val canScroll: () -> Boolean = { true },
) : JayAppBarScrollBehavior {
    private var _contentOffset by mutableFloatStateOf(initialContentOffset)

    override var scrollOffset: Float = 0.0f
    override var scrollOffsetLimit: Float = 0.0f

    override var contentOffset: Float
        @FrequentlyChangingValue get() = _contentOffset
        set(newOffset) {
            _contentOffset = newOffset
        }

    override fun Modifier.appBarScrollBehavior(): Modifier {
        return this.clipToBounds()
    }

    override var nestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!canScroll()) return Offset.Zero
                contentOffset += consumed.y
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y > 0f) {
                    // Reset the total content offset to zero when scrolling all the way down.
                    // This will eliminate some float precision inaccuracies.
                    contentOffset = 0f
                }
                return super.onPostFling(consumed, available)
            }
        }
}

@Composable
fun enterAlwaysCollapsedAppBarScrollBehavior(
    initialOffset: Float = 0f,
    initialOffsetLimit: Float = -Float.MAX_VALUE,
    initialContentOffset: Float = 0f,
    canScroll: () -> Boolean = { true },
    isAtTop: () -> Boolean = { true },
    topHeightPx: Float = 0f,
    bottomHeightPx: Float = 0f,
    searchHeightPx: Float = 0f,
    // TODO Load the motionScheme tokens from the component tokens file
    snapAnimationSpec: AnimationSpec<Float> = spring(stiffness = Spring.StiffnessMediumLow),
    flingAnimationSpec: DecayAnimationSpec<Float> = rememberSplineBasedDecay(),
): JayAppBarScrollBehavior =
    rememberSaveable(
        snapAnimationSpec,
        flingAnimationSpec,
        canScroll,
        isAtTop,
        saver =
            EnterAlwaysCollapsedAppBarScrollBehavior.Saver(
                canScroll = canScroll,
                isAtTop = isAtTop,
                snapAnimationSpec = snapAnimationSpec,
                flingAnimationSpec = flingAnimationSpec,
            )
    ) {
        EnterAlwaysCollapsedAppBarScrollBehavior(
            initialOffset = initialOffset,
            initialOffsetLimit = initialOffsetLimit,
            initialContentOffset = initialContentOffset,
            canScroll = canScroll,
            isAtTop = isAtTop,
            initialTopHeightPx = topHeightPx,
            initialBottomHeightPx = bottomHeightPx,
            initialSearchHeightPx = searchHeightPx,
            snapAnimationSpec = snapAnimationSpec,
            flingAnimationSpec = flingAnimationSpec,
        )
    }

@Composable
fun enterAlwaysAppBarScrollBehavior(
    initialOffset: Float = 0f,
    initialOffsetLimit: Float = -Float.MAX_VALUE,
    initialContentOffset: Float = 0f,
    canScroll: () -> Boolean = { true },
    // TODO Load the motionScheme tokens from the component tokens file
    snapAnimationSpec: AnimationSpec<Float> = spring(stiffness = Spring.StiffnessMediumLow),
    flingAnimationSpec: DecayAnimationSpec<Float> = rememberSplineBasedDecay(),
): JayAppBarScrollBehavior =
    rememberSaveable(
        snapAnimationSpec,
        flingAnimationSpec,
        canScroll,
        saver =
            EnterAlwaysAppBarScrollBehavior.Saver(
                canScroll = canScroll,
                snapAnimationSpec = snapAnimationSpec,
                flingAnimationSpec = flingAnimationSpec,
            )
    ) {
        EnterAlwaysAppBarScrollBehavior(
            initialOffset = initialOffset,
            initialOffsetLimit = initialOffsetLimit,
            initialContentOffset = initialContentOffset,
            canScroll = canScroll,
            snapAnimationSpec = snapAnimationSpec,
            flingAnimationSpec = flingAnimationSpec,
        )
    }

@Composable
fun pinnedAppBarScrollBehavior(
    initialContentOffset: Float = 0f,
    canScroll: () -> Boolean = { true },
): JayAppBarScrollBehavior =
    remember(canScroll) {
        PinnedAppBarScrollBehavior(
            canScroll = canScroll,
            initialContentOffset = initialContentOffset,
        )
    }
