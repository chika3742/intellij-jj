package net.chikach.intellijjj.jujutsu.models

import kotlinx.serialization.Serializable
import net.chikach.intellijjj.jujutsu.JujutsuTemplateUtil

/**
 * Serializable bookmark entry returned from `jj bookmark list`.
 */
@Serializable
data class JujutsuBookmark(
    val name: String,
) {
    companion object {
        /**
         * JSON-lines template consumed by `JujutsuBookmarkCommand`.
         */
        val TEMPLATE = JujutsuTemplateUtil.createSerializableTemplate(
            mapOf("name" to JujutsuTemplateUtil.json("name"))
        )
    }
}
