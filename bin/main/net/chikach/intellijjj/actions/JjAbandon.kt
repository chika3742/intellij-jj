package net.chikach.intellijjj.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAwareAction

class JjAbandon : DumbAwareAction("Abandon Change") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    private val log = Logger.getInstance(JjAbandon::class.java)
    
    override fun actionPerformed(e: AnActionEvent) {
        e.getData<>()
        TODO("Not yet implemented")
    }
}