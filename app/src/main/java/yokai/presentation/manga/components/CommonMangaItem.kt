package yokai.presentation.manga.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.R
import yokai.presentation.library.components.LazyLibraryGrid

data class BadgeSegment(
    val color: Color,
    val content: @Composable () -> Unit
)

@Composable
fun SlantedCapsuleBadge(
    segments: List<BadgeSegment>,
    modifier: Modifier = Modifier
) {
    val slantWidth = 10.dp
    val badgeHeight = 24.dp

    Row(
        modifier = modifier.height(badgeHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segments.forEachIndexed { index, segment ->
            val isFirst = index == 0
            val isLast = index == segments.lastIndex

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = if (isFirst) 0.dp else (-4).dp)
                    .drawBehind {
                        val s = slantWidth.toPx()
                        val h = size.height
                        val w = size.width
                        val r = h / 2 // Radius for full rounding

                        val path = Path().apply {
                            when {
                                isFirst -> {
                                    arcTo(Rect(0f, 0f, h, h), 90f, 180f, false)
                                    lineTo(w + s, 0f)
                                    lineTo(w, h)
                                }
                                isLast -> {
                                    moveTo(s, 0f)
                                    lineTo(w - r, 0f)
                                    arcTo(Rect(w - h, 0f, w, h), -90f, 180f, false)
                                    lineTo(0f, h)
                                }
                                else -> {
                                    moveTo(s, 0f)
                                    lineTo(w + s, 0f)
                                    lineTo(w, h)
                                    lineTo(0f, h)
                                }
                            }
                            close()
                        }
                        drawPath(path, segment.color)
                    }
                    .padding(
                        start = if (isFirst) 8.dp else 14.dp,
                        end = if (isLast) 10.dp else 6.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                segment.content()
            }
        }
    }
}

@Composable
fun MangaGridCover(
    modifier: Modifier = Modifier,
    cover: @Composable BoxScope.() -> Unit = {},
    badgeSegments: List<BadgeSegment> = listOf(),
    content: @Composable (BoxScope.() -> Unit)? = null,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(MangaCoverRatio.BOOK)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        cover()
        content?.invoke(this)
        SlantedCapsuleBadge(
            segments = badgeSegments,
            modifier = Modifier.padding(4.dp).align(Alignment.TopStart)
        )
    }
}

@Preview
@Composable
private fun CombinedCircularProgressIndicatorPreview() {
    MaterialTheme {
        Scaffold { contentPadding ->
            LazyLibraryGrid(
                columns = 3,
                contentPadding = contentPadding,
            ) {
                items(10) {
                    MangaGridCover(
                        badgeSegments = listOf(BadgeSegment(Color(0xFF87A9A3)) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_flag_en),
                                contentDescription = null,
                                modifier = Modifier.size(width = 18.dp, height = 12.dp)
                            )
                        })
                    ) {
                    }
                }
            }
        }
    }
}
