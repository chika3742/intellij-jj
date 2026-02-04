package net.chikach.intellijjj

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.changes.ChangeProvider
import com.intellij.openapi.vcs.checkin.CheckinEnvironment
import com.intellij.openapi.vcs.diff.DiffProvider
import com.intellij.openapi.vcs.history.VcsHistoryProvider
import com.intellij.openapi.vcs.rollback.RollbackEnvironment
import com.intellij.openapi.vcs.update.UpdateEnvironment
import net.chikach.intellijjj.commands.JujutsuCommandExecutor
import net.chikach.intellijjj.vcs.JujutsuChangeProvider

class JujutsuVcs(project: Project) : AbstractVcs(project, VCS_NAME) {
    
    private val changeProvider = JujutsuChangeProvider(project, this)
    val commandExecutor = JujutsuCommandExecutor(project)
    
    override fun getDisplayName(): String = "Jujutsu"

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

    override fun getDiffProvider(): DiffProvider? {
        return null
    }

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
        
        @JvmStatic
        private val vcsKey: VcsKey by lazy {
            // Use reflection to access AbstractVcs.getKeyInstanceMethod()
            // This is the standard mechanism that AbstractVcs uses internally to create VcsKeys.
            // While reflection-based, this approach follows the pattern used by the platform itself
            // and is necessary since getKeyInstanceMethod() is not part of the public API.
            val keyMethod = AbstractVcs::class.java.getMethod("getKeyInstanceMethod")
            val method = keyMethod.invoke(null) as java.lang.reflect.Method
            method.invoke(null, VCS_NAME) as VcsKey
        }
        
        @JvmStatic
        fun getInstance(project: Project): JujutsuVcs? {
            return ProjectLevelVcsManager.getInstance(project)
                .findVcsByName(VCS_NAME) as? JujutsuVcs
        }
        
        @JvmStatic
        fun getKey(): VcsKey = vcsKey
    }
}
