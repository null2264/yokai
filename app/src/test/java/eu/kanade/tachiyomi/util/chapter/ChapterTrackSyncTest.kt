package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.database.models.Chapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChapterTrackSyncTest {

    @Test
    fun `tracker ahead marks continuous unread chapters`() {
        val result = calculateTwoWayTrackerSync(
            chapters = listOf(
                chapter(number = 3f, sourceOrder = 0, read = false),
                chapter(number = 2f, sourceOrder = 1, read = false),
                chapter(number = 1f, sourceOrder = 2, read = false),
            ),
            remoteLastRead = 3f,
        )

        assertEquals(setOf(3f, 2f, 1f), result.coveredChapterNumbers)
        assertEquals(listOf(3f, 2f, 1f), result.chapterUpdates.map { it.chapter_number })
        assertEquals(0f, result.localLastRead)
    }

    @Test
    fun `local ahead only reports local continuous progress`() {
        val result = calculateTwoWayTrackerSync(
            chapters = listOf(
                chapter(number = 5f, sourceOrder = 0, read = true),
                chapter(number = 4f, sourceOrder = 1, read = true),
                chapter(number = 3f, sourceOrder = 2, read = true),
                chapter(number = 2f, sourceOrder = 3, read = true),
                chapter(number = 1f, sourceOrder = 4, read = true),
            ),
            remoteLastRead = 3f,
        )

        assertEquals(setOf(3f, 2f, 1f), result.coveredChapterNumbers)
        assertEquals(emptyList<Float>(), result.chapterUpdates.map { it.chapter_number })
        assertEquals(5f, result.localLastRead)
    }

    @Test
    fun `equal progress does not schedule local updates`() {
        val result = calculateTwoWayTrackerSync(
            chapters = listOf(
                chapter(number = 3f, sourceOrder = 0, read = true),
                chapter(number = 2f, sourceOrder = 1, read = true),
                chapter(number = 1f, sourceOrder = 2, read = true),
            ),
            remoteLastRead = 3f,
        )

        assertEquals(setOf(3f, 2f, 1f), result.coveredChapterNumbers)
        assertEquals(emptyList<Float>(), result.chapterUpdates.map { it.chapter_number })
        assertEquals(3f, result.localLastRead)
    }

    @Test
    fun `unrecognized chapters are ignored`() {
        val result = calculateTwoWayTrackerSync(
            chapters = listOf(
                chapter(number = 2f, sourceOrder = 0, read = false),
                chapter(number = -1f, sourceOrder = 1, read = false),
                chapter(number = 1f, sourceOrder = 2, read = false),
            ),
            remoteLastRead = 2f,
        )

        assertEquals(setOf(2f, 1f), result.coveredChapterNumbers)
        assertEquals(listOf(2f, 1f), result.chapterUpdates.map { it.chapter_number })
    }

    @Test
    fun `non monotonic numbering stops local sync`() {
        val result = calculateTwoWayTrackerSync(
            chapters = listOf(
                chapter(number = 3f, sourceOrder = 0, read = false),
                chapter(number = 1f, sourceOrder = 1, read = false),
                chapter(number = 2f, sourceOrder = 2, read = false),
            ),
            remoteLastRead = 3f,
        )

        assertEquals(setOf(3f), result.coveredChapterNumbers)
        assertEquals(listOf(3f), result.chapterUpdates.map { it.chapter_number })
    }

    @Test
    fun `gap after regression is not synced`() {
        val result = calculateTwoWayTrackerSync(
            chapters = listOf(
                chapter(number = 10f, sourceOrder = 0, read = false),
                chapter(number = 8f, sourceOrder = 1, read = false),
                chapter(number = 7f, sourceOrder = 2, read = false),
                chapter(number = 9f, sourceOrder = 3, read = false),
            ),
            remoteLastRead = 10f,
        )

        assertEquals(setOf(10f, 8f, 7f), result.coveredChapterNumbers)
        assertEquals(listOf(10f, 8f, 7f), result.chapterUpdates.map { it.chapter_number })
    }

    @Test
    fun `duplicate chapter variants are all marked when covered`() {
        val result = calculateTwoWayTrackerSync(
            chapters = listOf(
                chapter(number = 165f, sourceOrder = 0, read = false, name = "Official Scans 165", url = "/official-165"),
                chapter(number = 165f, sourceOrder = 1, read = false, name = "Punch 165", url = "/punch-165"),
                chapter(number = 164f, sourceOrder = 2, read = false, name = "Punch 164", url = "/punch-164"),
            ),
            remoteLastRead = 165f,
        )

        assertEquals(setOf(165f, 164f), result.coveredChapterNumbers)
        assertEquals(
            listOf("Official Scans 165", "Punch 165", "Punch 164"),
            result.chapterUpdates.map { it.name },
        )
    }

    @Test
    fun `interleaved one punch man style variants continue across duplicate branches`() {
        val result = calculateTwoWayTrackerSync(
            chapters = listOf(
                chapter(number = 167f, sourceOrder = 0, read = false, name = "Punch 167", url = "/punch-167"),
                chapter(number = 166f, sourceOrder = 1, read = false, name = "Punch 166", url = "/punch-166"),
                chapter(number = 165f, sourceOrder = 2, read = false, name = "Official Scans 165", url = "/official-165"),
                chapter(number = 165f, sourceOrder = 3, read = false, name = "Punch 165", url = "/punch-165"),
                chapter(number = 164f, sourceOrder = 4, read = false, name = "Official Scans 164", url = "/official-164"),
                chapter(number = 164f, sourceOrder = 5, read = false, name = "Punch 164", url = "/punch-164"),
                chapter(number = 163f, sourceOrder = 6, read = false, name = "Official Scans 163", url = "/official-163"),
                chapter(number = 163f, sourceOrder = 7, read = false, name = "Punch 163", url = "/punch-163"),
                chapter(number = 162f, sourceOrder = 8, read = false, name = "Official Scans 162", url = "/official-162"),
                chapter(number = 162f, sourceOrder = 9, read = false, name = "Punch 162", url = "/punch-162"),
                chapter(number = 161f, sourceOrder = 10, read = false, name = "Official Scans 161", url = "/official-161"),
                chapter(number = 161f, sourceOrder = 11, read = false, name = "Punch 161", url = "/punch-161"),
            ),
            remoteLastRead = 167f,
        )

        assertEquals(setOf(167f, 166f, 165f, 164f, 163f, 162f, 161f), result.coveredChapterNumbers)
        assertEquals(12, result.chapterUpdates.size)
    }

    @Test
    fun `mixed variants plus mag versions continue through tracker progress`() {
        val result = calculateTwoWayTrackerSync(
            chapters = listOf(
                chapter(number = 225f, sourceOrder = 0, read = false, name = "Mag Version 225", url = "/mag-225"),
                chapter(number = 224.5f, sourceOrder = 1, read = false, name = "ReDraw 224.5", url = "/redraw-224-5"),
                chapter(number = 224f, sourceOrder = 2, read = false, name = "Mag Version 224", url = "/mag-224"),
                chapter(number = 223f, sourceOrder = 3, read = false, name = "Mag Version 223", url = "/mag-223"),
                chapter(number = 167f, sourceOrder = 4, read = false, name = "Punch 167", url = "/punch-167"),
                chapter(number = 166f, sourceOrder = 5, read = false, name = "Punch 166", url = "/punch-166"),
                chapter(number = 165f, sourceOrder = 6, read = false, name = "Official Scans 165", url = "/official-165"),
                chapter(number = 165f, sourceOrder = 7, read = false, name = "Punch 165", url = "/punch-165"),
            ),
            remoteLastRead = 225f,
        )

        assertEquals(setOf(225f, 224.5f, 224f, 223f, 167f, 166f, 165f), result.coveredChapterNumbers)
        assertEquals(
            listOf(
                "Mag Version 225",
                "ReDraw 224.5",
                "Mag Version 224",
                "Mag Version 223",
                "Punch 167",
                "Punch 166",
                "Official Scans 165",
                "Punch 165",
            ),
            result.chapterUpdates.map { it.name },
        )
    }

    private fun chapter(
        number: Float,
        sourceOrder: Int,
        read: Boolean,
        name: String = "Chapter $number",
        url: String = "/$number",
    ): Chapter {
        return Chapter.create().apply {
            id = (sourceOrder + 1).toLong()
            manga_id = 1L
            chapter_number = number
            source_order = sourceOrder
            this.read = read
            bookmark = false
            last_page_read = 0
            pages_left = 0
            date_fetch = 0L
            this.name = name
            this.url = url
        }
    }
}
