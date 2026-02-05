package net.chikach.intellijjj.commands

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

class JujutsuCommandExecutor(private val project: Project) {
    
    companion object {
        private const val PROCESS_TIMEOUT_MS = 30000 // 30 second timeout
    }
    
    fun execute(workingDir: VirtualFile, vararg args: String): ProcessOutput {
        val commandLine = GeneralCommandLine("jj")
            .withWorkDirectory(workingDir.path)
            .withCharset(StandardCharsets.UTF_8)
        
        args.forEach { commandLine.addParameter(it) }
        
        val handler = CapturingProcessHandler(commandLine)
        
        // Check if we're on EDT - if so, we need to run in background
        return if (ApplicationManager.getApplication().isDispatchThread) {
            // Use ProgressManager to run the process in a background thread
            // This prevents EDT violations while still providing progress indication
            val result = AtomicReference<ProcessOutput>()
            val commandDescription = if (args.isNotEmpty()) "Running jj ${args[0]}" else "Running jj command"
            ProgressManager.getInstance().runProcessWithProgressSynchronously(
                {
                    result.set(handler.runProcess(PROCESS_TIMEOUT_MS))
                },
                commandDescription,
                true,  // canBeCanceled
                project
            )
            result.get() ?: ProcessOutput("", "Process was cancelled or failed to execute", 1, false, false)
        } else {
            // Already on background thread, execute directly with explicit timeout
            handler.runProcess(PROCESS_TIMEOUT_MS)
        }
    }
    
    fun executeAndCheck(workingDir: VirtualFile, vararg args: String): String {
        val output = execute(workingDir, *args)
        
        if (output.exitCode != 0) {
            throw VcsException("jj command failed: ${output.stderr}")
        }
        
        return output.stdout
    }
    
    fun newChange(workingDir: VirtualFile, message: String? = null): String {
        val args = mutableListOf("new")
        if (message != null) {
            args.add("-m")
            args.add(message)
        }
        return executeAndCheck(workingDir, *args.toTypedArray())
    }
    
    fun editChange(workingDir: VirtualFile, changeId: String): String {
        return executeAndCheck(workingDir, "edit", changeId)
    }
    
    fun splitChange(workingDir: VirtualFile): String {
        return executeAndCheck(workingDir, "split")
    }
    
    fun squashChange(workingDir: VirtualFile): String {
        return executeAndCheck(workingDir, "squash")
    }
    
    fun mergeChanges(workingDir: VirtualFile, vararg changeIds: String): String {
        val args = mutableListOf("merge")
        args.addAll(changeIds)
        return executeAndCheck(workingDir, *args.toTypedArray())
    }
    
    fun getLog(workingDir: VirtualFile, limit: Int = 100): String {
        return executeAndCheck(workingDir, "log", "-l", limit.toString())
    }
    
    fun createBookmark(workingDir: VirtualFile, name: String, revision: String? = null): String {
        val args = mutableListOf("bookmark", "create", name)
        if (revision != null) {
            args.add("-r")
            args.add(revision)
        }
        return executeAndCheck(workingDir, *args.toTypedArray())
    }
    
    fun listBookmarks(workingDir: VirtualFile): String {
        return executeAndCheck(workingDir, "bookmark", "list")
    }
    
    fun deleteBookmark(workingDir: VirtualFile, name: String): String {
        return executeAndCheck(workingDir, "bookmark", "delete", name)
    }
    
    fun getCurrentChange(workingDir: VirtualFile): String {
        return executeAndCheck(workingDir, "log", "-r", "@", "-T", "change_id")
    }
}
