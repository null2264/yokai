package eu.kanade.tachiyomi.data.image.coil

import android.graphics.Bitmap
import android.os.Build
import coil3.ImageLoader
import coil3.asCoilImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.bitmapConfig
import eu.kanade.tachiyomi.util.system.GLUtil
import eu.kanade.tachiyomi.util.system.ImageUtil
import okio.BufferedSource
import tachiyomi.decoder.ImageDecoder

/**
 * A [Decoder] that uses built-in [ImageDecoder] to decode images that is not supported by the system.
 */
class TachiyomiImageDecoder(private val resources: ImageSource, private val options: Options) : Decoder {

    override suspend fun decode(): DecodeResult {
        val decoder = resources.sourceOrNull()?.use {
            ImageDecoder.newInstance(it.inputStream(), options.cropBorders, displayProfile)
        }

        check(decoder != null && decoder.width > 0 && decoder.height > 0) { "Failed to initialize decoder." }

        val srcWidth = decoder.width
        val srcHeight = decoder.height

        val dstWidth = options.size.widthPx(options.scale) { srcWidth }
        val dstHeight = options.size.heightPx(options.scale) { srcHeight }

        val sampleSize = DecodeUtils.calculateInSampleSize(
            srcWidth = srcWidth,
            srcHeight = srcHeight,
            dstWidth = dstWidth,
            dstHeight = dstHeight,
            scale = options.scale,
        )

        var bitmap = decoder.decode(sampleSize = sampleSize)
        decoder.recycle()

        check(bitmap != null) { "Failed to decode image." }

        // FIXME: Remove this once Coil handle max texture size
        // REF: https://github.com/coil-kt/coil/issues/2211
        if (maxOf(bitmap.width, bitmap.height) > GLUtil.maxTextureSize) {
            val widthRatio = bitmap.width / 4096f
            val heightRatio = bitmap.height / 4096f

            val targetWidth: Float
            val targetHeight: Float

            if (widthRatio >= heightRatio) {
                targetWidth = 4096f
                targetHeight = (targetWidth / bitmap.width) * bitmap.height
            } else {
                targetHeight = 4096f
                targetWidth = (targetHeight / bitmap.height) * bitmap.width
            }

            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth.toInt(), targetHeight.toInt(), false)
            bitmap.recycle()
            bitmap = scaledBitmap
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            options.bitmapConfig == Bitmap.Config.HARDWARE &&
            maxOf(bitmap.width, bitmap.height) <= GLUtil.maxTextureSize
        ) {
            val hwBitmap = bitmap.copy(Bitmap.Config.HARDWARE, false)
            if (hwBitmap != null) {
                bitmap.recycle()
                bitmap = hwBitmap
            }
        }

        return DecodeResult(
            image = bitmap.asCoilImage(),
            isSampled = sampleSize > 1,
        )
    }

    class Factory : Decoder.Factory {

        override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
            return if (options.customDecoder || isApplicable(result.source.source())) {
                TachiyomiImageDecoder(result.source, options)
            } else {
                null
            }
        }

        private fun isApplicable(source: BufferedSource): Boolean {
            val type = source.peek().inputStream().use {
                ImageUtil.findImageType(it)
            }
            return when (type) {
                ImageUtil.ImageType.AVIF, ImageUtil.ImageType.JXL -> true
                ImageUtil.ImageType.HEIF -> Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                else -> false
            }
        }

        override fun equals(other: Any?) = other is Factory

        override fun hashCode() = javaClass.hashCode()
    }

    companion object {
        var displayProfile: ByteArray? = null
    }
}
