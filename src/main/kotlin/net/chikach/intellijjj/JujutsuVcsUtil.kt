package net.chikach.intellijjj

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import net.chikach.intellijjj.jujutsu.JujutsuCommandExecutor
import java.util.Collections
import java.util.WeakHashMap

/**
 * Helpers for looking up Jujutsu VCS services and keys from IntelliJ APIs.
 */
object JujutsuVcsUtil {
    private val fallbackExecutors =
        Collections.synchronizedMap(WeakHashMap<Project, JujutsuCommandExecutor>())

    fun getKey() = JujutsuVcs.Key.key

    fun getInstance(project: Project): JujutsuVcs? {
        return ProjectLevelVcsManager.getInstance(project)
            .findVcsByName(JujutsuVcs.VCS_NAME) as? JujutsuVcs
    }

    /**
     * Returns the shared command executor when the Jujutsu VCS is available, otherwise
     * returns a fallback instance cached per project.
     */
    fun getCommandExecutor(project: Project): JujutsuCommandExecutor {
        val vcs = getInstance(project)
        if (vcs != null) {
            fallbackExecutors.remove(project)
            return vcs.commandExecutor
        }
        return fallbackExecutors.getOrPut(project) { JujutsuCommandExecutor(project) }
    }
}
