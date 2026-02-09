package net.chikach.intellijjj.jujutsu.commands

import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.json.Json
import net.chikach.intellijjj.jujutsu.JujutsuCommandExecutor
import net.chikach.intellijjj.jujutsu.Revset
import net.chikach.intellijjj.jujutsu.models.JujutsuBookmark

/**
 * Wrapper for `jj bookmark` commands.
 */
class JujutsuBookmarkCommand(commandExecutor: JujutsuCommandExecutor) : JujutsuCommand(commandExecutor) {
    private fun execute(
        root: VirtualFile,
        subcommand: String,
        vararg args: String,
    ): String {
        val commandArgs = mutableListOf("bookmark", subcommand, "--quiet", "--color=never", *args)
        return execute(root, commandArgs)
    }

    /**
     * Reads bookmarks, optionally filtered by target revisions.
     */
    fun getBookmarkRefs(root: VirtualFile, revset: Revset? = null): List<JujutsuBookmark> {
        val args = mutableListOf<String>()
        if (revset != null) {
            args.add("-r")
            args.add(revset.stringify())
        }
        args.add("-T")
        args.add(JujutsuBookmark.TEMPLATE)
        val output = execute(root, "list", *args.toTypedArray()).trim()
        if (output.isEmpty()) return emptyList()
        return output.lineSequence()
            .filter { it.trim().isNotEmpty() }
            .map { Json.decodeFromString<JujutsuBookmark>(it) }
            .toList()
    }

    /**
     * Reads bookmark names, optionally filtered by target revisions.
     */
    fun getBookmarks(root: VirtualFile, revset: Revset? = null): Set<String> {
        return getBookmarkRefs(root, revset)
            .map { it.name }
            .toSet()
    }
}
