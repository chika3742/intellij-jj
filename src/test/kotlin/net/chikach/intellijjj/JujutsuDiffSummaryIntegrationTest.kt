package net.chikach.intellijjj

import net.chikach.intellijjj.jujutsu.JujutsuDiffParser
import net.chikach.intellijjj.testutil.JjTestRepo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JujutsuDiffSummaryIntegrationTest {
    @Test
    fun parsesSummaryFromWorkingCopyDiff() {
        if (!JjTestRepo.isAvailable()) return
        JjTestRepo.init().use { repo ->
            repo.writeFile("file.txt", "base")
            repo.writeFile("remove.txt", "remove-me")
            repo.commit("base")

            repo.writeFile("file.txt", "modified")
            repo.writeFile("added.txt", "added")
            repo.deleteFile("remove.txt")

            val entries = repo.diffSummaryLines()
                .mapNotNull { JujutsuDiffParser.parseSummaryLine(it) }

            val statuses = entries.map { it.status to (it.afterPath ?: it.beforePath) }.toSet()
            assertTrue(statuses.contains('M' to "file.txt"), "Expected modified file in summary")
            assertTrue(statuses.contains('A' to "added.txt"), "Expected added file in summary")
            assertTrue(statuses.contains('D' to "remove.txt"), "Expected deleted file in summary")
            assertEquals(3, entries.size)
        }
    }
}
