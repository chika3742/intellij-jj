@file:OptIn(ExperimentalTime::class)

package net.chikach.intellijjj.log

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.TextRevisionNumber
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.vcs.log.*
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.impl.VcsUserImpl
import com.intellij.vcsUtil.VcsUtil
import net.chikach.intellijjj.JujutsuVcsUtil
import net.chikach.intellijjj.jujutsu.JujutsuCommandExecutor
import net.chikach.intellijjj.jujutsu.commands.JujutsuLogCommand
import net.chikach.intellijjj.jujutsu.Pattern
import net.chikach.intellijjj.jujutsu.Revset
import net.chikach.intellijjj.jujutsu.JujutsuDiffParser
import net.chikach.intellijjj.jujutsu.models.JujutsuCommit
import net.chikach.intellijjj.repo.JujutsuRepositoryChangeListener
import net.chikach.intellijjj.repo.JujutsuRepositoryWatcher
import java.io.File
import java.time.Instant
import kotlin.time.ExperimentalTime

/**
 * Jujutsu-backed implementation of IntelliJ's [VcsLogProvider].
 *
 * It translates VCS Log API requests into `jj log`/`jj diff` invocations and
 * maps IDE filters to revset expressions.
 */
class JujutsuVcsLogProvider(
    private val project: Project
) : VcsLogProvider {

    private val LOG = Logger.getInstance(JujutsuVcsLogProvider::class.java)
    private val commandExecutor = JujutsuCommandExecutor(project)
    private val logCommand = JujutsuLogCommand(commandExecutor)
    private val vcsObjectsFactory = project.getService(VcsLogObjectsFactory::class.java)
    
    companion object {
        private const val NO_BOOKMARKS_OUTPUT = "(no bookmarks)"
    }

    override fun readFirstBlock(
        root: VirtualFile,
        requirements: VcsLogProvider.Requirements
    ): VcsLogProvider.DetailedLogData {
        try {
            val commits = logCommand.getCommits(root, limit = requirements.commitCount)

            LOG.debug("Commits: $commits")
            
            return object : VcsLogProvider.DetailedLogData {
                override fun getCommits(): List<VcsCommitMetadata> {
                    return commits.map { it.toCommitMetadata(root, vcsObjectsFactory) }
                }
                override fun getRefs(): MutableSet<VcsRef> = extractBookmarksForCommits(commits, root)
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
            val commits = logCommand.getCommits(root) // Read all commits
            commits.forEach { commit ->
                consumer.consume(commit.toTimedCommit(vcsObjectsFactory))
            }
            
            return object : VcsLogProvider.LogData {
                override fun getRefs(): MutableSet<VcsRef> = extractBookmarksForCommits(commits, root)
                override fun getUsers(): MutableSet<VcsUser> = extractUsersForCommits(commits)
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
            commits.forEach { commit -> consumer.consume(commit.toCommitMetadata(root, vcsObjectsFactory)) }
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
                    ?: JujutsuCommit.placeholder(hash)
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

    override fun getSupportedVcs(): VcsKey = JujutsuVcsUtil.getKey()
    
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
            val output = logCommand.executeWithTemplate(
                root,
                "bookmarks ++ \"\n\"",
                Revset.and(
                    Revset.bookmarks(),
                    Revset.rangeWithRoot(to = Revset.commitId(commitHash.asString())),
                ),
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
            val output = logCommand.executeWithTemplate(root, "bookmarks", Revset.WORKING_COPY)
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
        val hashFilter = filterCollection.get(VcsLogFilterCollection.HASH_FILTER)
        val userFilter = filterCollection.get(VcsLogFilterCollection.USER_FILTER)
        val structureFilter = filterCollection.get(VcsLogFilterCollection.STRUCTURE_FILTER)
        val textFilter = filterCollection.get(VcsLogFilterCollection.TEXT_FILTER)
        val dateFilter = filterCollection.get(VcsLogFilterCollection.DATE_FILTER)

        var revset: Revset = Revset.ALL

        if (textFilter != null) {
            val pattern = if (textFilter.isRegex) {
                Pattern.regex(textFilter.text, textFilter.matchesCase())
            } else {
                Pattern.substring(textFilter.text, textFilter.matchesCase())
            }
            revset = Revset.and(
                revset,
                Revset.description(pattern)
            )
        }

        if (hashFilter != null) {
            val hashRevsets = hashFilter.hashes.map { Revset.commitId(it) }
            revset = Revset.and(
                revset,
                Revset.or(hashRevsets)
            )
        }

        if (userFilter != null) {
            val userRevsets = userFilter.getUsers(root).map { Revset.user(it) }
            revset = Revset.and(
                revset,
                Revset.or(userRevsets)
            )
        }
        
        if (structureFilter != null) {
            val rootPath = FileUtil.toSystemIndependentName(root.path)
            val pathPatterns = structureFilter.files.mapNotNull { file ->
                val filePath = FileUtil.toSystemIndependentName(file.path)
                val relativePath = FileUtil.getRelativePath(rootPath, filePath, '/')
                    ?: return@mapNotNull null
                Pattern.root(relativePath)
            }
            if (pathPatterns.isNotEmpty()) {
                revset = Revset.and(
                    revset,
                    Revset.files(Pattern.or(pathPatterns))
                )
            }
        }

        if (dateFilter != null) {
            val datePatterns = mutableListOf<Pattern>()
            dateFilter.after?.time?.let { afterMillis ->
                val afterString = Instant.ofEpochMilli(afterMillis).toString()
                datePatterns.add(Pattern.dateAfter(afterString))
            }
            dateFilter.before?.time?.let { beforeMillis ->
                val inclusiveBeforeMillis = if (beforeMillis < Long.MAX_VALUE) beforeMillis + 1 else beforeMillis
                val beforeString = Instant.ofEpochMilli(inclusiveBeforeMillis).toString()
                datePatterns.add(Pattern.dateBefore(beforeString))
            }
            if (datePatterns.isNotEmpty()) {
                revset = Revset.and(revset, Revset.committerDate(Pattern.and(datePatterns)))
            }
        }

        val commits = logCommand.getCommits(root, revset, maxCount)
        return commits.map { it.toTimedCommit(vcsObjectsFactory) }
    }
    
    /**
     * Reads commit metadata for specific hashes, with per-hash fallback when
     * the grouped revset query fails.
     */
    private fun readCommitsForHashes(root: VirtualFile, hashes: List<String>): List<JujutsuCommit> {
        if (hashes.isEmpty()) return emptyList()

        val revset = Revset.or(hashes.map { Revset.commitId(it) })
        return try {
            logCommand.getCommits(root, revset)
        } catch (e: Exception) {
            LOG.warn("Failed to read commits in a single revset call", e)
            val commits = mutableListOf<JujutsuCommit>()
            hashes.forEach { hash ->
                try {
                    logCommand.getCommits(root, revset)
                } catch (inner: Exception) {
                    LOG.warn("Failed to read commit $hash", inner)
                }
            }
            commits
        }
    }

    /**
     * Builds file-level changes for one commit from `jj diff --summary`.
     */
    private fun readChanges(root: VirtualFile, commit: JujutsuCommit): List<Change> {
        val parentHash = commit.parents.firstOrNull()
        val output = commandExecutor.executeAndCheck(
            root,
            "diff",
            "--summary",
            "-r",
            Revset.commitId(commit.hash.asString()).stringify()
        )
        val changes = mutableListOf<Change>()
        fun toFilePath(relativePath: String): FilePath {
            val absolutePath = File(root.path, relativePath).path
            return VcsUtil.getFilePath(absolutePath, false)
        }
        output.lineSequence().forEach { line ->
            val entry = JujutsuDiffParser.parseSummaryLine(line) ?: return@forEach
            val before = entry.beforePath?.let { path ->
                parentHash?.let { JujutsuContentRevision(root, toFilePath(path), path, it, commandExecutor) }
            }
            val after = entry.afterPath?.let { path ->
                JujutsuContentRevision(root, toFilePath(path), path, commit.hash.asString(), commandExecutor)
            }
            if (before != null || after != null) {
                changes.add(Change(before, after))
            }
        }
        return changes
    }

    private fun readCommitForHash(root: VirtualFile, hash: String): JujutsuCommit? {
        return try {
            logCommand.getCommits(root, Revset.commitId(hash)).firstOrNull()
        } catch (e: Exception) {
            LOG.warn("Failed to read commit metadata for $hash", e)
            null
        }
    }

    /**
     * Finds a commit by exact hash first, then by prefix match.
     */
    private fun findCommitForHash(commits: List<JujutsuCommit>, hash: String): JujutsuCommit? {
        val normalized = hash.trim()
        val exact = commits.firstOrNull { it.commitId.equals(normalized, ignoreCase = true) }
        if (exact != null) return exact
        return commits.firstOrNull {
            val commitHash = it.commitId
            commitHash.startsWith(normalized, ignoreCase = true) ||
                normalized.startsWith(commitHash, ignoreCase = true)
        }
    }
    
    private fun extractBookmarksForCommits(commits: List<JujutsuCommit>, root: VirtualFile): MutableSet<VcsRef> {
        return commits.mapNotNull { commit ->
            if (commit.bookmarks.isNotEmpty()) {
                commit.bookmarks.map { bookmark ->
                    vcsObjectsFactory.createRef(
                        commit.hash,
                        bookmark.name,
                        JujutsuRefType.BOOKMARK,
                        root,
                    )
                }
            } else null
        }.flatten().toMutableSet()
    }
    
    private fun extractUsersForCommits(commits: List<JujutsuCommit>): MutableSet<VcsUser> {
        return commits.flatMap { commit ->
            listOf(
                commit.author.getVcsUser(vcsObjectsFactory),
                commit.committer.getVcsUser(vcsObjectsFactory)
            )
        }.toMutableSet()
    }
    
    /**
     * Content revision backed by `jj file show` for log details diff views.
     */
    private class JujutsuContentRevision(
        private val root: VirtualFile,
        private val filePath: FilePath,
        private val relativePath: String,
        private val revision: String,
        private val commandExecutor: JujutsuCommandExecutor
    ) : com.intellij.openapi.vcs.changes.ContentRevision {
        override fun getContent(): String? {
            return try {
                commandExecutor.executeAndCheck(root, "file", "show", "-r", Revset.commitId(revision).stringify(), "--", relativePath)
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

    /**
     * Adapter from [JujutsuCommit] to IntelliJ [VcsFullCommitDetails].
     */
    private class JujutsuFullCommitDetails(
        private val commit: JujutsuCommit,
        private val root: VirtualFile,
        private val changes: List<Change>
    ) : VcsFullCommitDetails {
        override fun getChanges(): Collection<Change> = changes
        override fun getChanges(parent: Int): Collection<Change> = changes
        override fun getId(): Hash = commit.hash
        override fun getParents(): List<Hash> = commit.parentHashes
        override fun getCommitTime(): Long = commit.committer.timestamp.toEpochMilliseconds()
        override fun getTimestamp(): Long = commit.committer.timestamp.toEpochMilliseconds()
        override fun getRoot(): VirtualFile = root
        override fun getSubject(): String = commit.readableShortDescription
        override fun getAuthor(): VcsUser = commit.author.vcsUser
        override fun getFullMessage(): String = commit.readableDescription
        override fun getCommitter(): VcsUser = commit.committer.vcsUser
        override fun getAuthorTime(): Long = commit.author.timestamp.toEpochMilliseconds()
    }

    /**
     * Minimal ref manager for bookmark refs shown in the log UI.
     */
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
