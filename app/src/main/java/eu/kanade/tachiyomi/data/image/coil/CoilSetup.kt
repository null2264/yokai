package eu.kanade.tachiyomi.data.image.coil

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.getSystemService
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowHardware
import coil3.request.allowRgb565
import coil3.request.crossfade
import eu.kanade.tachiyomi.network.NetworkHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CoilSetup {
    companion object {
        fun setup(context: Context): ImageLoader {
            return ImageLoader.Builder(context).apply {
                val callFactoryLazy = lazy { Injekt.get<NetworkHelper>().client }
                val diskCacheLazy = lazy { CoilDiskCache.get(context) }
                components {
                    add(OkHttpNetworkFetcherFactory(callFactoryLazy::value))
                    add(TachiyomiImageDecoder.Factory())
                    add(MangaCoverFetcher.Factory(callFactoryLazy, diskCacheLazy))
                    add(MangaCoverKeyer())
                }
                diskCache(diskCacheLazy::value)
                memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.40).build() }
                crossfade(true)
                allowRgb565(context.getSystemService<ActivityManager>()!!.isLowRamDevice)
                allowHardware(true)
            }.build()
        }
    }
}

/**
 * Direct copy of Coil's internal SingletonDiskCache so that [MangaCoverFetcher] can access it.
 */
internal object CoilDiskCache {

    private const val FOLDER_NAME = "image_cache"
    private var instance: DiskCache? = null

    @Synchronized
    fun get(context: Context): DiskCache {
        return instance ?: run {
            val safeCacheDir = context.cacheDir.apply { mkdirs() }
            // Create the singleton disk cache instance.
            DiskCache.Builder()
                .directory(safeCacheDir.resolve(FOLDER_NAME))
                .build()
                .also { instance = it }
        }
    }
}
