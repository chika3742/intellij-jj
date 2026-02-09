package net.chikach.intellijjj.git

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import java.nio.charset.StandardCharsets

/**
 * Executes git commands needed by the Jujutsu plugin.
 */
class GitCommandExecutor(private val project: Project) {
    companion object {
        private const val EXEC_TIMEOUT = 30000 // 30 seconds
    }

    private val log = logger<GitCommandExecutor>()

    fun execute(workingDir: VirtualFile, vararg args: String): ProcessOutput {
        val commandLine = GeneralCommandLine("git")
            .withWorkDirectory(workingDir.path)
            .withCharset(StandardCharsets.UTF_8)

        args.forEach { commandLine.addParameter(it) }
        val handler = CapturingProcessHandler(commandLine)

        return if (ApplicationManager.getApplication().isDispatchThread) {
            runWithModalProgressBlocking(project, "Executing git command") {
                handler.runProcess(EXEC_TIMEOUT)
            }
        } else {
            handler.runProcess(EXEC_TIMEOUT)
        }
    }

    fun executeAndCheck(workingDir: VirtualFile, vararg args: String): ProcessOutput {
        val output = execute(workingDir, *args)
        if (output.exitCode != 0) {
            log.debug("git command failed: ${args.joinToString(" ")}\n${output.stderr}")
        }
        return output
    }
}
