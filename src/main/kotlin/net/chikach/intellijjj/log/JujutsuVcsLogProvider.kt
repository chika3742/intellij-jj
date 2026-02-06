package net.chikach.intellijjj.log

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.TextRevisionNumber
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.vcs.log.*
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsUserImpl
import com.intellij.vcsUtil.VcsUtil
import net.chikach.intellijjj.JujutsuVcs
import net.chikach.intellijjj.commands.JujutsuCommandExecutor
import net.chikach.intellijjj.repo.JujutsuRepositoryChangeListener
import net.chikach.intellijjj.repo.JujutsuRepositoryWatcher
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatterBuilder
import java.util.*

class JujutsuVcsLogProvider(
    private val project: Project
) : VcsLogProvider {

    private val LOG = Logger.getInstance(JujutsuVcsLogProvider::class.java)
    private val commandExecutor = JujutsuCommandExecutor(project)
    private val vcsObjectsFactory = project.getService(VcsLogObjectsFactory::class.java)
    
    companion object {
        // DateTimeFormatter is thread-safe and can be shared across instances.
        // Jujutsu always outputs timestamps in a consistent format regardless of user locale,
        // so using Locale.US is safe for parsing the numeric date/time components.
        private val TIMESTAMP_FORMATTER = DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendPattern(".SSS")
            .optionalEnd()
            .appendPattern(" XXX")
            .toFormatter(Locale.US)
        
        private const val NO_BOOKMARKS_OUTPUT = "(no bookmarks)"
        private const val INVALID_TIMESTAMP = -1L

        private fun commitRevset(hash: String): String = "commit_id(${hash.trim()})"
    }

    override fun readFirstBlock(
        root: VirtualFile,
        requirements: VcsLogProvider.Requirements
    ): VcsLogProvider.DetailedLogData {
        try {
            val commits = parseLogOutput(root, requirements.commitCount)

            LOG.debug("Commits: ${commits}")
            
            return object : VcsLogProvider.DetailedLogData {
                override fun getCommits(): List<VcsCommitMetadata> {
                    return commits.map { it.toVcsCommitMetadata(root, vcsObjectsFactory) }
                }
                override fun getRefs(): MutableSet<JujutsuRef> = mutableSetOf()
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
                consumer.consume(vcsObjectsFactory.createTimedCommit(commit.hash, commit.parents, commit.commitTime))
            }
            
            return object : VcsLogProvider.LogData {
                override fun getRefs(): MutableSet<JujutsuRef> = mutableSetOf()
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
            val commits = readCommitsForHashes(root, hashes)
            commits.forEach { commit -> consumer.consume(commit.toVcsCommitMetadata(root, vcsObjectsFactory)) }
        } catch (e: Exception) {
            LOG.warn("Failed to read metadata for specific hashes", e)
        }
    }

    override fun readFullDetails(root: VirtualFile, hashes: List<String>, consumer: Consumer<in VcsFullCommitDetails>) {
        if (hashes.isEmpty()) return
        try {
            val commits = readCommitsForHashes(root, hashes)
            hashes.forEach { hash ->
                val commit = findCommitForHash(commits, hash)
                    ?: readCommitForHash(root, hash)
                    ?: createPlaceholderCommit(hash)
                val changes = try {
                    readChanges(root, commit)
                } catch (e: Exception) {
                    LOG.warn("Failed to read changes for ${commit.hash.asString()}", e)
                    emptyList()
                }
                consumer.consume(JujutsuFullCommitDetails(commit, root, changes))
            }
        } catch (e: Exception) {
            LOG.warn("Failed to read full details for ${hashes.size} commits", e)
        }
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
                "bookmarks ++ \"\n\"",
                "--no-graph"
            )
            output.trim().lines().toSet()
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
                "bookmarks",
                "--no-graph"
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
        if (roots.isEmpty()) return Disposable { }
        project.getService(JujutsuRepositoryWatcher::class.java)
        val rootsSet = roots.toSet()
        val connection = project.messageBus.connect()
        connection.subscribe(JujutsuRepositoryChangeListener.TOPIC, JujutsuRepositoryChangeListener { root ->
            if (rootsSet.contains(root)) {
                refresher.refresh(root)
            }
        })
        return connection
    }

    override fun getCommitsMatchingFilter(
        root: VirtualFile,
        filterCollection: VcsLogFilterCollection,
        graphOptions: PermanentGraph.Options,
        maxCount: Int
    ): List<TimedVcsCommit> {
        if (maxCount == 0) return emptyList()
        val detailsFilters = filterCollection.detailsFilters
        val hashFilter = filterCollection.get(VcsLogFilterCollection.HASH_FILTER)
        val loadLimit = if (filterCollection.isEmpty || maxCount <= 0) maxCount else -1
        val log = parseLogOutput(root, loadLimit)

        val filtered = log.asSequence().filter { commit ->
            if (hashFilter != null) {
                val fullHash = commit.hash.asString()
                if (hashFilter.hashes.none { fullHash.startsWith(it, ignoreCase = true) }) {
                    return@filter false
                }
            }
            if (detailsFilters.isNotEmpty()) {
                val metadata = commit.toVcsCommitMetadata(root, vcsObjectsFactory)
                if (detailsFilters.any { !it.matches(metadata) }) {
                    return@filter false
                }
            }
            true
        }

        val limited = if (maxCount > 0) filtered.take(maxCount) else filtered
        return limited.map { commit ->
            vcsObjectsFactory.createTimedCommit(commit.hash, commit.parents, commit.commitTime)
        }.toList()
    }
    
    private fun parseLogOutput(root: VirtualFile, limit: Int): List<JujutsuCommitData> {
        // Build a template that outputs parseable format
        // Use a delimiter that's unlikely to appear in commit messages
        val delimiter = "\u001E" // ASCII Record Separator
        val commitSeparator = "\u001F" // ASCII Unit Separator
        val template = buildLogTemplate(delimiter, commitSeparator)

        val args = mutableListOf("log", "--no-graph", "-T", template)
        if (limit > 0) {
            args.add("-n")
            args.add(limit.toString())
        }
        
        val output = commandExecutor.executeAndCheck(root, *args.toTypedArray())
        return parseCommits(output, delimiter, commitSeparator)
    }

    private fun readCommitsForHashes(root: VirtualFile, hashes: List<String>): List<JujutsuCommitData> {
        if (hashes.isEmpty()) return emptyList()
        val delimiter = "\u001E" // ASCII Record Separator
        val commitSeparator = "\u001F" // ASCII Unit Separator
        val template = buildLogTemplate(delimiter, commitSeparator)

        val revset = hashes.joinToString(" | ") { commitRevset(it) }
        return try {
            val output = commandExecutor.executeAndCheck(root, "log", "--no-graph", "-r", revset, "-T", template)
            parseCommits(output, delimiter, commitSeparator)
        } catch (e: Exception) {
            LOG.warn("Failed to read commits in a single revset call", e)
            val commits = mutableListOf<JujutsuCommitData>()
            hashes.forEach { hash ->
                try {
                    val output = commandExecutor.executeAndCheck(root, "log", "--no-graph", "-r", commitRevset(hash), "-T", template)
                    commits.addAll(parseCommits(output, delimiter, commitSeparator))
                } catch (inner: Exception) {
                    LOG.warn("Failed to read commit $hash", inner)
                }
            }
            commits
        }
    }

    private fun readChanges(root: VirtualFile, commit: JujutsuCommitData): List<Change> {
        val parentHash = commit.parents.firstOrNull()?.asString()
        val output = commandExecutor.executeAndCheck(
            root,
            "diff",
            "--summary",
            "-r",
            commitRevset(commit.hash.asString())
        )
        val changes = mutableListOf<Change>()
        output.lineSequence().forEach { line ->
            val entry = parseSummaryLine(line) ?: return@forEach
            val absolutePath = File(root.path, entry.path).path
            val filePath = VcsUtil.getFilePath(absolutePath, false)
            val before = when (entry.type) {
                JujutsuChangeType.MODIFIED, JujutsuChangeType.DELETED ->
                    parentHash?.let { JujutsuContentRevision(root, filePath, entry.path, it, commandExecutor) }
                JujutsuChangeType.ADDED -> null
            }
            val after = when (entry.type) {
                JujutsuChangeType.MODIFIED, JujutsuChangeType.ADDED ->
                    JujutsuContentRevision(root, filePath, entry.path, commit.hash.asString(), commandExecutor)
                JujutsuChangeType.DELETED -> null
            }
            if (before != null || after != null) {
                changes.add(Change(before, after))
            }
        }
        return changes
    }

    private fun readCommitForHash(root: VirtualFile, hash: String): JujutsuCommitData? {
        val delimiter = "\u001E" // ASCII Record Separator
        val commitSeparator = "\u001F" // ASCII Unit Separator
        val template = buildLogTemplate(delimiter, commitSeparator)
        return try {
            val output = commandExecutor.executeAndCheck(root, "log", "--no-graph", "-r", commitRevset(hash), "-T", template)
            parseCommits(output, delimiter, commitSeparator).firstOrNull()
        } catch (e: Exception) {
            LOG.warn("Failed to read commit metadata for $hash", e)
            null
        }
    }

    private fun findCommitForHash(commits: List<JujutsuCommitData>, hash: String): JujutsuCommitData? {
        val normalized = hash.trim()
        val exact = commits.firstOrNull { it.hash.asString().equals(normalized, ignoreCase = true) }
        if (exact != null) return exact
        return commits.firstOrNull {
            val commitHash = it.hash.asString()
            commitHash.startsWith(normalized, ignoreCase = true) ||
                normalized.startsWith(commitHash, ignoreCase = true)
        }
    }

    private fun createPlaceholderCommit(hash: String): JujutsuCommitData {
        return JujutsuCommitData(
            hash = HashImpl.build(hash),
            parents = emptyList(),
            commitTime = INVALID_TIMESTAMP,
            author = VcsUserImpl("<unknown>", ""),
            fullMessage = "<details unavailable>",
            subject = "<details unavailable>",
            changeId = ""
        )
    }

    private fun parseSummaryLine(line: String): JujutsuChangeEntry? {
        val trimmed = line.trim()
        if (trimmed.length < 3) return null
        if (trimmed[1] != ' ') return null
        val path = trimmed.substring(2).trim()
        if (path.isEmpty()) return null
        val type = when (trimmed[0]) {
            'M' -> JujutsuChangeType.MODIFIED
            'A' -> JujutsuChangeType.ADDED
            'D' -> JujutsuChangeType.DELETED
            else -> return null
        }
        return JujutsuChangeEntry(type, path)
    }

    private fun parseCommits(output: String, delimiter: String, commitSeparator: String): List<JujutsuCommitData> {
        val commits = mutableListOf<JujutsuCommitData>()
        val commitStrings = output.split(commitSeparator)

        for (commitStr in commitStrings) {
            if (commitStr.isBlank()) continue
            
            try {
                val parts = commitStr.split(delimiter)
                if (parts.size < 8) {
                    LOG.warn("Invalid commit format: expected at least 8 parts, got ${parts.size}, $parts", Throwable())
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
                        fullMessage = fullMessage.ifEmpty { "<no description set>" },
                        subject = subject.ifEmpty { "<no description set>" },
                        changeId = changeId
                    )
                )
            } catch (e: Exception) {
                LOG.warn("Failed to parse commit", e)
            }
        }
        
        return commits
    }

    private fun buildLogTemplate(delimiter: String, commitSeparator: String): String {
        return """
            commit_id ++ "$delimiter" ++ 
            change_id ++ "$delimiter" ++ 
            parents.map(|p| p.commit_id()).join(",") ++ "$delimiter" ++ 
            author.name() ++ "$delimiter" ++ 
            author.email() ++ "$delimiter" ++ 
            author.timestamp() ++ "$delimiter" ++ 
            description.first_line() ++ "$delimiter" ++ 
            description ++ "$commitSeparator"
        """.trimIndent().replace("\n", " ")
    }

    private fun parseTimestamp(timestampStr: String): Long {
        try {
            // Jujutsu timestamps are in the format "2024-01-15 10:30:00.000 +00:00"
            // (space-separated date and time with timezone offset, not standard ISO 8601)
            val cleanedStr = timestampStr.trim()
            return ZonedDateTime.parse(cleanedStr, TIMESTAMP_FORMATTER).toInstant().toEpochMilli()
        } catch (e: Exception) {
            LOG.warn("Failed to parse timestamp: $timestampStr", e)
            return INVALID_TIMESTAMP
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
        fun toVcsCommitMetadata(root: VirtualFile, factory: VcsLogObjectsFactory): VcsCommitMetadata {
            return factory.createCommitMetadata(
                hash,
                parents,
                commitTime,
                root,
                subject,
                author.name,
                author.email,
                fullMessage,
                author.name,
                author.email,
                commitTime
            )
        }
    }

    private data class JujutsuChangeEntry(
        val type: JujutsuChangeType,
        val path: String
    )

    private enum class JujutsuChangeType {
        MODIFIED,
        ADDED,
        DELETED
    }

    private class JujutsuContentRevision(
        private val root: VirtualFile,
        private val filePath: FilePath,
        private val relativePath: String,
        private val revision: String,
        private val commandExecutor: JujutsuCommandExecutor
    ) : com.intellij.openapi.vcs.changes.ContentRevision {
        override fun getContent(): String? {
            return try {
                commandExecutor.executeAndCheck(root, "file", "show", "-r", commitRevset(revision), "--", relativePath)
            } catch (e: Exception) {
                Logger.getInstance(JujutsuContentRevision::class.java)
                    .warn("Failed to read content for $relativePath at $revision", e)
                null
            }
        }

        override fun getFile(): FilePath = filePath

        override fun getRevisionNumber(): VcsRevisionNumber {
            val shortRevision = revision.take(7)
            return TextRevisionNumber(revision, shortRevision)
        }
    }

    private class JujutsuFullCommitDetails(
        private val commit: JujutsuCommitData,
        private val root: VirtualFile,
        private val changes: List<Change>
    ) : VcsFullCommitDetails {
        override fun getChanges(): Collection<Change> = changes
        override fun getChanges(parent: Int): Collection<Change> = changes
        override fun getId(): Hash = commit.hash
        override fun getParents(): List<Hash> = commit.parents
        override fun getCommitTime(): Long = commit.commitTime
        override fun getTimestamp(): Long = commit.commitTime
        override fun getRoot(): VirtualFile = root
        override fun getSubject(): String = commit.subject
        override fun getAuthor(): VcsUser = commit.author
        override fun getFullMessage(): String = commit.fullMessage
        override fun getCommitter(): VcsUser = commit.author
        override fun getAuthorTime(): Long = commit.commitTime
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
            return when (val typeId = input.readInt()) {
                0 -> JujutsuRefType.BOOKMARK
                else -> throw IllegalArgumentException("Unknown ref type id: $typeId")
            }
        }

        override fun getBranchLayoutComparator(): Comparator<VcsRef> {
            return Comparator.comparing { ref -> ref.name }
        }

        override fun getLabelsOrderComparator(): Comparator<VcsRef> {
            return Comparator.comparing { ref -> ref.name }
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
    
    private interface JujutsuRef : VcsRef {
        override fun getCommitHash(): Hash {
            if (getIsConflicted()) {
                throw IllegalStateException("Cannot get commit hash for conflicted ref")
            }
            
            return HashImpl.build(getCommitId()!!)
        }
        
        fun getCommitId(): String?
        
        fun getIsConflicted(): Boolean
    }
    
    private enum class JujutsuRefType : VcsRefType {
        BOOKMARK {
            override fun isBranch(): Boolean = true
            override fun getBackgroundColor(): java.awt.Color = BOOKMARK_COLOR
        };
        
        companion object {
            // Light blue color (R=117, G=170, B=219) for bookmark refs to match Git branch colors
            private val BOOKMARK_COLOR = java.awt.Color(0x75, 0xAA, 0xDB)
        }
    }
}
