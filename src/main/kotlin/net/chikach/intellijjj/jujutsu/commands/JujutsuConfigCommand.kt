package net.chikach.intellijjj.jujutsu.commands

import com.intellij.openapi.vfs.VirtualFile
import net.chikach.intellijjj.jujutsu.JujutsuCommandExecutor

/**
 * Wrapper for `jj config` commands.
 */
class JujutsuConfigCommand(commandExecutor: JujutsuCommandExecutor) : JujutsuCommand(commandExecutor) {
    /**
     * Reads a config value with `jj config get`.
     *
     * Returns `null` when the key is missing or the command fails.
     */
    fun getValue(root: VirtualFile, key: String): String? {
        val output = executeWithOutput(root, listOf("config", "get", key))
        if (output.exitCode != 0) return null
        return output.stdout.trim().ifEmpty { null }
    }
}
