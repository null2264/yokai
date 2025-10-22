package yokai.presentation.core

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirst
import androidx.compose.ui.util.fastMaxOfOrNull
import androidx.compose.ui.util.fastSumBy
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Composable replacement for [eu.kanade.tachiyomi.ui.base.ExpandedAppBarLayout]
 *
 * Copied from [androidx.compose.material3.LargeTopAppBar], modified to mimic J2K's
 * [eu.kanade.tachiyomi.ui.base.ExpandedAppBarLayout] behaviors
 */
@Composable
fun ExpandedAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.largeTopAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    TwoRowsTopAppBar(
        title = title,
        titleTextStyle = MaterialTheme.typography.headlineMedium,
        smallTitleTextStyle = MaterialTheme.typography.titleLarge,
        titleBottomPadding = LargeTitleBottomPadding,
        smallTitle = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        collapsedHeight = CollapsedContainerHeight,
        expandedHeight = ExpandedContainerHeight,
        windowInsets = windowInsets,
        colors = colors,
        scrollBehavior = scrollBehavior,
        subtitle = null,
        subtitleTextStyle = TextStyle.Default,
        smallSubtitle = null,
        smallSubtitleTextStyle = TextStyle.Default,
    )
}

@Composable
private fun TwoRowsTopAppBar(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    titleTextStyle: TextStyle,
    titleBottomPadding: Dp,
    smallTitle: @Composable () -> Unit,
    smallTitleTextStyle: TextStyle,
    subtitle: (@Composable () -> Unit)?,
    subtitleTextStyle: TextStyle,
    smallSubtitle: (@Composable () -> Unit)?,
    smallSubtitleTextStyle: TextStyle,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    collapsedHeight: Dp,
    expandedHeight: Dp,
    windowInsets: WindowInsets,
    colors: TopAppBarColors,
    scrollBehavior: TopAppBarScrollBehavior?
) {
    require(collapsedHeight.isSpecified && collapsedHeight.isFinite) {
        "The collapsedHeight is expected to be specified and finite"
    }
    require(expandedHeight.isSpecified && expandedHeight.isFinite) {
        "The expandedHeight is expected to be specified and finite"
    }
    require(expandedHeight >= collapsedHeight) {
        "The expandedHeight is expected to be greater or equal to the collapsedHeight"
    }
    val expandedHeightPx: Float
    val collapsedHeightPx: Float
    val titleBottomPaddingPx: Int
    LocalDensity.current.run {
        expandedHeightPx = expandedHeight.toPx()
        collapsedHeightPx = collapsedHeight.toPx()
        titleBottomPaddingPx = titleBottomPadding.roundToPx()
    }

    // Sets the app bar's height offset limit to hide just the bottom title area and keep top title
    // visible when collapsed.
    SideEffect {
        if (scrollBehavior?.state?.heightOffsetLimit != -expandedHeightPx) {
            scrollBehavior?.state?.heightOffsetLimit = -expandedHeightPx
        }
    }

    val state = scrollBehavior?.state

    // Overall collapse fraction, from 0.0 (expanded) to 1.0 (fully collapsed).
    // A lambda that computes the collapsed fraction. It is invoked within other lambdas to defer state reading.
    val collapsedFraction = {
        val offset = state?.heightOffset ?: 0f
        val limit = state?.heightOffsetLimit ?: -expandedHeightPx
        if (limit != 0f) (offset / limit) else 0f
    }

    // The fraction of the total collapse distance that corresponds to the bottom part collapsing.
    val bottomPartCollapseEndFraction = {
        if (expandedHeightPx > 0) {
            (expandedHeightPx - collapsedHeightPx) / expandedHeightPx
        } else {
            0f
        }
    }

    // The fraction of the way through the bottom part's collapse.
    val bottomPartCollapseFraction = {
        if (bottomPartCollapseEndFraction() > 0f) {
            (collapsedFraction() / bottomPartCollapseEndFraction()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    // The fraction of the way through the top part's collapse.
    val topPartCollapseFraction = {
        if (bottomPartCollapseEndFraction() < 1f) {
            ((collapsedFraction() - bottomPartCollapseEndFraction()) / (1f - bottomPartCollapseEndFraction())).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    // Obtain the container Color from the TopAppBarColors using the `collapsedFraction`, as the
    // bottom part of this TwoRowsTopAppBar changes color at the same rate the app bar expands or
    // collapse.
    // This will potentially animate or interpolate a transition between the container color and the
    // container's scrolled color according to the app bar's scroll state.

    val appBarContainerColor = {
        lerp(
            colors.containerColor,
            colors.scrolledContainerColor,
            FastOutLinearInEasing.transform(bottomPartCollapseFraction())
        )
    }

    // Wrap the given actions in a Row.
    val actionsRow =
        @Composable {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
    val topTitleAlpha = TitleAlphaEasing.transform(bottomPartCollapseFraction())
    val bottomTitleAlpha = 1f - bottomPartCollapseFraction()
    // Hide the top row title semantics when its alpha value goes below 0.5 threshold.
    // Hide the bottom row title semantics when the top title semantics are active.
    val hideTopRowSemantics = bottomPartCollapseFraction() < 0.5f
    val hideBottomRowSemantics = !hideTopRowSemantics

    val topRowOffset = {
        lerp(0f, -collapsedHeightPx, topPartCollapseFraction())
    }

    val bottomRowOffset = {
        lerp(0f, collapsedHeightPx - expandedHeightPx, bottomPartCollapseFraction())
    }

    Box(
        modifier =
            modifier
                .drawBehind { drawRect(color = appBarContainerColor()) }
                .semantics { isTraversalGroup = true }
                .pointerInput(Unit) {}
    ) {
        Column {
            AppBarLayout(
                modifier =
                    Modifier.windowInsetsPadding(windowInsets)
                        // clip after padding so we don't show the title over the inset area
                        .clipToBounds(),
                scrolledOffset = topRowOffset,
                navigationIconContentColor = colors.navigationIconContentColor,
                titleContentColor = colors.titleContentColor,
                //subtitleContentColor = colors.subtitleContentColor,
                subtitleContentColor = colors.titleContentColor,
                actionIconContentColor = colors.actionIconContentColor,
                title = smallTitle,
                titleTextStyle = smallTitleTextStyle,
                titleAlpha = { topTitleAlpha },
                titleVerticalArrangement = Arrangement.Bottom,
                titleHorizontalAlignment = Alignment.Start,
                titleBottomPadding = 0,
                subtitle = smallSubtitle,
                subtitleTextStyle = smallSubtitleTextStyle,
                hideTitleSemantics = hideTopRowSemantics,
                navigationIcon = navigationIcon,
                actions = actionsRow,
                height = collapsedHeight,
            )
            AppBarLayout(
                modifier =
                    Modifier
                        // only apply the horizontal sides of the window insets padding, since the
                        // top
                        // padding will always be applied by the layout above
                        .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Horizontal))
                        .clipToBounds(),
                scrolledOffset = bottomRowOffset,
                navigationIconContentColor = colors.navigationIconContentColor,
                titleContentColor = colors.titleContentColor,
                //subtitleContentColor = colors.subtitleContentColor,
                subtitleContentColor = colors.titleContentColor,
                actionIconContentColor = colors.actionIconContentColor,
                title = title,
                titleTextStyle = titleTextStyle,
                titleAlpha = { bottomTitleAlpha },
                titleVerticalArrangement = Arrangement.Bottom,
                titleHorizontalAlignment = Alignment.Start,
                titleBottomPadding = titleBottomPaddingPx,
                hideTitleSemantics = hideBottomRowSemantics,
                subtitle = subtitle,
                subtitleTextStyle = subtitleTextStyle,
                navigationIcon = {},
                actions = {},
                height = expandedHeight - collapsedHeight,
            )
        }
    }
}

/**
 * Alternative to `() -> Float` but avoids boxing.
 */
internal fun interface FloatProducer {
    /** Returns the Float. */
    operator fun invoke(): Float
}

@Composable
private fun AppBarLayout(
    modifier: Modifier,
    scrolledOffset: FloatProducer,
    navigationIconContentColor: Color,
    titleContentColor: Color,
    subtitleContentColor: Color,
    actionIconContentColor: Color,
    title: @Composable () -> Unit,
    titleTextStyle: TextStyle,
    subtitle: (@Composable () -> Unit)?,
    subtitleTextStyle: TextStyle,
    titleAlpha: () -> Float,
    titleVerticalArrangement: Arrangement.Vertical,
    titleHorizontalAlignment: Alignment.Horizontal,
    titleBottomPadding: Int,
    hideTitleSemantics: Boolean,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    height: Dp,
) {
    Layout(
        {
            Box(Modifier.layoutId("navigationIcon").padding(start = TopAppBarHorizontalPadding)) {
                CompositionLocalProvider(
                    LocalContentColor provides navigationIconContentColor,
                    content = navigationIcon
                )
            }
            if (subtitle != null) {
                Column(
                    modifier =
                        Modifier.layoutId("title")
                            .padding(horizontal = TopAppBarHorizontalPadding)
                            .then(
                                if (hideTitleSemantics) Modifier.clearAndSetSemantics {}
                                else Modifier
                            )
                            .graphicsLayer { alpha = titleAlpha() },
                    horizontalAlignment = titleHorizontalAlignment
                ) {
                    ProvideContentColorTextStyle(
                        contentColor = titleContentColor,
                        textStyle = titleTextStyle,
                        content = title
                    )
                    ProvideContentColorTextStyle(
                        contentColor = subtitleContentColor,
                        textStyle = subtitleTextStyle,
                        content = subtitle
                    )
                }
            } else { // TODO(b/352770398): Workaround to maintain compatibility
                Box(
                    modifier =
                        Modifier.layoutId("title")
                            .padding(horizontal = TopAppBarHorizontalPadding)
                            .then(
                                if (hideTitleSemantics) Modifier.clearAndSetSemantics {}
                                else Modifier
                            )
                            .graphicsLayer { alpha = titleAlpha() }
                ) {
                    ProvideContentColorTextStyle(
                        contentColor = titleContentColor,
                        textStyle = titleTextStyle,
                        content = title
                    )
                }
            }
            Box(Modifier.layoutId("actionIcons").padding(end = TopAppBarHorizontalPadding)) {
                CompositionLocalProvider(
                    LocalContentColor provides actionIconContentColor,
                    content = actions
                )
            }
        },
        modifier = modifier,
        measurePolicy =
            remember(
                scrolledOffset,
                titleVerticalArrangement,
                titleHorizontalAlignment,
                titleBottomPadding,
                height
            ) {
                TopAppBarMeasurePolicy(
                    scrolledOffset,
                    titleVerticalArrangement,
                    titleHorizontalAlignment,
                    titleBottomPadding,
                    height
                )
            }
    )
}

private class TopAppBarMeasurePolicy(
    val scrolledOffset: FloatProducer,
    val titleVerticalArrangement: Arrangement.Vertical,
    val titleHorizontalAlignment: Alignment.Horizontal,
    val titleBottomPadding: Int,
    val height: Dp,
) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints
    ): MeasureResult {
        val navigationIconPlaceable =
            measurables
                .fastFirst { it.layoutId == "navigationIcon" }
                .measure(constraints.copy(minWidth = 0))
        val actionIconsPlaceable =
            measurables
                .fastFirst { it.layoutId == "actionIcons" }
                .measure(constraints.copy(minWidth = 0))

        val maxTitleWidth =
            if (constraints.maxWidth == Constraints.Infinity) {
                constraints.maxWidth
            } else {
                (constraints.maxWidth - navigationIconPlaceable.width - actionIconsPlaceable.width)
                    .coerceAtLeast(0)
            }
        val titlePlaceable =
            measurables
                .fastFirst { it.layoutId == "title" }
                .measure(constraints.copy(minWidth = 0, maxWidth = maxTitleWidth))

        // Locate the title's baseline.
        val titleBaseline =
            if (titlePlaceable[LastBaseline] != AlignmentLine.Unspecified) {
                titlePlaceable[LastBaseline]
            } else {
                0
            }

        // Subtract the scrolledOffset from the maxHeight. The scrolledOffset is expected to be
        // equal or smaller than zero.
        val scrolledOffsetValue = scrolledOffset()
        val heightOffset = if (scrolledOffsetValue.isNaN()) 0 else scrolledOffsetValue.roundToInt()

        val maxLayoutHeight = max(height.roundToPx(), titlePlaceable.height)
        val layoutHeight =
            if (constraints.maxHeight == Constraints.Infinity) {
                maxLayoutHeight
            } else {
                (maxLayoutHeight + heightOffset).coerceAtLeast(0)
            }

        return placeTopAppBar(
            constraints,
            layoutHeight,
            maxLayoutHeight,
            navigationIconPlaceable,
            titlePlaceable,
            actionIconsPlaceable,
            titleBaseline
        )
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int
    ) = measurables.fastSumBy { it.minIntrinsicWidth(height) }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int
    ): Int {
        return max(
            height.roundToPx(),
            measurables.fastMaxOfOrNull { it.minIntrinsicHeight(width) } ?: 0
        )
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int
    ) = measurables.fastSumBy { it.maxIntrinsicWidth(height) }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int
    ): Int {
        return max(
            height.roundToPx(),
            measurables.fastMaxOfOrNull { it.maxIntrinsicHeight(width) } ?: 0
        )
    }

    private fun MeasureScope.placeTopAppBar(
        constraints: Constraints,
        layoutHeight: Int,
        maxLayoutHeight: Int,
        navigationIconPlaceable: Placeable,
        titlePlaceable: Placeable,
        actionIconsPlaceable: Placeable,
        titleBaseline: Int
    ): MeasureResult =
        layout(constraints.maxWidth, layoutHeight) {
            // Navigation icon
            navigationIconPlaceable.placeRelative(
                x = 0,
                y =
                    when (titleVerticalArrangement) {
                        Arrangement.Bottom -> {
                            val padding = (maxLayoutHeight - navigationIconPlaceable.height) / 2
                            val paddingFromBottom = padding - (navigationIconPlaceable.height - titleBaseline)
                            val heightWithPadding = paddingFromBottom + navigationIconPlaceable.height
                            val adjustedBottomPadding =
                                if (heightWithPadding > maxLayoutHeight) {
                                    paddingFromBottom - (heightWithPadding - maxLayoutHeight)
                                } else {
                                    paddingFromBottom
                                }

                            layoutHeight - navigationIconPlaceable.height - max(0, adjustedBottomPadding)
                        }
                        else -> (layoutHeight - navigationIconPlaceable.height) / 2
                    },
            )

            titlePlaceable.let {
                val start = max(TopAppBarTitleInset.roundToPx(), navigationIconPlaceable.width)
                val end = actionIconsPlaceable.width

                // Align using the maxWidth. We will adjust the position later according to the
                // start and end. This is done to ensure that a center alignment is still maintained
                // when the start and end have different widths. Note that the title is centered
                // relative to the entire app bar width, and not just centered between the
                // navigation icon and the actions.
                var titleX =
                    titleHorizontalAlignment.align(
                        size = titlePlaceable.width,
                        space = constraints.maxWidth,
                        // Using Ltr as we call placeRelative later on.
                        layoutDirection = LayoutDirection.Ltr
                    )
                // Reposition the title based on the start and the end (i.e. the navigation and
                // action widths).
                if (titleX < start) {
                    titleX += (start - titleX)
                } else if (titleX + titlePlaceable.width > constraints.maxWidth - end) {
                    titleX += ((constraints.maxWidth - end) - (titleX + titlePlaceable.width))
                }

                // The titleVerticalArrangement is always one of Center or Bottom.
                val titleY =
                    when (titleVerticalArrangement) {
                        Arrangement.Center -> (layoutHeight - titlePlaceable.height) / 2
                        // Apply bottom padding from the title's baseline only when the Arrangement
                        // is "Bottom".
                        Arrangement.Bottom -> {
                            // Calculate the actual padding from the bottom of the title, taking
                            // into account its baseline.
                            val paddingFromBottom = if (titleBottomPadding == 0) {
                                (maxLayoutHeight - titlePlaceable.height) / 2
                            } else {
                                titleBottomPadding
                            } - (titlePlaceable.height - titleBaseline)

                            // Adjust the bottom padding to a smaller number if there is no room
                            // to fit the title.
                            val heightWithPadding = paddingFromBottom + titlePlaceable.height
                            val adjustedBottomPadding =
                                if (heightWithPadding > maxLayoutHeight) {
                                    paddingFromBottom - (heightWithPadding - maxLayoutHeight)
                                } else {
                                    paddingFromBottom
                                }

                            layoutHeight - titlePlaceable.height - max(0, adjustedBottomPadding)
                        }
                        // Arrangement.Top
                        else -> 0
                    }

                it.placeRelative(titleX, titleY)
            }

            // Action icons
            actionIconsPlaceable.placeRelative(
                x = constraints.maxWidth - actionIconsPlaceable.width,
                y =
                    when (titleVerticalArrangement) {
                        Arrangement.Bottom -> {
                            val padding = (maxLayoutHeight - actionIconsPlaceable.height) / 2
                            val paddingFromBottom = padding - (actionIconsPlaceable.height - titleBaseline)
                            val heightWithPadding = paddingFromBottom + actionIconsPlaceable.height
                            val adjustedBottomPadding =
                                if (heightWithPadding > maxLayoutHeight) {
                                    paddingFromBottom -
                                        (heightWithPadding - maxLayoutHeight)
                                } else {
                                    paddingFromBottom
                                }

                            layoutHeight - actionIconsPlaceable.height - max(0, adjustedBottomPadding)
                        }
                        else -> (layoutHeight - actionIconsPlaceable.height) / 2
                    },
            )
        }
}

@Composable
internal fun ProvideContentColorTextStyle(
    contentColor: Color,
    textStyle: TextStyle,
    content: @Composable () -> Unit
) {
    val mergedStyle = LocalTextStyle.current.merge(textStyle)
    CompositionLocalProvider(
        LocalContentColor provides contentColor,
        LocalTextStyle provides mergedStyle,
        content = content
    )
}

private fun interface ScrolledOffset {
    fun offset(): Float
}

private suspend fun settleAppBar(
    state: TopAppBarState,
    velocity: Float,
    topHeightPx: Float,
    totalHeightPx: Float,
    flingAnimationSpec: DecayAnimationSpec<Float>?,
    snapAnimationSpec: AnimationSpec<Float>?,
): Velocity {
    // Check if the app bar is completely collapsed/expanded. If so, no need to settle the app bar,
    // and just return Zero Velocity.
    // Note that we don't check for 0f due to float precision with the collapsedFraction
    // calculation.
    if (state.topCollapsedFraction(topHeightPx, totalHeightPx) < 0.01f || state.topCollapsedFraction(topHeightPx, totalHeightPx) == 1f) {
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
                val initialHeightOffset = state.heightOffset
                state.heightOffset = initialHeightOffset + delta
                val consumed = abs(initialHeightOffset - state.heightOffset)
                lastValue = value
                remainingVelocity = this.velocity
                // avoid rounding errors and stop if anything is unconsumed
                if (abs(delta - consumed) > 0.5f) this.cancelAnimation()
            }
    }
    // Snap if animation specs were provided.
    if (snapAnimationSpec != null) {
        if (state.topHeightOffset(topHeightPx, totalHeightPx) < 0 && state.topHeightOffset(topHeightPx, totalHeightPx) > -topHeightPx) {
            AnimationState(initialValue = state.topHeightOffset(topHeightPx, totalHeightPx)).animateTo(
                if (state.topCollapsedFraction(topHeightPx, totalHeightPx) < 0.5f) {
                    0f
                } else {
                    -topHeightPx
                },
                animationSpec = snapAnimationSpec
            ) {
                state.heightOffset = value + (topHeightPx - totalHeightPx)
            }
        }
    }

    return Velocity(0f, remainingVelocity)
}

/**
 * Default values:
 * - Top app bar height: 128px
 * - Total app bar height: 304px
 * - Bottom app bar height: 176px
 * - Top offset limit: (-(Total), (Top - Total)) = (-304px, -176px)
 * - Bottom offset limit: ((Top - Total), 0) = (-176px, 0px)
 */

private fun TopAppBarState.rawTopHeightOffset(topHeightPx: Float, totalHeightPx: Float): Float {
    return heightOffset + (totalHeightPx - topHeightPx)
}

private fun TopAppBarState.topHeightOffset(topHeightPx: Float, totalHeightPx: Float): Float {
    return rawTopHeightOffset(topHeightPx, totalHeightPx).fastCoerceIn(-topHeightPx, 0f)
}

private fun TopAppBarState.bottomHeightOffset(topHeightPx: Float, totalHeightPx: Float): Float {
    return heightOffset.fastCoerceIn(topHeightPx - totalHeightPx, 0f)
}

private fun TopAppBarState.topCollapsedFraction(topHeightPx: Float, totalHeightPx: Float): Float {
    val offset = topHeightOffset(topHeightPx, totalHeightPx)
    return offset / -topHeightPx
}

private fun TopAppBarState.bottomCollapsedFraction(topHeightPx: Float, totalHeightPx: Float): Float {
    val offset = bottomHeightOffset(topHeightPx, totalHeightPx)
    return offset / (topHeightPx - totalHeightPx)
}

@Composable
fun enterAlwaysCollapsedScrollBehavior(
    state: TopAppBarState = rememberTopAppBarState(),
    canScroll: () -> Boolean = { true },
    isAtTop: () -> Boolean = { true },
    snapAnimationSpec: AnimationSpec<Float>? = spring(stiffness = Spring.StiffnessMediumLow),
    flingAnimationSpec: DecayAnimationSpec<Float>? = rememberSplineBasedDecay()
): TopAppBarScrollBehavior {
    val (topHeightPx, totalHeightPx) = with(LocalDensity.current) {
        CollapsedContainerHeight.toPx() to ExpandedContainerHeight.toPx()
    }

    return remember(state, canScroll, isAtTop, snapAnimationSpec, flingAnimationSpec, topHeightPx, totalHeightPx) {
        EnterAlwaysCollapsedScrollBehavior(
            state = state,
            snapAnimationSpec = snapAnimationSpec,
            flingAnimationSpec = flingAnimationSpec,
            canScroll = canScroll,
            isAtTop = isAtTop,
            topHeightPx = topHeightPx,
            totalHeightPx = totalHeightPx,
        )
    }
}

private class EnterAlwaysCollapsedScrollBehavior(
    override val state: TopAppBarState,
    override val snapAnimationSpec: AnimationSpec<Float>?,
    override val flingAnimationSpec: DecayAnimationSpec<Float>?,
    val canScroll: () -> Boolean = { true },
    // FIXME: See if it's possible to eliminate this argument
    val isAtTop: () -> Boolean = { true },
    val topHeightPx: Float,
    val totalHeightPx: Float,
) : TopAppBarScrollBehavior {
    override val isPinned: Boolean = false
    override var nestedScrollConnection =
        object : NestedScrollConnection {
            private fun TopAppBarState.setClampedOffsetIfAtTop(offset: Float) {
                heightOffset = if (isAtTop()) {
                    offset
                } else {
                    offset.fastCoerceIn(-totalHeightPx, (topHeightPx - totalHeightPx))
                }
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Don't intercept if scrolling down.
                if (!canScroll() || (available.y > 0f && state.rawTopHeightOffset(topHeightPx, totalHeightPx) >= 0f))
                    return Offset.Zero

                val prevHeightOffset = state.heightOffset
                state.setClampedOffsetIfAtTop(state.heightOffset + available.y)
                return if (prevHeightOffset != state.heightOffset) {
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
                state.contentOffset += consumed.y

                if (available.y < 0f || consumed.y < 0f) {
                    // When scrolling up, just update the state's height offset.
                    val oldHeightOffset = state.heightOffset
                    state.setClampedOffsetIfAtTop(state.heightOffset + consumed.y)
                    return Offset(0f, state.heightOffset - oldHeightOffset)
                }

                if (consumed.y == 0f && available.y > 0) {
                    // Reset the total content offset to zero when scrolling all the way down. This
                    // will eliminate some float precision inaccuracies.
                    state.contentOffset = 0f
                }

                if (available.y > 0f) {
                    // Adjust the height offset in case the consumed delta Y is less than what was
                    // recorded as available delta Y in the pre-scroll.
                    val oldHeightOffset = state.heightOffset
                    state.setClampedOffsetIfAtTop(state.heightOffset + available.y)
                    return Offset(0f, state.heightOffset - oldHeightOffset)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val superConsumed = super.onPostFling(consumed, available)
                return superConsumed +
                    settleAppBar(state, available.y, topHeightPx, totalHeightPx, flingAnimationSpec, snapAnimationSpec)
            }
        }
}

val CollapsedContainerHeight = 64.0.dp
val ExpandedContainerHeight = 152.0.dp
internal val TitleAlphaEasing = CubicBezierEasing(.8f, 0f, .8f, .15f)
private val MediumTitleBottomPadding = 24.dp
private val LargeTitleBottomPadding = 28.dp
private val TopAppBarHorizontalPadding = 4.dp
private val TopAppBarTitleInset = 16.dp - TopAppBarHorizontalPadding
