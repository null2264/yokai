package eu.kanade.tachiyomi.data.image.coil

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import coil3.Image
import coil3.target.ImageViewTarget
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.getResourceColor

class CoverViewTarget(
    view: ImageView,
    val progress: View? = null,
    val scaleType: ImageView.ScaleType = ImageView.ScaleType.CENTER_CROP,
) : ImageViewTarget(view) {

    override fun onError(error: Image?) {
        //val drawable = error?.asDrawable(view.context.resources)

        progress?.isVisible = false
        view.scaleType = ImageView.ScaleType.CENTER
        val vector = VectorDrawableCompat.create(
            view.context.resources,
            R.drawable.ic_broken_image_24dp,
            null,
        )
        vector?.setTint(view.context.getResourceColor(android.R.attr.textColorSecondary))
        view.setImageDrawable(vector)
    }

    override fun onStart(placeholder: Image?) {
        //val drawable = placeholder?.asDrawable(view.context.resources)

        progress?.isVisible = true
        view.scaleType = scaleType
        super.onStart(placeholder)
    }

    override fun onSuccess(result: Image) {
        //val drawable = result?.asDrawable(view.context.resources)

        progress?.isVisible = false
        view.scaleType = scaleType
        super.onSuccess(result)
    }
}
