package net.chikach.intellijjj

import net.chikach.intellijjj.jujutsu.JujutsuDiffParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class JujutsuDiffParserTest {
    @Test
    fun parsesAddedLine() {
        val entry = JujutsuDiffParser.parseSummaryLine("A foo.txt")
        requireNotNull(entry)
        assertEquals('A', entry.status)
        assertNull(entry.beforePath)
        assertEquals("foo.txt", entry.afterPath)
    }

    @Test
    fun parsesDeletedLine() {
        val entry = JujutsuDiffParser.parseSummaryLine("D bar.txt")
        requireNotNull(entry)
        assertEquals('D', entry.status)
        assertEquals("bar.txt", entry.beforePath)
        assertNull(entry.afterPath)
    }

    @Test
    fun parsesModifiedLine() {
        val entry = JujutsuDiffParser.parseSummaryLine("M src/Main.kt")
        requireNotNull(entry)
        assertEquals('M', entry.status)
        assertEquals("src/Main.kt", entry.beforePath)
        assertEquals("src/Main.kt", entry.afterPath)
    }

    @Test
    fun parsesRenameLineWithBraces() {
        val entry = JujutsuDiffParser.parseSummaryLine("R src/{Old => New}.kt")
        requireNotNull(entry)
        assertEquals('R', entry.status)
        assertEquals("src/Old.kt", entry.beforePath)
        assertEquals("src/New.kt", entry.afterPath)
    }

    @Test
    fun parsesRenameLineWithArrow() {
        val entry = JujutsuDiffParser.parseSummaryLine("R old/path -> new/path")
        requireNotNull(entry)
        assertEquals('R', entry.status)
        assertEquals("old/path", entry.beforePath)
        assertEquals("new/path", entry.afterPath)
    }

    @Test
    fun parsesCopyLineWithBraces() {
        val entry = JujutsuDiffParser.parseSummaryLine("C src/{Old => New}.kt")
        requireNotNull(entry)
        assertEquals('C', entry.status)
        assertNull(entry.beforePath)
        assertEquals("src/New.kt", entry.afterPath)
    }

    @Test
    fun returnsNullForNonSummaryLine() {
        assertNull(JujutsuDiffParser.parseSummaryLine("not a summary line"))
        assertNull(JujutsuDiffParser.parseSummaryLine("A"))
    }

    @Test
    fun throwsOnUnexpectedStatus() {
        assertFailsWith<IllegalArgumentException> {
            JujutsuDiffParser.parseSummaryLine("X nope.txt")
        }
    }
}
