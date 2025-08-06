package eu.kanade.tachiyomi.data.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.text.format.Formatter
import co.touchlab.kermit.Logger
import coil3.imageLoader
import eu.kanade.tachiyomi.data.database.models.updateCoverLastModified
import eu.kanade.tachiyomi.domain.manga.models.Manga
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.system.withIOContext
import eu.kanade.tachiyomi.util.system.withUIContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.injectLazy
import yokai.domain.manga.interactor.GetManga
import yokai.i18n.MR
import yokai.util.lang.getString

/**
 * Class used to create cover cache.
 * It is used to store the covers of the library.
 * Names of files are created with the md5 of the thumbnail URL.
 *
 * @param context the application context.
 * @constructor creates an instance of the cover cache.
 */
class CoverCache(val context: Context) {

    companion object {
        private const val COVERS_DIR = "covers"
        private const val CUSTOM_COVERS_DIR = "covers/custom"
        private const val ONLINE_COVERS_DIR = "online_covers"
    }

    private val getManga: GetManga by injectLazy()

    /** Cache directory used for cache management.*/
    private val cacheDir = getCacheDir(COVERS_DIR)

    /** Cache directory used for custom cover cache management.*/
    private val customCoverCacheDir = getCacheDir(CUSTOM_COVERS_DIR)

    /** Cache directory used for covers not in library management.*/
    private val onlineCoverDirectory = File(context.cacheDir, ONLINE_COVERS_DIR).also { it.mkdirs() }

    private val maxOnlineCacheSize = 50L * 1024L * 1024L // 50 MB

    private var lastClean = 0L

    /**
     * The interval after which this cache should be invalidated. 1 hour shouldn't cause major
     * issues, as the cache is only used for UI feedback.
     */
    private val renewInterval = TimeUnit.HOURS.toMillis(1)

    fun getChapterCacheSize(): String {
        return Formatter.formatFileSize(context, DiskUtil.getDirectorySize(cacheDir))
    }

    fun getOnlineCoverCacheSize(): String {
        return Formatter.formatFileSize(context, DiskUtil.getDirectorySize(onlineCoverDirectory))
    }

    suspend fun deleteOldCovers() {
        var deletedSize = 0L
        val urls = getManga.awaitFavorites().mapNotNull {
            it.thumbnail_url?.let { url ->
                it.updateCoverLastModified()
                return@mapNotNull DiskUtil.hashKeyForDisk(url)
            }
            null
        }
        val files = cacheDir.listFiles()?.iterator() ?: return
        while (files.hasNext()) {
            val file = files.next()
            if (file.isFile && file.name !in urls) {
                deletedSize += file.length()
                file.delete()
            }
        }
        withUIContext {
            context.toast(
                context.getString(
                    MR.strings.deleted_,
                    Formatter.formatFileSize(context, deletedSize),
                ),
            )
        }
    }

    /**
     * Clear out all online covers
     */
    suspend fun deleteAllCachedCovers() {
        val directory = onlineCoverDirectory
        var deletedSize = 0L
        val files =
            directory.listFiles()?.sortedBy { it.lastModified() }?.iterator() ?: return
        while (files.hasNext()) {
            val file = files.next()
            deletedSize += file.length()
            file.delete()
        }
        withContext(Dispatchers.Main) {
            context.toast(
                context.getString(
                    MR.strings.deleted_,
                    Formatter.formatFileSize(context, deletedSize),
                ),
            )
        }
        context.imageLoader.memoryCache?.clear()

        lastClean = System.currentTimeMillis()
    }

    /**
     * Clear out online covers until its under a certain size
     */
    suspend fun deleteCachedCovers() {
        withIOContext {
            if (lastClean + renewInterval < System.currentTimeMillis()) {
                try {
                    val directory = onlineCoverDirectory
                    val size = DiskUtil.getDirectorySize(directory)
                    if (size <= maxOnlineCacheSize) {
                        return@withIOContext
                    }
                    var deletedSize = 0L
                    val files = directory.listFiles()?.sortedBy { it.lastModified() }?.iterator()
                        ?: return@withIOContext
                    while (files.hasNext()) {
                        val file = files.next()
                        deletedSize += file.length()
                        file.delete()
                        if (size - deletedSize <= maxOnlineCacheSize) {
                            break
                        }
                    }
                } catch (e: Exception) {
                    Logger.e(e) { "Unable to delete unused cover cache" }
                }
                lastClean = System.currentTimeMillis()
            }
        }
    }

    /**
     * Returns the custom cover from cache.
     *
     * @param manga the manga.
     * @return cover image.
     */
    fun getCustomCoverFile(manga: Manga): File = getCustomCoverFile(manga.id)

    fun getCustomCoverFile(mangaId: Long?): File {
        return File(customCoverCacheDir, DiskUtil.hashKeyForDisk(mangaId.toString()))
    }

    /**
     * Saves the given stream as the manga's custom cover to cache.
     *
     * @param manga the manga.
     * @param inputStream the stream to copy.
     * @throws IOException if there's any error.
     */
    @Throws(IOException::class)
    fun setCustomCoverToCache(manga: Manga, inputStream: InputStream) {
        val maxTextureSize = 4096f

        val imageBytes = inputStream.readBytes()
        inputStream.close()

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)

        val originalWidth = bounds.outWidth
        val originalHeight = bounds.outHeight

        val sampleSize = calculateInSampleSize(originalWidth, originalHeight, maxTextureSize.toInt())

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = if (sampleSize > 1) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        }

        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOptions)
            ?: throw IOException("Failed to decode image")

        if (maxOf(bitmap.width, bitmap.height) > maxTextureSize) {
            val widthRatio = bitmap.width / maxTextureSize
            val heightRatio = bitmap.height / maxTextureSize

            val targetWidth: Float
            val targetHeight: Float

            if (widthRatio >= heightRatio) {
                targetWidth = maxTextureSize
                targetHeight = (targetWidth / bitmap.width) * bitmap.height
            } else {
                targetHeight = maxTextureSize
                targetWidth = (targetHeight / bitmap.height) * bitmap.width
            }

            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth.toInt(), targetHeight.toInt(), true)
            bitmap.recycle()
            bitmap = scaledBitmap
        }
        getCustomCoverFile(manga).outputStream().use {
            @Suppress("DEPRECATION")
            bitmap.compress(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    Bitmap.CompressFormat.WEBP_LOSSLESS
                else
                    Bitmap.CompressFormat.WEBP,
                100,
                it
            )
            bitmap.recycle()
        }
    }

    /**
     * Calculate the largest inSampleSize value that is a power of 2 and keeps both
     * height and width larger than the requested height and width.
     *
     * @param width the original image width.
     * @param height the original image height.
     * @param maxSize the maximum allowed dimension.
     * @return the sample size to use for BitmapFactory.Options.
     */
    private fun calculateInSampleSize(width: Int, height: Int, maxSize: Int): Int {
        var inSampleSize = 1
        if (height > maxSize || width > maxSize) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= maxSize && (halfWidth / inSampleSize) >= maxSize) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Delete custom cover of the manga from the cache
     *
     * @param manga the manga.
     * @return whether the cover was deleted.
     */
    fun deleteCustomCover(manga: Manga): Boolean {
        val result = getCustomCoverFile(manga).let {
            it.exists() && it.delete()
        }
        return result
    }

    /**
     * Returns the cover from cache.
     *
     * @param mangaThumbnailUrl the thumbnail url.
     * @return cover image.
     */
    fun getCoverFile(mangaThumbnailUrl: String?, isOnline: Boolean = false): File? {
        return mangaThumbnailUrl?.let {
            File(if (!isOnline) cacheDir else onlineCoverDirectory, DiskUtil.hashKeyForDisk(it))
        }
    }

    /**
     * Delete the cover file from the disk cache and optional from memory cache
     *
     * @param thumbnailUrl the thumbnail url.
     * @return status of deletion.
     */
    fun deleteFromCache(
        manga: Manga,
        deleteCustom: Boolean = true,
    ) {
        // Check if url is empty.
        if (manga.thumbnail_url.isNullOrEmpty()) return

        // Remove file
        getCoverFile(manga.thumbnail_url, !manga.favorite)?.let {
            if (it.exists()) it.delete()
        }
        if (deleteCustom) deleteCustomCover(manga)
    }

    private fun getCacheDir(dir: String): File {
        return context.getExternalFilesDir(dir)
            ?: File(context.filesDir, dir).also { it.mkdirs() }
    }
}
