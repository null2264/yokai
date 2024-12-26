package eu.kanade.tachiyomi.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView.ScaleType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.AbstractComposeView
import androidx.core.view.isVisible
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import eu.kanade.tachiyomi.data.coil.useCustomCover
import yokai.presentation.component.manga.MangaCover

class MangaCoverView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    var adjustViewBounds by mutableStateOf(true)
    var ratio by mutableFloatStateOf(2f / 3f)
    private var data by mutableStateOf<Data?>(null)

    var maxHeight: Int
        get() = data?.maximumHeight ?: 0
        set(value) {
            data = data?.copy(maximumHeight = value)
        }

    var scaleType: ScaleType
        get() = throw NotImplementedError()
        set(value) {
            data = data?.copy(scaleType = value.toContentScale())
        }

    fun reset() {
        this.data = null
    }

    fun book() {
        this.ratio = 2f / 3f
    }

    fun square() {
        this.ratio = 1f / 1f
    }

    fun loadManga(
        cover: Any,
        progress: View? = null,
        scaleType: ScaleType = ScaleType.CENTER_CROP,
        minimumHeight: Int = this.minimumHeight,
        maximumHeight: Int? = null,
        useCustomCover: Boolean = true,
    ) {
        loadManga(
            cover = cover,
            scaleType = scaleType,
            minimumHeight = minimumHeight,
            maximumHeight = maximumHeight,
            useCustomCover = useCustomCover,
            onLoading = {
                progress?.isVisible = true
            },
            onError = {
                progress?.isVisible = false
            },
            onSuccess = {
                progress?.isVisible = false
            },
        )
    }

    private fun ScaleType.toContentScale(): ContentScale {
        return when (this) {
            ScaleType.CENTER_CROP -> ContentScale.Crop
            ScaleType.CENTER_INSIDE -> ContentScale.Inside
            ScaleType.FIT_CENTER -> ContentScale.Fit
            else -> ContentScale.None
        }
    }

    fun loadManga(
        cover: Any,
        scaleType: ScaleType = ScaleType.CENTER_CROP,
        minimumHeight: Int = this.minimumHeight,
        maximumHeight: Int? = null,
        useCustomCover: Boolean = true,
        onLoading: ((AsyncImagePainter.State.Loading) -> Unit)? = null,
        onError: ((AsyncImagePainter.State.Error) -> Unit)? = null,
        onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    ) {
        val coverRequest = ImageRequest.Builder(context)
            .data(cover)
            .useCustomCover(useCustomCover)

        this.data = Data(
            cover = coverRequest.build(),
            scaleType = scaleType.toContentScale(),
            minimumHeight = minimumHeight,
            maximumHeight = maximumHeight,
            onLoading = onLoading,
            onError = onError,
            onSuccess = onSuccess,
        )
    }

    override fun setMinimumHeight(minHeight: Int) {
        super.setMinimumHeight(minHeight)
        data = data?.copy(minimumHeight = minHeight)
    }

    @Composable
    override fun Content() {
        data?.let {
            MangaCover(
                data = it.cover,
                adjustViewBounds = { adjustViewBounds },
                ratio = { ratio },
                minimumHeight = { it.minimumHeight },
                maximumHeight = it.maximumHeight?.let { { it } },
                onLoading = it.onLoading,
                onError = it.onError,
                onSuccess = it.onSuccess,
            )
        }
    }

    data class Data(
        val cover: Any?,
        val scaleType: ContentScale = ContentScale.Crop,
        val minimumHeight: Int,
        val maximumHeight: Int? = null,
        val onLoading: ((AsyncImagePainter.State.Loading) -> Unit)? = null,
        val onError: ((AsyncImagePainter.State.Error) -> Unit)? = null,
        val onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    )
}
