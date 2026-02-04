package net.chikach.intellijjj.vcs.log

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.vcs.log.*
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsUserImpl
import net.chikach.intellijjj.JujutsuVcs
import net.chikach.intellijjj.commands.JujutsuCommandExecutor
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatterBuilder
import java.util.*

class JujutsuVcsLogProvider(
    private val project: Project
) : VcsLogProvider {

    private val LOG = Logger.getInstance(JujutsuVcsLogProvider::class.java)
    private val commandExecutor = JujutsuCommandExecutor(project)
    
    companion object {
        private val TIMESTAMP_FORMATTER = DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendPattern(".SSS")
            .optionalEnd()
            .appendPattern(" XXX")
            .toFormatter(Locale.US)
        
        private const val NO_BOOKMARKS_OUTPUT = "(no bookmarks)"
    }

    override fun readFirstBlock(
        root: VirtualFile,
        requirements: VcsLogProvider.Requirements
    ): VcsLogProvider.DetailedLogData {
        try {
            val commits = parseLogOutput(root, requirements.commitCount)
            val refs = readRefs(root)
            
            return object : VcsLogProvider.DetailedLogData {
                override fun getCommits(): List<VcsCommitMetadata> {
                    return commits.map { it.toVcsCommitMetadata(root) }
                }
                override fun getRefs(): MutableSet<VcsRef> = refs
            }
        } catch (e: Exception) {
            LOG.error("Failed to read first block", e)
            return object : VcsLogProvider.DetailedLogData {
                override fun getCommits(): List<VcsCommitMetadata> = emptyList()
                override fun getRefs(): MutableSet<VcsRef> = mutableSetOf()
            }
        }
    }

    override fun readAllHashes(root: VirtualFile, consumer: Consumer<in TimedVcsCommit>): VcsLogProvider.LogData {
        try {
            val commits = parseLogOutput(root, -1) // Read all commits
            commits.forEach { commit ->
                consumer.consume(object : TimedVcsCommit {
                    override fun getId(): Hash = commit.hash
                    override fun getParents(): List<Hash> = commit.parents
                    override fun getTimestamp(): Long = commit.commitTime
                })
            }
            
            val refs = readRefs(root)
            
            return object : VcsLogProvider.LogData {
                override fun getRefs(): MutableSet<VcsRef> = refs
                override fun getUsers(): MutableSet<VcsUser> = mutableSetOf()
            }
        } catch (e: Exception) {
            LOG.error("Failed to read all hashes", e)
            return object : VcsLogProvider.LogData {
                override fun getRefs(): MutableSet<VcsRef> = mutableSetOf()
                override fun getUsers(): MutableSet<VcsUser> = mutableSetOf()
            }
        }
    }

    override fun readMetadata(root: VirtualFile, hashes: List<String>, consumer: Consumer<in VcsCommitMetadata>) {
        try {
            // Optimize: fetch only the requested commits using revset
            if (hashes.isEmpty()) return
            
            val delimiter = "\u001E" // ASCII Record Separator
            val commitSeparator = "\u001F" // ASCII Unit Separator
            val template = """
                commit_id ++ "$delimiter" ++ 
                change_id ++ "$delimiter" ++ 
                parents.map(|p| p.commit_id).join(",") ++ "$delimiter" ++ 
                author.name() ++ "$delimiter" ++ 
                author.email() ++ "$delimiter" ++ 
                author.timestamp() ++ "$delimiter" ++ 
                description.first_line() ++ "$delimiter" ++ 
                description ++ "$commitSeparator"
            """.trimIndent().replace("\n", " ")
            
            // Fetch commits using a revset expression
            val revset = hashes.joinToString(" | ")
            val output = commandExecutor.executeAndCheck(root, "log", "-r", revset, "-T", template)
            val commits = parseCommits(output, delimiter, commitSeparator)
            
            commits.forEach { consumer.consume(it.toVcsCommitMetadata(root)) }
        } catch (e: Exception) {
            LOG.warn("Failed to read metadata for specific hashes", e)
        }
    }

    override fun readFullDetails(root: VirtualFile, hashes: List<String>, consumer: Consumer<in VcsFullCommitDetails>) {
        // For now, we don't provide full details with file changes
        // This would require parsing `jj diff` output for each commit
    }

    override fun getSupportedVcs(): VcsKey {
        return JujutsuVcs.getKey()
    }
    
    override fun getCurrentUser(root: VirtualFile): VcsUser? {
        return try {
            val nameOutput = commandExecutor.execute(root, "config", "get", "user.name")
            val emailOutput = commandExecutor.execute(root, "config", "get", "user.email")
            
            if (nameOutput.exitCode == 0 && emailOutput.exitCode == 0) {
                val name = nameOutput.stdout.trim()
                val email = emailOutput.stdout.trim()
                if (name.isNotEmpty() && email.isNotEmpty()) {
                    VcsUserImpl(name, email)
                } else null
            } else null
        } catch (e: Exception) {
            LOG.warn("Failed to get current user", e)
            null
        }
    }
    
    override fun getContainingBranches(root: VirtualFile, commitHash: Hash): Collection<String> {
        return try {
            // Jujutsu uses "bookmarks" instead of branches
            val output = commandExecutor.executeAndCheck(
                root,
                "log",
                "-r",
                "bookmarks() & ::${commitHash.asString()}",
                "-T",
                "bookmarks"
            )
            output.lines()
                .filter { it.isNotBlank() }
                .flatMap { it.split(",").map { name -> name.trim() } }
                .filter { it.isNotEmpty() }
                .toSet()
        } catch (e: Exception) {
            LOG.warn("Failed to get containing branches for ${commitHash.asString()}", e)
            emptyList()
        }
    }
    
    override fun <T> getPropertyValue(property: VcsLogProperties.VcsLogProperty<T>): T? {
        return null
    }
    
    override fun getCurrentBranch(root: VirtualFile): String? {
        return try {
            // In Jujutsu, we can check for the current bookmark(s)
            val output = commandExecutor.executeAndCheck(
                root,
                "log",
                "-r",
                "@",
                "-T",
                "bookmarks"
            )
            val bookmarks = output.trim()
            if (bookmarks.isNotEmpty() && bookmarks != NO_BOOKMARKS_OUTPUT) {
                bookmarks.split(",").firstOrNull()?.trim()
            } else null
        } catch (e: Exception) {
            LOG.warn("Failed to get current branch", e)
            null
        }
    }

    override fun getReferenceManager(): VcsLogRefManager {
        return JujutsuLogRefManager()
    }

    override fun subscribeToRootRefreshEvents(
        roots: Collection<VirtualFile>,
        refresher: VcsLogRefresher
    ): Disposable {
        // For now, return an empty disposable
        // In a full implementation, you'd watch for file system changes
        return Disposable { }
    }
    
    private fun readRefs(root: VirtualFile): MutableSet<VcsRef> {
        val refs = mutableSetOf<VcsRef>()
        try {
            // Read bookmarks (branches in Jujutsu terminology)
            val bookmarksOutput = commandExecutor.executeAndCheck(
                root,
                "bookmark",
                "list",
                "-T",
                "name ++ \" \" ++ commit_id ++ \"\\n\""
            )
            
            bookmarksOutput.lines().forEach { line ->
                val parts = line.trim().split(" ")
                if (parts.size >= 2) {
                    val name = parts[0]
                    val commitId = parts[1]
                    refs.add(
                        object : VcsRef {
                            override fun getName(): String = name
                            override fun getCommitHash(): Hash = HashImpl.build(commitId)
                            override fun getRoot(): VirtualFile = root
                            override fun getType(): VcsRefType = JujutsuRefType.BOOKMARK
                        }
                    )
                }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to read refs", e)
        }
        return refs
    }

    private fun parseLogOutput(root: VirtualFile, limit: Int): List<JujutsuCommitData> {
        // Build a template that outputs parseable format
        // Use a delimiter that's unlikely to appear in commit messages
        val delimiter = "\u001E" // ASCII Record Separator
        val commitSeparator = "\u001F" // ASCII Unit Separator
        val template = """
            commit_id ++ "$delimiter" ++ 
            change_id ++ "$delimiter" ++ 
            parents.map(|p| p.commit_id).join(",") ++ "$delimiter" ++ 
            author.name() ++ "$delimiter" ++ 
            author.email() ++ "$delimiter" ++ 
            author.timestamp() ++ "$delimiter" ++ 
            description.first_line() ++ "$delimiter" ++ 
            description ++ "$commitSeparator"
        """.trimIndent().replace("\n", " ")

        val args = mutableListOf("log", "-T", template)
        if (limit > 0) {
            args.add("-l")
            args.add(limit.toString())
        }
        
        val output = commandExecutor.executeAndCheck(root, *args.toTypedArray())
        return parseCommits(output, delimiter, commitSeparator)
    }

    private fun parseCommits(output: String, delimiter: String, commitSeparator: String): List<JujutsuCommitData> {
        val commits = mutableListOf<JujutsuCommitData>()
        val commitStrings = output.trim().split(commitSeparator)
        
        for (commitStr in commitStrings) {
            if (commitStr.isBlank()) continue
            
            try {
                val parts = commitStr.split(delimiter)
                if (parts.size < 8) {
                    LOG.warn("Invalid commit format: expected at least 8 parts, got ${parts.size}")
                    continue
                }
                
                val commitId = parts[0]
                val changeId = parts[1]
                val parentIds = if (parts[2].isEmpty()) emptyList() else parts[2].split(",")
                val authorName = parts[3]
                val authorEmail = parts[4]
                val timestamp = parseTimestamp(parts[5])
                val subject = parts[6]
                val fullMessage = parts[7]
                
                val hash = HashImpl.build(commitId)
                val parents = parentIds.map { HashImpl.build(it) }
                val author = VcsUserImpl(authorName, authorEmail)
                
                commits.add(
                    JujutsuCommitData(
                        hash = hash,
                        parents = parents,
                        commitTime = timestamp,
                        author = author,
                        fullMessage = fullMessage,
                        subject = subject,
                        changeId = changeId
                    )
                )
            } catch (e: Exception) {
                LOG.warn("Failed to parse commit", e)
            }
        }
        
        return commits
    }

    private fun parseTimestamp(timestampStr: String): Long {
        try {
            // Jujutsu timestamps are in ISO 8601 format, e.g., "2024-01-15 10:30:00.000 +00:00"
            val cleanedStr = timestampStr.trim()
            return ZonedDateTime.parse(cleanedStr, TIMESTAMP_FORMATTER).toInstant().toEpochMilli()
        } catch (e: Exception) {
            LOG.warn("Failed to parse timestamp: $timestampStr", e)
            return -1L // Use -1 as sentinel value to indicate parsing failure
        }
    }

    private data class JujutsuCommitData(
        val hash: Hash,
        val parents: List<Hash>,
        val commitTime: Long,
        val author: VcsUser,
        val fullMessage: String,
        val subject: String,
        val changeId: String
    ) {
        fun toVcsCommitMetadata(root: VirtualFile): VcsCommitMetadata {
            return object : VcsCommitMetadata {
                override fun getId(): Hash = hash
                override fun getParents(): List<Hash> = parents
                override fun getCommitTime(): Long = commitTime
                override fun getTimestamp(): Long = commitTime
                override fun getRoot(): VirtualFile = root
                override fun getSubject(): String = subject
                override fun getAuthor(): VcsUser = author
                override fun getFullMessage(): String = fullMessage
                override fun getCommitter(): VcsUser = author
                override fun getAuthorTime(): Long = commitTime
            }
        }
    }

    private class JujutsuLogRefManager : VcsLogRefManager {
        override fun serialize(output: java.io.DataOutput, type: VcsRefType) {
            // Serialize the ref type - for now we only support BOOKMARK
            when (type) {
                JujutsuRefType.BOOKMARK -> output.writeInt(0)
                else -> throw IllegalArgumentException("Unknown ref type: $type")
            }
        }

        override fun deserialize(input: java.io.DataInput): VcsRefType {
            val typeId = input.readInt()
            return when (typeId) {
                0 -> JujutsuRefType.BOOKMARK
                else -> throw IllegalArgumentException("Unknown ref type id: $typeId")
            }
        }

        override fun getBranchLayoutComparator(): Comparator<VcsRef> {
            return Comparator.comparing { it.name }
        }

        override fun getLabelsOrderComparator(): Comparator<VcsRef> {
            return Comparator.comparing { it.name }
        }

        override fun groupForBranchFilter(refs: Collection<VcsRef>): List<RefGroup> {
            return emptyList()
        }

        override fun groupForTable(refs: Collection<VcsRef>, compact: Boolean, showTagNames: Boolean): List<RefGroup> {
            return emptyList()
        }

        override fun isFavorite(reference: VcsRef): Boolean {
            return false
        }

        override fun setFavorite(reference: VcsRef, favorite: Boolean) {
            // Not implemented for now
        }
    }
    
    private enum class JujutsuRefType : VcsRefType {
        BOOKMARK {
            override fun isBranch(): Boolean = true
            override fun getBackgroundColor(): java.awt.Color = java.awt.Color(0x75, 0xAA, 0xDB)
        }
    }
}
