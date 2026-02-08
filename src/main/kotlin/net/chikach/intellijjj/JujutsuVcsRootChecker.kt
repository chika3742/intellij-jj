package net.chikach.intellijjj

import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.VcsRootChecker
import com.intellij.openapi.vfs.VirtualFile

/**
 * Detects Jujutsu roots by checking for a `.jj` directory.
 */
class JujutsuVcsRootChecker : VcsRootChecker() {
    override fun getSupportedVcs(): VcsKey = JujutsuVcsUtil.getKey()

    override fun isRoot(file: VirtualFile): Boolean {
        if (!file.isDirectory) return false
        val jjDir = file.findChild(JUJUTSU_DIR)
        return jjDir != null && jjDir.isDirectory
    }

    override fun isVcsDir(dirName: String): Boolean = dirName == JUJUTSU_DIR

    companion object {
        private const val JUJUTSU_DIR = ".jj"
    }
}
