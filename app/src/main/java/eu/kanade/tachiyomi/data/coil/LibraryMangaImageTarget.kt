package eu.kanade.tachiyomi.data.coil

import android.graphics.BitmapFactory
import android.widget.ImageView
import androidx.palette.graphics.Palette
import coil3.Image
import coil3.target.ImageViewTarget
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.models.updateCoverLastModified
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.util.system.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import yokai.domain.manga.models.MangaCover
import yokai.domain.manga.models.cover

class LibraryMangaImageTarget(
    override val view: ImageView,
    private val libraryManga: Manga,
) : ImageViewTarget(view) {

    override fun onError(error: Image?) {
        super.onError(error)
        libraryManga.cover().onLibraryImageError()
    }
}

fun MangaCover.onLibraryImageError(coverCache: CoverCache = Injekt.get()) {
    if (this.inLibrary) {
        launchIO {
            val file = coverCache.getCoverFile(this@onLibraryImageError.url, false)
            // if the file exists and the there was still an error then the file is corrupted
            if (file != null && file.exists()) {
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeFile(file.path, options)
                if (options.outWidth == -1 || options.outHeight == -1) {
                    this@onLibraryImageError.updateCoverLastModified()
                    file.delete()
                }
            }
        }
    }
}

fun Palette.getBestColor(defaultColor: Int) = getBestColor() ?: defaultColor

fun Palette.getBestColor(): Int? {
    val vibPopulation = vibrantSwatch?.population ?: -1
    val domLum = dominantSwatch?.hsl?.get(2) ?: -1f
    val mutedPopulation = mutedSwatch?.population ?: -1
    val mutedSaturationLimit = if (mutedPopulation > vibPopulation * 3f) 0.1f else 0.25f
    return when {
        (dominantSwatch?.hsl?.get(1) ?: 0f) >= .25f &&
            domLum <= .8f && domLum > .2f -> dominantSwatch?.rgb
        vibPopulation >= mutedPopulation * 0.75f -> vibrantSwatch?.rgb
        mutedPopulation > vibPopulation * 1.5f &&
            (mutedSwatch?.hsl?.get(1) ?: 0f) > mutedSaturationLimit -> mutedSwatch?.rgb
        else -> arrayListOf(vibrantSwatch, lightVibrantSwatch, darkVibrantSwatch).maxByOrNull {
            if (it === vibrantSwatch) (it?.population ?: -1) * 3 else it?.population ?: -1
        }?.rgb
    }
}
