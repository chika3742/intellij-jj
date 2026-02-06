package net.chikach.intellijjj

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager

object JujutsuVcsUtil {
    fun getKey() = JujutsuVcs.Key.key

    fun getInstance(project: Project): JujutsuVcs? {
        return ProjectLevelVcsManager.getInstance(project)
            .findVcsByName(JujutsuVcs.VCS_NAME) as? JujutsuVcs
    }
}