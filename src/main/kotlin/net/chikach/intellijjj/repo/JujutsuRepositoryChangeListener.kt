package net.chikach.intellijjj.repo

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic

fun interface JujutsuRepositoryChangeListener {
    fun repositoryChanged(root: VirtualFile)

    companion object {
        @Topic.ProjectLevel
        @JvmField
        val TOPIC: Topic<JujutsuRepositoryChangeListener> = Topic.create(
            "Jujutsu repository change",
            JujutsuRepositoryChangeListener::class.java
        )
    }
}
