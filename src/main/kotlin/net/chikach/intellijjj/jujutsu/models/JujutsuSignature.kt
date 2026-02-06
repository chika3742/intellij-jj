@file:OptIn(ExperimentalTime::class)

package net.chikach.intellijjj.jujutsu.models

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
    val vcsUser: VcsUser
        get() = VcsUserImpl(name, email)
}
