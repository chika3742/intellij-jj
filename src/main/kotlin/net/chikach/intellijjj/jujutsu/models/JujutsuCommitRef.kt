package net.chikach.intellijjj.jujutsu.models

import kotlinx.serialization.Serializable

@Serializable
class JujutsuCommitRef(
    val name: String,
    val target: List<String>,
) {
}