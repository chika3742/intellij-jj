package net.chikach.intellijjj.git

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Reads ignored paths using git (for Jujutsu repos backed by Git).
 */
class GitIgnoreScanner(project: Project) {
    private val log = logger<GitIgnoreScanner>()
    private val executor = GitCommandExecutor(project)

    fun listIgnoredPaths(root: VirtualFile): List<String> {
        val output = executor.executeAndCheck(
            root,
            "ls-files",
            "-i",
            "-o",
            "--exclude-standard",
            "--exclude=.jj/",
            "-z",
        )
        if (output.exitCode != 0) return emptyList()
        return parseZeroSeparated(output.stdout)
    }

    companion object {
        internal fun parseZeroSeparated(output: String): List<String> {
            if (output.isEmpty()) return emptyList()
            return output.split('\u0000')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
    }
}
