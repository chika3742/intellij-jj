package net.chikach.intellijjj.commands

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import java.nio.charset.StandardCharsets

class JujutsuCommandExecutor(private val project: Project) {
    companion object {
        private const val EXEC_TIMEOUT = 30000 // 30 seconds
    }

    val log = logger<JujutsuCommandExecutor>()
    val logCommand = JujutsuLogCommand(this)
    
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
    
    fun executeAndCheck(workingDir: VirtualFile, vararg args: String): String {
        val output = execute(workingDir, *args)
        
        if (output.exitCode != 0) {
            log.error(output.stderr)
            throw VcsException("jj command failed: ${output.stderr}")
        }
        
        return output.stdout
    }
}
