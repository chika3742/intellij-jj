package net.chikach.intellijjj.git

import kotlin.test.Test
import kotlin.test.assertEquals

class GitIgnoreScannerTest {
    @Test
    fun parseZeroSeparatedHandlesEmptyAndTrailing() {
        val input = "a\u0000b\u0000"
        val parsed = GitIgnoreScanner.parseZeroSeparated(input)
        assertEquals(listOf("a", "b"), parsed)
    }
}
