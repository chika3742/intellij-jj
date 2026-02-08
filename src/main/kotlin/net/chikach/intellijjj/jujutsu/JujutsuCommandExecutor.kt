package net.chikach.intellijjj.jujutsu

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import net.chikach.intellijjj.jujutsu.commands.JujutsuCommitCommand
import net.chikach.intellijjj.jujutsu.commands.JujutsuConfigCommand
import net.chikach.intellijjj.jujutsu.commands.JujutsuDiffCommand
import net.chikach.intellijjj.jujutsu.commands.JujutsuFileCommand
import net.chikach.intellijjj.jujutsu.commands.JujutsuLogCommand
import java.nio.charset.StandardCharsets

/**
 * Central boundary for invoking the `jj` CLI.
 *
 * Calls are executed with UTF-8 and a fixed timeout. When called from EDT,
 * execution is wrapped in modal progress to avoid blocking the UI without
 * feedback.
 */
class JujutsuCommandExecutor(private val project: Project) {
    companion object {
        private const val EXEC_TIMEOUT = 30000 // 30 seconds
    }

    private val log = logger<JujutsuCommandExecutor>()
    val logCommand = JujutsuLogCommand(this)
    val fileCommand = JujutsuFileCommand(this)
    val diffCommand = JujutsuDiffCommand(this)
    val configCommand = JujutsuConfigCommand(this)
    val commitCommand = JujutsuCommitCommand(this)
    
    /**
     * Executes `jj` in [workingDir] with the provided arguments.
     */
    fun execute(workingDir: VirtualFile, vararg args: String): ProcessOutput {
        val commandLine = GeneralCommandLine("jj")
            .withWorkDirectory(workingDir.path)
            .withCharset(StandardCharsets.UTF_8)
        
        args.forEach { commandLine.addParameter(it) }
        
        val handler = CapturingProcessHandler(commandLine)
        
        // Check if we're on EDT
        return if (ApplicationManager.getApplication().isDispatchThread) {
            runWithModalProgressBlocking(project, "Executing jj command") {
                handler.runProcess(EXEC_TIMEOUT)
            }
        } else {
            // Already on background thread, execute directly with explicit timeout
            handler.runProcess(EXEC_TIMEOUT) // 30 second timeout
        }
    }
    
    /**
     * Executes `jj` and returns stdout, throwing [VcsException] on non-zero exit.
     */
    fun executeAndCheck(workingDir: VirtualFile, vararg args: String): String {
        val output = execute(workingDir, *args)
        
        if (output.exitCode != 0) {
            log.error(output.stderr)
            throw VcsException("jj command failed: ${output.stderr}")
        }
        
        return output.stdout
    }
}
