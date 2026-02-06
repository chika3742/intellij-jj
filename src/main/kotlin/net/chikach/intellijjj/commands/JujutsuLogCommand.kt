package net.chikach.intellijjj.commands

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.json.Json
import net.chikach.intellijjj.jujutsu.models.JujutsuCommit

class JujutsuLogCommand(private val commandExecutor: JujutsuCommandExecutor) {
    val log = logger<JujutsuLogCommand>()
    
    private fun execute(
        root: VirtualFile,
        template: String,
        revset: Revset?,
        limit: Int?,
        noGraph: Boolean,
        noColor: Boolean,
    ): String {
        val args = mutableListOf("log", "--quiet")
        if (noGraph) {
            args.add("--no-graph")
        }
        if (noColor) {
            args.add("--color=never")
        }
        if (revset != null) {
            args.add("-r")
            args.add(revset.stringify())
        }
        if (limit != null && limit > 0) {
            args.add("-n")
            args.add(limit.toString())
        }
        args.add("-T")
        args.add(template)
        return commandExecutor.executeAndCheck(root, *args.toTypedArray())
    }
    
    fun executeWithTemplate(
        root: VirtualFile,
        template: String,
        revset: Revset? = null,
        limit: Int? = null,
        noGraph: Boolean = true,
        noColor: Boolean = true
    ): String {
        return execute(root, template, revset, limit, noGraph, noColor)
    }

    fun getCommits(root: VirtualFile, revset: Revset? = null, limit: Int? = null): List<JujutsuCommit> {
        val result = executeWithTemplate(
            root,
            JujutsuCommit.TEMPLATE,
            revset,
            limit,
        ).trim()
        
        if (result.isEmpty()) {
            return emptyList()
        }
        return result.lines().map { Json.decodeFromString<JujutsuCommit>(it.trim()) }
    }

    fun readFirstNonBlankLine(
        root: VirtualFile,
        template: String,
        revset: Revset? = null,
        noGraph: Boolean = false,
        noColor: Boolean = true,
    ): String? {
        val output = executeWithTemplate(
            root,
            template,
            revset,
            noGraph = noGraph,
            noColor = noColor
        )
        return output.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
    }
}
