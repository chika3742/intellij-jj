package net.chikach.intellijjj.jujutsu.commands

import com.intellij.openapi.vfs.VirtualFile
import net.chikach.intellijjj.jujutsu.JujutsuCommandExecutor
import net.chikach.intellijjj.jujutsu.Revset

/**
 * Wrapper for `jj diff` commands.
 */
class JujutsuDiffCommand(commandExecutor: JujutsuCommandExecutor) : JujutsuCommand(commandExecutor) {
    /**
     * Returns `jj diff --summary` output for the given revset.
     */
    fun getSummary(root: VirtualFile, revset: Revset): String {
        return execute(
            root,
            listOf(
                "diff",
                "--summary",
                "--color=never",
                "-r",
                revset.stringify(),
            ),
        )
    }
}
