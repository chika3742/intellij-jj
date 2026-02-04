package net.chika3742.intellijjj.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeProvider
import com.intellij.openapi.vcs.changes.ChangelistBuilder
import com.intellij.openapi.vcs.changes.VcsDirtyScope
import com.intellij.openapi.vcs.VcsException
import net.chika3742.intellijjj.JujutsuVcs

class JujutsuChangeProvider(
    private val project: Project,
    private val vcs: JujutsuVcs
) : ChangeProvider {
    
    override fun getChanges(
        dirtyScope: VcsDirtyScope,
        builder: ChangelistBuilder,
        progress: com.intellij.openapi.progress.ProgressIndicator,
        addGate: com.intellij.openapi.vcs.changes.ChangeListManagerGate
    ) {
        // TODO: Implement change detection using jj status command
        // For now, this is a placeholder implementation
    }

    override fun isModifiedDocumentTrackingRequired(): Boolean = true
}
