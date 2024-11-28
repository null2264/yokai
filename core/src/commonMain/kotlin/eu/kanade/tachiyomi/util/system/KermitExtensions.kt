package eu.kanade.tachiyomi.util.system

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.platformLogWriter

fun Logger.w(e: Throwable) = w(e) { "Something is not right..." }
fun Logger.e(e: Throwable) = e(e) { "Something went wrong!" }

fun Logger.setToDefault(writersToAdd: List<LogWriter>) {
    Logger.setLogWriters(listOf(platformLogWriter()) + writersToAdd)
    Logger.setTag("Yokai")
}
