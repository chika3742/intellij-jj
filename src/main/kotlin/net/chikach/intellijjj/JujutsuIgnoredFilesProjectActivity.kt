package net.chikach.intellijjj

import com.intellij.ide.util.RunOnceUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Ensures `.jj` is added to the IDE ignored files list once per IDE installation.
 */
class JujutsuIgnoredFilesProjectActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val application = ApplicationManager.getApplication()
        if (application.isUnitTestMode) return

        RunOnceUtil.runOnceForApp(RUN_ONCE_ID) {
            val fileTypeManager = FileTypeManager.getInstance()
            val ignoredList = fileTypeManager.ignoredFilesList
            val ignoredPatterns = ignoredList.split(';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (ignoredPatterns.any { it == JJ_IGNORE_PATTERN }) return@runOnceForApp

            val newList = buildString {
                append(ignoredList)
                if (ignoredList.isNotEmpty() && !ignoredList.endsWith(";")) {
                    append(";")
                }
                append(JJ_IGNORE_PATTERN)
            }
            application.invokeLater {
                application.runWriteAction { fileTypeManager.ignoredFilesList = newList }
            }
        }
    }

    private companion object {
        private const val JJ_IGNORE_PATTERN = ".jj"
        private const val RUN_ONCE_ID = "net.chikach.intellij-jj.ignore-jj-directory"
    }
}
