// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class ClipboardHistoryEntryTest {
    @Test
    fun pinnedEntriesSortFirstThenNewest() {
        val entries = listOf(
            entry(id = 1, timestamp = 100, pinned = false),
            entry(id = 2, timestamp = 200, pinned = true),
            entry(id = 3, timestamp = 300, pinned = false),
            entry(id = 4, timestamp = 400, pinned = true),
        )

        val sortedIds = entries.sortedWith(ClipboardHistoryEntry.comparator(pinnedFirst = true)).map { it.id }

        assertEquals(listOf(4L, 2L, 3L, 1L), sortedIds)
    }

    @Test
    fun unpinnedEntriesCanSortBeforePinnedEntries() {
        val entries = listOf(
            entry(id = 1, timestamp = 100, pinned = false),
            entry(id = 2, timestamp = 200, pinned = true),
            entry(id = 3, timestamp = 300, pinned = false),
            entry(id = 4, timestamp = 400, pinned = true),
        )

        val sortedIds = entries.sortedWith(ClipboardHistoryEntry.comparator(pinnedFirst = false)).map { it.id }

        assertEquals(listOf(3L, 1L, 4L, 2L), sortedIds)
    }

    @Test
    fun pinToggleProducesANewEntryWithoutMutatingThePreviousSnapshot() {
        val original = entry(id = 1, timestamp = 100, pinned = false)
        val oldSnapshot = listOf(original)
        val updated = original.copy(timeStamp = 200, isPinned = true)
        val newSnapshot = listOf(updated)

        assertNotSame(oldSnapshot.single(), newSnapshot.single())
        assertFalse(oldSnapshot.single().isPinned)
        assertEquals(100, oldSnapshot.single().timeStamp)
        assertTrue(newSnapshot.single().isPinned)
        assertEquals(200, newSnapshot.single().timeStamp)
    }

    @Test
    fun equalTimestampsHaveDeterministicIdOrder() {
        val entries = listOf(
            entry(id = 7, timestamp = 100, pinned = false),
            entry(id = 9, timestamp = 100, pinned = false),
            entry(id = 8, timestamp = 100, pinned = false),
        )

        val sortedIds = entries.sortedWith(ClipboardHistoryEntry.comparator(pinnedFirst = true)).map { it.id }

        assertEquals(listOf(9L, 8L, 7L), sortedIds)
    }

    private fun entry(id: Long, timestamp: Long, pinned: Boolean) = ClipboardHistoryEntry(
        id = id,
        timeStamp = timestamp,
        isPinned = pinned,
        text = "entry $id",
        filename = null,
        mimeTypes = null,
    )
}
