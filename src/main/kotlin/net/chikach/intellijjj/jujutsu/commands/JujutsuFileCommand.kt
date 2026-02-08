package net.chikach.intellijjj.jujutsu.commands

import com.intellij.openapi.vfs.VirtualFile
import net.chikach.intellijjj.jujutsu.JujutsuCommandExecutor
import net.chikach.intellijjj.jujutsu.Revset

/**
 * Wrapper for `jj file` commands.
 */
class JujutsuFileCommand(commandExecutor: JujutsuCommandExecutor) : JujutsuCommand(commandExecutor) {
    private fun execute(
        root: VirtualFile,
        subcommand: String,
        vararg args: String,
    ): String {
        val args = mutableListOf("file", subcommand, "--quiet", "--color=never", *args)
        return execute(root, args)
    }

    /**
     * Get the contents of files at the specified revision.
     */
    fun getContents(root: VirtualFile, filePath: String, revset: Revset? = null): String {
        val args = mutableListOf<String>()
        if (revset != null) {
            args.add("-r")
            args.add(revset.stringify())
        }
        args.add(filePath)
        return execute(root, "show", *args.toTypedArray())
    }
}
