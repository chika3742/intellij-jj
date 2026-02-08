@file:OptIn(ExperimentalTime::class)

package net.chikach.intellijjj.jujutsu.models

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.TimedVcsCommit
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.impl.HashImpl
import kotlinx.serialization.Serializable
import net.chikach.intellijjj.jujutsu.JujutsuTemplateUtil
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Serializable
data class JujutsuCommit(
    /**
     * Jujutsu change ID
     */
    val changeId: String,
    /**
     * Git commit hash
     */
    val commitId: String,
    /**
     * List of parent commit IDs
     */
    val parents: List<String>,
    val bookmarks: List<JujutsuCommitRef>,
    val author: JujutsuSignature,
    val committer: JujutsuSignature,
    /**
     * Full commit description (commit message)
     */
    val description: String,
    /**
     * Short description (first line of the commit message)
     */
    val shortDescription: String,
    val isRoot: Boolean
) {
    companion object {
        val TEMPLATE = JujutsuTemplateUtil.createSerializableTemplate(mapOf(
            "commitId" to JujutsuTemplateUtil.json("commit_id"),
            "changeId" to JujutsuTemplateUtil.json("change_id"),
            "parents" to JujutsuTemplateUtil.mappedList("parents", "|p| json(p.commit_id())"),
            "bookmarks" to JujutsuTemplateUtil.mappedList("bookmarks", "|b| json(b)"),
            "author" to JujutsuTemplateUtil.json("author"),
            "committer" to JujutsuTemplateUtil.json("committer"),
            "description" to JujutsuTemplateUtil.json("description"),
            "shortDescription" to JujutsuTemplateUtil.json("description.first_line()"),
            "isRoot" to JujutsuTemplateUtil.json("root"),
        ))
        
        const val UNKNOWN_TEXT = "<unknown>"
        const val NO_DESC_TEXT = "<no description set>"
        const val ROOT_COMMIT_DESC = "<root>"

        fun placeholder(hash: String): JujutsuCommit {
            return JujutsuCommit(
                changeId = UNKNOWN_TEXT,
                commitId = hash,
                parents = emptyList(),
                author = JujutsuSignature(UNKNOWN_TEXT, UNKNOWN_TEXT, Clock.System.now()),
                bookmarks = emptyList(),
                committer = JujutsuSignature(UNKNOWN_TEXT, UNKNOWN_TEXT, Clock.System.now()),
                description = NO_DESC_TEXT,
                shortDescription = NO_DESC_TEXT,
                isRoot = false
            )
        }
    }
    
    val hash: Hash
        get() = HashImpl.build(commitId)
    
    val parentHashes: List<Hash>
        get() = parents.map { HashImpl.build(it) }
    
    val readableDescription: String
        get() = if (isRoot) ROOT_COMMIT_DESC else description.ifEmpty { NO_DESC_TEXT }
    
    val readableShortDescription: String
        get() = if (isRoot) ROOT_COMMIT_DESC else shortDescription.ifEmpty { NO_DESC_TEXT }

    fun toCommitMetadata(root: VirtualFile, factory: VcsLogObjectsFactory): VcsCommitMetadata {
        return factory.createCommitMetadata(
            hash,
            parentHashes,
            committer.timestamp.toEpochMilliseconds(),
            root,
            readableShortDescription,
            author.safeName,
            author.safeEmail,
            readableDescription,
            committer.safeName,
            committer.safeEmail,
            author.timestamp.toEpochMilliseconds()
        )
    }

    fun toTimedCommit(factory: VcsLogObjectsFactory): TimedVcsCommit {
        return factory.createTimedCommit(
            hash,
            parentHashes,
            committer.timestamp.toEpochMilliseconds(),
        )
    }
}