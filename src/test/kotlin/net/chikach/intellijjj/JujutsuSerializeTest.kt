package net.chikach.intellijjj

import kotlinx.serialization.json.Json
import net.chikach.intellijjj.jujutsu.Pattern
import net.chikach.intellijjj.jujutsu.models.JujutsuCommit
import kotlin.test.Test
import kotlin.test.assertEquals

class JujutsuSerializeTest {
    @Test
    fun canSerializeCommit() {
        val process = ProcessBuilder("jj", "log", "--no-graph", "--color=never", "-T", JujutsuCommit.TEMPLATE)
            .redirectErrorStream(true)
            .start()
        
        val output = process.inputStream.bufferedReader().readLines()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw RuntimeException("jj command failed: ${output.joinToString("\n")}")
        }
        
        output.forEach {
            println(Json.decodeFromString<JujutsuCommit>(it))
        }
    }
    
    @Test
    fun canSerializeNoCommit() {
        val process = ProcessBuilder("jj", "log", "--no-graph", "--color=never", "-r", "commit_id(abc)", "-T", JujutsuCommit.TEMPLATE)
            .redirectErrorStream(true)
            .start()
        
        val output = process.inputStream.bufferedReader().readLines()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw RuntimeException("jj command failed: ${output.joinToString("\n")}")
        }
        
        output.forEach {
            println(Json.decodeFromString<JujutsuCommit>(it))
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
        assertEquals( "root-file:\"src/main/kotlin\" | root-file:\"src/main/java\"", pattern.stringify())
    }
}