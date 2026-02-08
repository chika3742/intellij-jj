package net.chikach.intellijjj.jujutsu.models

import kotlinx.serialization.Serializable

/**
 * Bookmark/reference data embedded in `jj log` commit output.
 */
@Serializable
class JujutsuCommitRef(
    val name: String,
    val target: List<String>,
) {
}
