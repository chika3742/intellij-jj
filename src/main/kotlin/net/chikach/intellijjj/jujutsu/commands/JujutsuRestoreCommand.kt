package net.chikach.intellijjj.jujutsu.commands

import com.intellij.openapi.vfs.VirtualFile
import net.chikach.intellijjj.jujutsu.JujutsuCommandExecutor

/**
 * Wrapper for `jj restore` commands.
 */
class JujutsuRestoreCommand(commandExecutor: JujutsuCommandExecutor) : JujutsuCommand(commandExecutor) {
    /**
     * Restores the given root-relative paths in the working copy.
     */
    fun restore(root: VirtualFile, paths: List<String>) {
        if (paths.isEmpty()) return
        val args = mutableListOf("restore", "--quiet", "--color=never", "--")
        args.addAll(paths)
        execute(root, args)
    }
}
