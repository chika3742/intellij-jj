package net.chikach.intellijjj

import kotlinx.serialization.json.Json
import net.chikach.intellijjj.jujutsu.Pattern
import net.chikach.intellijjj.jujutsu.models.JujutsuCommit
import net.chikach.intellijjj.testutil.JjTestRepo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JujutsuSerializeTest {
    @Test
    fun canSerializeCommit() {
        if (!JjTestRepo.isAvailable()) return
        JjTestRepo.init().use { repo ->
            repo.writeFile("hello.txt", "hello")
            repo.commit("init")
            val output = repo.logJsonLines(JujutsuCommit.TEMPLATE)
            val commits = output.map { Json.decodeFromString<JujutsuCommit>(it) }
            assertTrue(commits.isNotEmpty())
            assertTrue(commits.any { it.commitId.isNotBlank() })
        }
    }
    
    @Test
    fun canSerializeNoCommit() {
        if (!JjTestRepo.isAvailable()) return
        JjTestRepo.init().use { repo ->
            val output = repo.logJsonLines(JujutsuCommit.TEMPLATE, "commit_id(abc)")
            assertTrue(output.isEmpty())
        }
    }
    
    @Test
    fun canStringifyRevsetPattern() {
        val pattern = Pattern.or(
            listOf(
                Pattern.root("src/main/kotlin"),
                Pattern.root("src/main/java"),
            )
        )
        assertEquals( "root:\"src/main/kotlin\" | root:\"src/main/java\"", pattern.stringify())
    }
}
