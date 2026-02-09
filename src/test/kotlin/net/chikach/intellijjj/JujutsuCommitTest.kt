@file:OptIn(kotlin.time.ExperimentalTime::class)

package net.chikach.intellijjj

import net.chikach.intellijjj.jujutsu.models.JujutsuCommit
import net.chikach.intellijjj.jujutsu.models.JujutsuSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Clock

class JujutsuCommitTest {
    @Test
    fun readableDescriptionUsesRootMarker() {
        val commit = JujutsuCommit(
            changeId = "c",
            commitId = "h",
            parents = emptyList(),
            bookmarks = emptyList(),
            author = JujutsuSignature("a", "b", Clock.System.now()),
            committer = JujutsuSignature("c", "d", Clock.System.now()),
            description = "desc",
            shortDescription = "short",
            isRoot = true,
        )
        assertEquals(JujutsuCommit.ROOT_COMMIT_DESC, commit.readableDescription)
        assertEquals(JujutsuCommit.ROOT_COMMIT_DESC, commit.readableShortDescription)
    }

    @Test
    fun readableDescriptionUsesFallbackForEmpty() {
        val commit = JujutsuCommit(
            changeId = "c",
            commitId = "h",
            parents = emptyList(),
            bookmarks = emptyList(),
            author = JujutsuSignature("a", "b", Clock.System.now()),
            committer = JujutsuSignature("c", "d", Clock.System.now()),
            description = "",
            shortDescription = "",
            isRoot = false,
        )
        assertEquals(JujutsuCommit.NO_DESC_TEXT, commit.readableDescription)
        assertEquals(JujutsuCommit.NO_DESC_TEXT, commit.readableShortDescription)
    }

    @Test
    fun placeholderUsesExpectedDefaults() {
        val placeholder = JujutsuCommit.placeholder("deadbeef")
        assertEquals("deadbeef", placeholder.commitId)
        assertEquals(JujutsuCommit.NO_DESC_TEXT, placeholder.description)
        assertFalse(placeholder.isRoot)
    }
}
