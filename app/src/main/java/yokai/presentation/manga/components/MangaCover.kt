package yokai.presentation.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun MangaCover(
    data: Any?,
    modifier: Modifier = Modifier,
    ratio: Float? = null,
    contentDescription: String = "",
    shape: Shape = RoundedCornerShape(12.dp),
    contentScale: ContentScale = ContentScale.Crop,
    onClick: (() -> Unit)? = null,
    onSuccess: (() -> Unit)? = null,
) {
    AsyncImage(
        model = data,
        // Using your placeholder color from the metadata logic
        placeholder = ColorPainter(Color(0xFF3B3840)),
//        error = rememberResourceBitmapPainter(id = R.drawable.cover_error),
        contentDescription = contentDescription,
        contentScale = contentScale,
        onSuccess = { _ ->
            onSuccess?.invoke()
        },
        modifier = modifier
            .then(if (ratio != null) Modifier.aspectRatio(ratio) else Modifier)
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
    )
}

object MangaCoverRatio {
    val SQUARE = 1f / 1f
    val BOOK = 2f / 3f
}
