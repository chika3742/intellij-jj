package net.chikach.intellijjj.jujutsu.commands

import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.json.Json
import net.chikach.intellijjj.jujutsu.JujutsuCommandExecutor
import net.chikach.intellijjj.jujutsu.Revset
import net.chikach.intellijjj.jujutsu.models.JujutsuCommit

/**
 * Wrapper for `jj log` queries used by change, diff, and VCS log integrations.
 */
class JujutsuLogCommand(commandExecutor: JujutsuCommandExecutor) : JujutsuCommand(commandExecutor) {
    private fun execute(
        root: VirtualFile,
        template: String,
        revset: Revset?,
        limit: Int?,
    ): String {
        val args = mutableListOf("log", "--quiet", "--no-graph", "--color=never")
        if (revset != null) {
            args.add("-r")
            args.add(revset.stringify())
        }
        if (limit != null && limit > 0) {
            args.add("-n")
            args.add(limit.toString())
        }
        args.add("-T")
        args.add(template)
        return execute(root, args)
    }
    
    /**
     * Executes `jj log` with an arbitrary template and optional revset/limit.
     */
    private fun executeWithTemplate(
        root: VirtualFile,
        template: String,
        revset: Revset? = null,
        limit: Int? = null,
    ): String {
        return execute(root, template, revset, limit)
    }

    /**
     * Decodes line-delimited JSON commits emitted by [JujutsuCommit.TEMPLATE].
     */
    fun getCommits(root: VirtualFile, revset: Revset? = null, limit: Int? = null): List<JujutsuCommit> {
        val result = executeWithTemplate(
            root,
            JujutsuCommit.TEMPLATE,
            Revset.connected(revset ?: Revset.rangeWithRoot()),
            limit,
        ).trim()
        
        if (result.isEmpty()) {
            return emptyList()
        }
        return result.lines().map { Json.decodeFromString<JujutsuCommit>(it.trim()) }
    }

    /**
     * Reads bookmark names for commits selected by a revset.
     */
    fun getBookmarks(
        root: VirtualFile,
        revset: Revset,
        limit: Int? = null,
    ): Set<String> {
        val output = executeWithTemplate(root, "bookmarks ++ \"\\n\"", revset, limit)
        return output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
