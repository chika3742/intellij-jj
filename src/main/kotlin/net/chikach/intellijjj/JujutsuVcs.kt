package net.chikach.intellijjj

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.VcsType
import com.intellij.openapi.vcs.changes.ChangeProvider
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.checkin.CheckinEnvironment
import com.intellij.openapi.vcs.diff.DiffProvider
import com.intellij.openapi.vcs.history.VcsHistoryProvider
import com.intellij.openapi.vcs.rollback.RollbackEnvironment
import com.intellij.openapi.vcs.update.UpdateEnvironment
import com.intellij.util.messages.MessageBusConnection
import net.chikach.intellijjj.commands.JujutsuCommandExecutor
import net.chikach.intellijjj.commit.JujutsuCheckinEnvironment
import net.chikach.intellijjj.diff.JujutsuDiffProvider
import net.chikach.intellijjj.repo.JujutsuRepositoryChangeListener

class JujutsuVcs(project: Project) : AbstractVcs(project, "Jujutsu") {
    
    private val changeProvider = JujutsuChangeProvider(project, this)
    private val diffProvider = JujutsuDiffProvider(project, this)
    private val checkinEnvironment = JujutsuCheckinEnvironment(project, this)
    val commandExecutor = JujutsuCommandExecutor(project)
    private val dirtyScopeManager = VcsDirtyScopeManager.getInstance(project)
    
    private var repoChangeListenerConnection: MessageBusConnection? = null

    override fun activate() {
        super.activate()
        repoChangeListenerConnection = project.messageBus.connect().apply {
            subscribe(JujutsuRepositoryChangeListener.TOPIC,
                JujutsuRepositoryChangeListener {
                    dirtyScopeManager.rootDirty(it)
                })
        }
    }

    override fun deactivate() {
        super.deactivate()
        repoChangeListenerConnection?.dispose()
    }
    
    override fun getDisplayName(): String = "Jujutsu"

    override fun getType(): VcsType = VcsType.distributed

    override fun getConfigurable(): Configurable? {
        return null // Will be provided by JujutsuConfigurableProvider
    }

    override fun getChangeProvider(): ChangeProvider = changeProvider

    override fun getCheckinEnvironment(): CheckinEnvironment = checkinEnvironment

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

        fun getInstance(project: Project): JujutsuVcs? {
            return ProjectLevelVcsManager.getInstance(project)
                .findVcsByName(VCS_NAME) as? JujutsuVcs
        }
    }
}
