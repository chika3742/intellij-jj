package net.chikach.intellijjj

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsConfigurableProvider

class JujutsuConfigurableProvider : VcsConfigurableProvider {
    override fun getConfigurable(project: Project): Configurable? {
        return null // No configuration UI for now
    }
}
