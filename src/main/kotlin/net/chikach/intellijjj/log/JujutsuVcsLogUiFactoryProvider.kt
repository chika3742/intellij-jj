@file:Suppress("UnstableApiUsage")

package net.chikach.intellijjj.log

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.impl.CustomVcsLogUiFactoryProvider
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsLogTabsProperties
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.VcsLogColorManager
import com.intellij.vcs.log.ui.VcsLogUiImpl
import com.intellij.vcs.log.visible.VisiblePackRefresherImpl
import net.chikach.intellijjj.JujutsuVcsUtil

/**
 * Replaces the default VCS Log UI factory when all visible roots are Jujutsu.
 */
class JujutsuVcsLogUiFactoryProvider : CustomVcsLogUiFactoryProvider {
    override fun isActive(providers: Map<VirtualFile, VcsLogProvider>): Boolean {
        if (providers.isEmpty()) return false
        val key = JujutsuVcsUtil.getKey()
        return providers.values.all { it.supportedVcs == key }
    }

    override fun createLogUiFactory(
        logId: String,
        vcsLogManager: VcsLogManager,
        filters: VcsLogFilterCollection?
    ): VcsLogManager.VcsLogUiFactory<out MainVcsLogUi> {
        return JujutsuLogUiFactory(logId, filters, vcsLogManager.uiProperties, vcsLogManager.colorManager)
    }
}

/**
 * Creates the Jujutsu-specific VCS log UI implementation.
 */
private class JujutsuLogUiFactory(
    logId: String,
    filters: VcsLogFilterCollection?,
    uiProperties: VcsLogTabsProperties,
    colorManager: VcsLogColorManager
) : VcsLogManager.BaseVcsLogUiFactory<VcsLogUiImpl>(logId, filters, uiProperties, colorManager) {
    override fun createVcsLogUiImpl(
        logId: String,
        logData: com.intellij.vcs.log.data.VcsLogData,
        properties: MainVcsLogUiProperties,
        colorManager: VcsLogColorManager,
        refresher: VisiblePackRefresherImpl,
        filters: VcsLogFilterCollection?
    ): VcsLogUiImpl {
        return JujutsuVcsLogUiImpl(logId, logData, colorManager, properties, refresher, filters)
    }
}
