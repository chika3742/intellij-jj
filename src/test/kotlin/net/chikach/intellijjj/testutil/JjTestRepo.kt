package net.chikach.intellijjj.testutil

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import java.util.concurrent.TimeUnit

internal data class JjCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

internal class JjTestRepo private constructor(
    val root: Path,
) : AutoCloseable {
    companion object {
        fun isAvailable(): Boolean {
            return try {
                val process = ProcessBuilder("jj", "--version")
                    .redirectErrorStream(true)
                    .start()
                val finished = process.waitFor(5, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    false
                } else {
                    process.exitValue() == 0
                }
            } catch (e: Exception) {
                false
            }
        }

        fun init(): JjTestRepo {
            val dir = Files.createTempDirectory("jj-test-")
            val repo = JjTestRepo(dir)
            repo.runJjChecked("git", "init")
            repo.runJjChecked("config", "set", "--repo", "user.name", "Test User")
            repo.runJjChecked("config", "set", "--repo", "user.email", "test@example.com")
            return repo
        }
    }

    fun writeFile(relativePath: String, content: String) {
        val path = root.resolve(relativePath)
        val parent = path.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }
        Files.writeString(path, content, StandardCharsets.UTF_8)
    }

    fun deleteFile(relativePath: String) {
        val path = root.resolve(relativePath)
        Files.deleteIfExists(path)
    }

    fun moveFile(fromRelative: String, toRelative: String) {
        val source = root.resolve(fromRelative)
        val target = root.resolve(toRelative)
        val parent = target.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
    }

    fun copyFile(fromRelative: String, toRelative: String) {
        val source = root.resolve(fromRelative)
        val target = root.resolve(toRelative)
        val parent = target.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    }

    fun commit(message: String, vararg paths: String) {
        val args = mutableListOf("commit", "-m", message)
        if (paths.isNotEmpty()) {
            args.add("--")
            args.addAll(paths)
        }
        runJjChecked(*args.toTypedArray())
    }

    fun diffSummaryLines(): List<String> {
        val result = runJjChecked("diff", "--summary", "--color=never")
        return result.stdout.lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotEmpty() }
            .toList()
    }

    fun logJsonLines(template: String, revset: String? = null, limit: Int? = null): List<String> {
        val args = mutableListOf("log", "--no-graph", "--color=never")
        if (revset != null) {
            args.add("-r")
            args.add(revset)
        }
        if (limit != null && limit > 0) {
            args.add("-n")
            args.add(limit.toString())
        }
        args.add("-T")
        args.add(template)
        val result = runJjChecked(*args.toTypedArray())
        return result.stdout.lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotEmpty() }
            .toList()
    }

    fun runJjChecked(vararg args: String): JjCommandResult {
        val result = runJj(*args)
        if (result.exitCode != 0) {
            val message = buildString {
                append("jj command failed: ")
                append(args.joinToString(" "))
                if (result.stderr.isNotBlank()) {
                    append("\n")
                    append(result.stderr)
                }
            }
            throw IllegalStateException(message)
        }
        return result
    }

    fun runJj(vararg args: String): JjCommandResult {
        val process = ProcessBuilder(listOf("jj") + args)
            .directory(root.toFile())
            .redirectErrorStream(false)
            .start()
        val exitCode = process.waitFor()
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        return JjCommandResult(exitCode, stdout, stderr)
    }

    override fun close() {
        deleteRecursively(root)
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .forEach { Files.deleteIfExists(it) }
    }
}
