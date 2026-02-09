package net.chikach.intellijjj.jujutsu.commands

import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.json.Json
import net.chikach.intellijjj.jujutsu.JujutsuCommandExecutor
import net.chikach.intellijjj.jujutsu.Revset
import net.chikach.intellijjj.jujutsu.models.JujutsuCommit

/**
 * Wrapper for `jj show` queries targeting a single revision.
 */
class JujutsuShowCommand(commandExecutor: JujutsuCommandExecutor) : JujutsuCommand(commandExecutor) {
    private fun execute(
        root: VirtualFile,
        template: String,
        revset: Revset?,
    ): String {
        val args = mutableListOf("show", "--quiet", "--color=never", "--no-patch")
        args.add("-T")
        args.add(template)
        if (revset != null) {
            args.add(revset.stringify())
        }
        return execute(root, args)
    }

    /**
     * Executes `jj show` with an arbitrary template and optional revset.
     */
    private fun executeWithTemplate(
        root: VirtualFile,
        template: String,
        revset: Revset? = null,
    ): String {
        return execute(root, template, revset)
    }

    /**
     * Decodes a commit from template output for a single revision query.
     */
    fun getCommit(root: VirtualFile, revset: Revset? = null): JujutsuCommit? {
        val result = executeWithTemplate(root, JujutsuCommit.TEMPLATE, revset).trim()
        if (result.isEmpty()) return null
        val firstLine = result.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        return Json.decodeFromString<JujutsuCommit>(firstLine)
    }

    /**
     * Reads a commit ID from a single revision.
     */
    private fun getCommitId(
        root: VirtualFile,
        revset: Revset? = null,
    ): String? {
        val output = executeWithTemplate(root, "commit_id", revset)
        return output.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
    }

    /**
     * Reads the working-copy (`@`) commit ID.
     */
    fun getWorkingCopyCommitId(root: VirtualFile): String? {
        return getCommitId(root)
    }

    /**
     * Reads commit description text from a single revision.
     */
    fun getDescription(root: VirtualFile, revset: Revset? = null): String? {
        val description = executeWithTemplate(root, "description", revset).trimEnd()
        return description.ifBlank { null }
    }
}
