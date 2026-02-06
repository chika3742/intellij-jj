package net.chikach.intellijjj

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.VcsType
import com.intellij.openapi.vcs.changes.ChangeProvider
import com.intellij.openapi.vcs.checkin.CheckinEnvironment
import com.intellij.openapi.vcs.diff.DiffProvider
import com.intellij.openapi.vcs.history.VcsHistoryProvider
import com.intellij.openapi.vcs.rollback.RollbackEnvironment
import com.intellij.openapi.vcs.update.UpdateEnvironment
import net.chikach.intellijjj.commands.JujutsuCommandExecutor
import net.chikach.intellijjj.diff.JujutsuDiffProvider

class JujutsuVcs(project: Project) : AbstractVcs(project, "Jujutsu") {
    
    private val changeProvider = JujutsuChangeProvider(project, this)
    private val diffProvider = JujutsuDiffProvider(project, this)
    val commandExecutor = JujutsuCommandExecutor(project)
    
    override fun getDisplayName(): String = "Jujutsu"

    override fun getType(): VcsType = VcsType.distributed

    override fun getConfigurable(): Configurable? {
        return null // Will be provided by JujutsuConfigurableProvider
    }

    override fun getChangeProvider(): ChangeProvider = changeProvider

    override fun getCheckinEnvironment(): CheckinEnvironment? {
        return null // Jujutsu doesn't use traditional checkin
    }

    override fun getRollbackEnvironment(): RollbackEnvironment? {
        return null
    }

    override fun getDiffProvider(): DiffProvider = diffProvider

    override fun getVcsHistoryProvider(): VcsHistoryProvider? {
        return null
    }

    override fun getIntegrateEnvironment(): UpdateEnvironment? {
        return null
    }

    override fun getUpdateEnvironment(): UpdateEnvironment? {
        return null
    }

    companion object {
        const val VCS_NAME = "Jujutsu"
        private val ourKey = createKey(VCS_NAME)
        
        fun getKey(): VcsKey = ourKey
    }
}
