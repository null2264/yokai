package yokai.presentation.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

class ForwardSlantedShape(private val slantAmount: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(
            Path().apply {
                moveTo(slantAmount, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
        )
    }
}

data class BadgeSegment(
    val backgroundColor: Color = Color.Transparent,
    val fillEntireSegment: Boolean = false,
    val contentPadding: PaddingValues = PaddingValues(horizontal = 4.dp),
    val content: @Composable () -> Unit,
)

@Composable
fun Badge(
    segments: List<BadgeSegment>,
    modifier: Modifier = Modifier,
    slant: Dp = 10.dp,
    height: Dp = 18.dp,
) {
    val slantPx = with(LocalDensity.current) { slant.toPx() }
    val layoutDirection = LocalLayoutDirection.current

    Row(
        modifier = modifier
            .height(height)
            .clip(CutCornerShape(bottomEnd = 5.dp)),
        horizontalArrangement = Arrangement.spacedBy(-slant),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segments.forEachIndexed { index, segment ->
            val isFirst = index == 0
            val isLast = index == segments.size - 1

            val shape = if (isFirst) RectangleShape else ForwardSlantedShape(slantPx)

            val leftStructuralPad = if (isFirst || segment.fillEntireSegment) 0.dp else slant
            val rightStructuralPad = if (isLast || segment.fillEntireSegment) 0.dp else slant

            val startPad = leftStructuralPad +
                if (segment.fillEntireSegment)
                    0.dp
                else
                    segment.contentPadding.calculateStartPadding(layoutDirection).let { if (isFirst) it + 4.dp else it }
            val endPad = rightStructuralPad +
                if (segment.fillEntireSegment)
                    0.dp
                else
                    segment.contentPadding.calculateEndPadding(layoutDirection).let { if (isLast) it + 4.dp else it }
            val topPad = if (segment.fillEntireSegment) 0.dp else segment.contentPadding.calculateTopPadding()
            val bottomPad = if (segment.fillEntireSegment) 0.dp else segment.contentPadding.calculateBottomPadding()

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(shape)
                    .background(segment.backgroundColor)
                    .padding(start = startPad, end = endPad, top = topPad, bottom = bottomPad),
                contentAlignment = Alignment.Center
            ) {
                segment.content()
            }
        }
    }
}
