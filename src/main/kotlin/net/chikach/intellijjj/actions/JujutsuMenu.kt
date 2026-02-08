package net.chikach.intellijjj.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.actions.StandardVcsGroup
import net.chikach.intellijjj.JujutsuVcs
import net.chikach.intellijjj.JujutsuVcsUtil

/**
 * VCS main-menu group shown for Jujutsu roots.
 */
class JujutsuMenu : StandardVcsGroup() {
    override fun getVcs(project: Project): AbstractVcs? = JujutsuVcsUtil.getInstance(project)

    override fun getVcsName(project: Project): String = JujutsuVcs.VCS_NAME
}
