package net.chika3742.intellijjj

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.VcsChangesViewRefresher

class JujutsuChangesViewRefresher(private val project: Project) : VcsChangesViewRefresher {
    override fun refresh() {
        // Refresh logic can be implemented here
    }
}
