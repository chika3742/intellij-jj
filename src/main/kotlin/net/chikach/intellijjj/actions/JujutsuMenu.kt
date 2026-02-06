package net.chikach.intellijjj.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.actions.StandardVcsGroup
import net.chikach.intellijjj.JujutsuVcs

class JujutsuMenu : StandardVcsGroup() {
    override fun getVcs(project: Project): AbstractVcs? = JujutsuVcs.getInstance(project)

    override fun getVcsName(project: Project): String = JujutsuVcs.VCS_NAME
}
