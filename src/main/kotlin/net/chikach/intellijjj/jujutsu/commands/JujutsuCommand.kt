package net.chikach.intellijjj.jujutsu.commands

import com.intellij.openapi.vfs.VirtualFile
import net.chikach.intellijjj.jujutsu.JujutsuCommandExecutor

abstract class JujutsuCommand(private val commandExecutor: JujutsuCommandExecutor) {
    protected fun execute(root: VirtualFile, args: Collection<String>): String {
        return commandExecutor.executeAndCheck(root, *args.toTypedArray())
    }
}