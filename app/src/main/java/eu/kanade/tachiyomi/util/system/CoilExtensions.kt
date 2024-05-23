package eu.kanade.tachiyomi.util.system

import coil3.Extras
import coil3.request.ImageRequest

fun <T> ImageRequest.Builder.setExtras(extraKey: Extras.Key<T>, value: T): ImageRequest.Builder {
    this.extras[extraKey] = value
    return this
}
