@file:OptIn(ExperimentalTime::class)

package net.chikach.intellijjj.jujutsu.models

import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.impl.VcsUserImpl
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
data class JujutsuSignature(
    val name: String,
    val email: String,
    val timestamp: Instant
) {
    val safeName: String
        get() = name.ifBlank { "<unknown>" }
    
    val safeEmail: String
        get() = email.ifBlank { "<unknown>" }
    
    val vcsUser: VcsUser
        get() = VcsUserImpl(safeName, safeEmail)

    fun getVcsUser(factory: VcsLogObjectsFactory): VcsUser {
        return factory.createUser(safeName, safeEmail)
    }
}
