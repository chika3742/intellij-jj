package net.chikach.intellijjj

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.VcsType
import com.intellij.openapi.vcs.changes.ChangeProvider
import com.intellij.openapi.vcs.checkin.CheckinEnvironment
import com.intellij.openapi.vcs.diff.DiffProvider
import com.intellij.openapi.vcs.history.VcsHistoryProvider
import com.intellij.openapi.vcs.rollback.RollbackEnvironment
import com.intellij.openapi.vcs.update.UpdateEnvironment
import com.intellij.openapi.vfs.VirtualFile
import net.chikach.intellijjj.commands.JujutsuCommandExecutor
import net.chikach.intellijjj.diff.JujutsuDiffProvider
import net.chikach.intellijjj.JujutsuChangeProvider

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

    override fun isVersionedDirectory(dir: VirtualFile?): Boolean {
        if (dir == null || !dir.isDirectory) return false
        val jjDir = dir.findChild(".jj")
        return jjDir != null && jjDir.isDirectory
    }

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
        const val VCS_KEY = "Jujutsu"
        private val ourKey = createKey(VCS_NAME)
        
        fun getKey(): VcsKey = ourKey
        
        fun getInstance(project: Project): JujutsuVcs? {
            return ProjectLevelVcsManager.getInstance(project)
                .findVcsByName(VCS_NAME) as? JujutsuVcs
        }
    }
}
