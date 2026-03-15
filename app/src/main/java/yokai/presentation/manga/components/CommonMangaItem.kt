package yokai.presentation.manga.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.desc.Utils
import yokai.presentation.library.components.LazyLibraryGrid
import yokai.domain.manga.models.MangaCover as MangaCoverModel

@Composable
fun MangaCompactGridItem(
    coverData: MangaCoverModel,
    lang: String? = null,
    unreadCount: Int = 0,
    downloadCount: Int = 0,
    badgeSegments: List<BadgeSegment> = listOf(),
    isSelected: Boolean = false,
) {
    val context = LocalContext.current

    val badgeSegments = buildList {
        if (!lang.isNullOrBlank()) {
            val resources = Utils.resourcesForContext(context)
            val flagId = resources.getIdentifier(
                "ic_flag_${lang.replace("-", "_")}",
                "drawable",
                context.packageName,
            ).takeIf { it != 0 } ?: (
                if (lang.contains("-")) {
                    resources.getIdentifier(
                        "ic_flag_${lang.split("-").first()}",
                        "drawable",
                        context.packageName,
                    ).takeIf { it != 0 }
                } else {
                    null
                }
                )
            if (flagId != null) {
                add(
                    BadgeSegment(
                        fillEntireSegment = true,
                        content = {
                            Image(
                                painter = painterResource(id = flagId),
                                contentDescription = "lang: $lang",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .wrapContentWidth()
                                    .aspectRatio(3f / 2f)
                            )
                        }
                    )
                )

                if (downloadCount > 0) {
                    add(
                        BadgeSegment(
                            backgroundColor = MaterialTheme.colorScheme.tertiary,
                            content = {
                                Text(
                                    text = downloadCount.toString(),
                                    color = MaterialTheme.colorScheme.onTertiary,
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
                }

                if (unreadCount > 0) {
                    add(
                        BadgeSegment(
                            backgroundColor = MaterialTheme.colorScheme.secondary,
                            content = {
                                Text(
                                    text = unreadCount.toString(),
                                    color = MaterialTheme.colorScheme.onSecondary,
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
                }
            }
        }
    } + badgeSegments

    MangaGridCover(
        cover = {
            MangaCover(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (isSelected) 0.34f else 1.0f),
                data = coverData,
            )
        },
        badgeSegments = badgeSegments,
        content = {
            CoverTextOverlay(
                title = "dingus",
                onClickContinueReading = {},
            )
        },
    )
}

@Composable
private fun BoxScope.CoverTextOverlay(
    title: String,
    onClickContinueReading: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color(0xAA000000),
                ),
            )
            .fillMaxHeight(0.33f)
            .fillMaxWidth()
            .align(Alignment.BottomCenter),
    )
    Row(
        modifier = Modifier.align(Alignment.BottomStart),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
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
                    MangaCompactGridItem(
                        coverData = MangaCoverModel(
                            mangaId = 0,
                            sourceId = 0,
                            url = "https://www.example.com/image.jpg",
                            lastModified = 0,
                            inLibrary = false,
                        ),
                        isSelected = false,
                        badgeSegments = listOf(
                            BadgeSegment(
                                backgroundColor = MaterialTheme.colorScheme.secondary,
                                content = {
                                    Text(
                                        text = "In Library",
                                        color = MaterialTheme.colorScheme.onSecondary,
                                        fontSize = 13.sp,
                                        style = TextStyle(
                                            platformStyle = PlatformTextStyle(
                                                includeFontPadding = false
                                            ),
                                        ),
                                    )
                                }
                            ),
                        )
                    )
                }
            }
        }
    }
}
