package net.chikach.intellijjj

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
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
import net.chikach.intellijjj.commit.JujutsuCheckinEnvironment
import net.chikach.intellijjj.history.JujutsuHistoryProvider
import net.chikach.intellijjj.jujutsu.JujutsuCommandExecutor
import net.chikach.intellijjj.repo.JujutsuRepositoryChangeListener
import net.chikach.intellijjj.rollback.JujutsuRollbackEnvironment
import net.chikach.intellijjj.update.JujutsuUpdateEnvironment

/**
 * IntelliJ VCS entry point for the Jujutsu integration.
 *
 * This wires the platform extension points (change provider, diff provider,
 * check-in environment) and reacts to repository metadata updates by marking
 * affected roots dirty so the IDE refreshes state.
 */
class JujutsuVcs(project: Project) : AbstractVcs(project, "Jujutsu") {
    
    private val changeProvider = JujutsuChangeProvider(project, this)
    private val diffProvider = JujutsuDiffProvider(project, this)
    private val checkinEnvironment = JujutsuCheckinEnvironment(project, this)
    private val rollbackEnvironment = JujutsuRollbackEnvironment(project, this)
    private val historyProvider = JujutsuHistoryProvider(project, this)
    private val integrateEnvironment = JujutsuUpdateEnvironment("Integrate")
    private val updateEnvironment = JujutsuUpdateEnvironment("Update")
    private val dirtyScopeManager = VcsDirtyScopeManager.getInstance(project)

    val commandExecutor = JujutsuCommandExecutor(project)

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

    override fun getRollbackEnvironment(): RollbackEnvironment = rollbackEnvironment

    override fun getDiffProvider(): DiffProvider = diffProvider

    override fun getVcsHistoryProvider(): VcsHistoryProvider = historyProvider

    override fun getIntegrateEnvironment(): UpdateEnvironment = integrateEnvironment

    override fun getUpdateEnvironment(): UpdateEnvironment = updateEnvironment

    companion object {
        const val VCS_NAME = "Jujutsu"
    }
    
    /** Shared [VcsKey] holder used by registrations and providers. */
    object Key {
        val key: VcsKey = createKey(VCS_NAME)
    }
}
