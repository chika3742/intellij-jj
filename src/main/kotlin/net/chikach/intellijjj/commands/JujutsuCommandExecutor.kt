package net.chikach.intellijjj.commands

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import java.nio.charset.StandardCharsets

class JujutsuCommandExecutor(private val project: Project) {
    
    fun execute(workingDir: FilePath, vararg args: String): ProcessOutput {
        val commandLine = GeneralCommandLine("jj")
            .withWorkDirectory(workingDir.path)
            .withCharset(StandardCharsets.UTF_8)
        
        args.forEach { commandLine.addParameter(it) }
        
        val handler = CapturingProcessHandler(commandLine)
        return handler.runProcess(30000) // 30 second timeout
    }
    
    fun executeAndCheck(workingDir: FilePath, vararg args: String): String {
        val output = execute(workingDir, *args)
        
        if (output.exitCode != 0) {
            throw VcsException("jj command failed: ${output.stderr}")
        }
        
        return output.stdout
    }
    
    fun newChange(workingDir: FilePath, message: String? = null): String {
        val args = mutableListOf("new")
        if (message != null) {
            args.add("-m")
            args.add(message)
        }
        return executeAndCheck(workingDir, *args.toTypedArray())
    }
    
    fun editChange(workingDir: FilePath, changeId: String): String {
        return executeAndCheck(workingDir, "edit", changeId)
    }
    
    fun splitChange(workingDir: FilePath): String {
        return executeAndCheck(workingDir, "split")
    }
    
    fun squashChange(workingDir: FilePath): String {
        return executeAndCheck(workingDir, "squash")
    }
    
    fun mergeChanges(workingDir: FilePath, vararg changeIds: String): String {
        val args = mutableListOf("merge")
        args.addAll(changeIds)
        return executeAndCheck(workingDir, *args.toTypedArray())
    }
    
    fun getLog(workingDir: FilePath, limit: Int = 100): String {
        return executeAndCheck(workingDir, "log", "-l", limit.toString())
    }
    
    fun createBookmark(workingDir: FilePath, name: String, revision: String? = null): String {
        val args = mutableListOf("bookmark", "create", name)
        if (revision != null) {
            args.add("-r")
            args.add(revision)
        }
        return executeAndCheck(workingDir, *args.toTypedArray())
    }
    
    fun listBookmarks(workingDir: FilePath): String {
        return executeAndCheck(workingDir, "bookmark", "list")
    }
    
    fun deleteBookmark(workingDir: FilePath, name: String): String {
        return executeAndCheck(workingDir, "bookmark", "delete", name)
    }
    
    fun getCurrentChange(workingDir: FilePath): String {
        return executeAndCheck(workingDir, "log", "-r", "@", "-T", "change_id")
    }
}
