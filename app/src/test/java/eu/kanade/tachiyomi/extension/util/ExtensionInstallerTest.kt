package eu.kanade.tachiyomi.extension.util

import android.app.DownloadManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ExtensionInstallerTest {
    @Test
    fun `completed download retains its local uri`() {
        val uri = "file:///tmp/extension.apk"

        assertEquals(uri, completedDownloadUri(DownloadManager.STATUS_SUCCESSFUL, uri))
    }

    @Test
    fun `failed download does not return a local uri`() {
        assertNull(completedDownloadUri(DownloadManager.STATUS_FAILED, "file:///tmp/extension.apk"))
    }
}
