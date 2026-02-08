package net.chikach.intellijjj.jujutsu.commands

import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.vfs.VirtualFile
import net.chikach.intellijjj.jujutsu.JujutsuCommandExecutor

/**
 * Wrapper for `jj commit` commands.
 */
class JujutsuCommitCommand(commandExecutor: JujutsuCommandExecutor) : JujutsuCommand(commandExecutor) {
    /**
     * Executes `jj commit -m <message> [-- <paths...>]`.
     */
    fun commit(root: VirtualFile, message: String, paths: List<String>): ProcessOutput {
        val args = mutableListOf("commit", "-m", message)
        if (paths.isNotEmpty()) {
            args.add("--")
            args.addAll(paths)
        }
        return executeWithOutput(root, args)
    }
}
