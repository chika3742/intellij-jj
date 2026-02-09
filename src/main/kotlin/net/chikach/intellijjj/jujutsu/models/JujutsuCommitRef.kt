@file:OptIn(ExperimentalSerializationApi::class)

package net.chikach.intellijjj.jujutsu.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.JsonNames

/**
 * Bookmark/reference data embedded in `jj log` commit output.
 */
@Serializable
@JsonIgnoreUnknownKeys
class JujutsuCommitRef(
    val name: String,
    val target: List<String?>,
    val remote: String? = null,
    @JsonNames("tracking_target")
    val trackingTarget: List<String>? = null,
) {
}
