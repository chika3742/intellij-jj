package net.chikach.intellijjj.jujutsu.commands

import com.intellij.openapi.vfs.VirtualFile
import net.chikach.intellijjj.jujutsu.JujutsuCommandExecutor

/**
 * Base command wrapper for `jj` subcommands.
 */
abstract class JujutsuCommand(private val commandExecutor: JujutsuCommandExecutor) {
    /**
     * Executes a prepared argument list and returns stdout.
     */
    protected fun execute(root: VirtualFile, args: Collection<String>): String {
        return commandExecutor.executeAndCheck(root, *args.toTypedArray())
    }
}
