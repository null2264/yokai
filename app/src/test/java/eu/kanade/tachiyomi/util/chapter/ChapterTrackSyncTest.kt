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

        assertEquals(listOf(10f, 8f, 7f), result.chapterUpdates.map { it.chapter_number })
    }

    @Test
    fun `hidden chapters in filtered view would be missed by tracker sync`() {
        val fullResult = calculateTwoWayTrackerSync(
            chapters = listOf(
                chapter(number = 3f, sourceOrder = 0, read = false),
                chapter(number = 2f, sourceOrder = 1, read = false),
                chapter(number = 1f, sourceOrder = 2, read = false),
            ),
            remoteLastRead = 3f,
        )
        val filteredResult = calculateTwoWayTrackerSync(
            chapters = listOf(
                chapter(number = 3f, sourceOrder = 0, read = false),
                chapter(number = 1f, sourceOrder = 2, read = false),
            ),
            remoteLastRead = 3f,
        )

        assertEquals(listOf(3f, 2f, 1f), fullResult.chapterUpdates.map { it.chapter_number })
        assertEquals(listOf(3f, 1f), filteredResult.chapterUpdates.map { it.chapter_number })
    }

    @Test
    fun `filtered chapter list understates local continuous progress`() {
        val fullResult = calculateTwoWayTrackerSync(
            chapters = listOf(
                chapter(number = 4f, sourceOrder = 0, read = true),
                chapter(number = 3f, sourceOrder = 1, read = true),
                chapter(number = 2f, sourceOrder = 2, read = true),
                chapter(number = 1f, sourceOrder = 3, read = true),
            ),
            remoteLastRead = 0f,
        )
        val filteredResult = calculateTwoWayTrackerSync(
            chapters = listOf(
                chapter(number = 4f, sourceOrder = 0, read = true),
                chapter(number = 1f, sourceOrder = 3, read = true),
            ),
            remoteLastRead = 0f,
        )

        assertEquals(4f, fullResult.localLastRead)
        assertEquals(1f, filteredResult.localLastRead)
    }

    private fun chapter(number: Float, sourceOrder: Int, read: Boolean): Chapter {
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
            name = "Chapter $number"
            url = "/$number"
        }
    }
}
