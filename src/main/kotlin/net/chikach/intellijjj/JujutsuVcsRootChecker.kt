package net.chikach.intellijjj

import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.VcsRootChecker
import java.io.File

class JujutsuVcsRootChecker : VcsRootChecker() {
    @Deprecated("Deprecated in VcsRootChecker base class")
    override fun isRoot(path: String): Boolean {
        // This method is deprecated in the base class but is still the primary way
        // to implement VCS root detection in VcsRootChecker implementations.
        // Check for the existence of a .jj directory
        val jjDir = File(path, ".jj")
        return jjDir.exists() && jjDir.isDirectory
    }

    override fun getSupportedVcs(): VcsKey {
        // Get the VcsKey for Jujutsu
        return JujutsuVcs.getKey()
    }

    override fun isVcsDir(dirName: String): Boolean {
        // Check if the directory name is .jj (case-sensitive)
        return dirName == ".jj"
    }
}
