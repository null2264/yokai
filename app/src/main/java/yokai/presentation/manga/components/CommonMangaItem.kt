package yokai.presentation.manga.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.R
import yokai.presentation.library.components.LazyLibraryGrid

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
        Badge(
            segments = badgeSegments,
            modifier = Modifier.align(Alignment.TopStart),
            slant = 6.dp,
        )
    }
}

@Preview
@Composable
private fun MangaGridCoverPreview() {
    MaterialTheme {
        Scaffold { contentPadding ->
            LazyLibraryGrid(
                columns = 3,
                contentPadding = contentPadding,
            ) {
                items(10) {
                    MangaGridCover(
                        badgeSegments = listOf(
                            BadgeSegment(
                                fillEntireSegment = true,
                                content = {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_flag_en),
                                        contentDescription = "English",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .wrapContentWidth()
                                            .aspectRatio(3f / 2f)
                                    )
                                }
                            ),

                            BadgeSegment(
                                backgroundColor = Color(0xFF98CA9A),
                                content = {
                                    Text(
                                        text = "1",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        style = TextStyle(
                                            platformStyle = PlatformTextStyle(
                                                includeFontPadding = false
                                            ),
                                        ),
                                    )
                                }
                            ),

                            BadgeSegment(
                                backgroundColor = Color(0xFFEBC16D),
                                contentPadding = PaddingValues(start = 4.dp, end = 8.dp),
                                content = {
                                    Text(
                                        text = "23",
                                        color = Color(0xFF1E1E1E),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        style = TextStyle(
                                            platformStyle = PlatformTextStyle(
                                                includeFontPadding = false
                                            ),
                                        ),
                                    )
                                }
                            )
                        )
                    ) {
                    }
                }
            }
        }
    }
}
