package net.chikach.intellijjj.commands

import com.intellij.openapi.vfs.VirtualFile

class JujutsuLogCommand(private val commandExecutor: JujutsuCommandExecutor) {
    private fun execute(
        root: VirtualFile,
        template: String,
        revset: Revset?,
        limit: Int?,
        noGraph: Boolean,
        noColor: Boolean,
    ): String {
        val args = mutableListOf("log")
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

    companion object {
        fun buildCommitTemplate(delimiter: String, commitSeparator: String): String {
            return """
                commit_id ++ "$delimiter" ++ 
                change_id ++ "$delimiter" ++ 
                parents.map(|p| p.commit_id()).join(",") ++ "$delimiter" ++ 
                author.name() ++ "$delimiter" ++ 
                author.email() ++ "$delimiter" ++ 
                author.timestamp() ++ "$delimiter" ++ 
                description.first_line() ++ "$delimiter" ++ 
                description ++ "$delimiter" ++
                root ++ "$commitSeparator"
            """.trimIndent().replace("\n", " ")
        }
    }
}
