package net.chikach.intellijjj.update

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.update.SequentialUpdatesContext
import com.intellij.openapi.vcs.update.UpdateEnvironment
import com.intellij.openapi.vcs.update.UpdateSession
import com.intellij.openapi.vcs.update.UpdateSessionAdapter
import com.intellij.openapi.vcs.update.UpdatedFiles

/**
 * Update/integrate environment placeholder for Jujutsu.
 */
class JujutsuUpdateEnvironment(
    private val operationName: String,
) : UpdateEnvironment {
    override fun fillGroups(updatedFiles: UpdatedFiles) = Unit

    @Throws(ProcessCanceledException::class)
    override fun updateDirectories(
        filePaths: Array<out FilePath>,
        updatedFiles: UpdatedFiles,
        progressIndicator: ProgressIndicator,
        sequentialUpdatesContextRef: Ref<SequentialUpdatesContext>,
    ): UpdateSession {
        val message = "$operationName is not supported for Jujutsu repositories yet."
        return UpdateSessionAdapter(listOf(VcsException(message)), false)
    }

    override fun createConfigurable(files: Collection<FilePath>): Configurable? = null

    override fun validateOptions(files: Collection<FilePath>): Boolean = true
}
