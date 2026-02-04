package net.chikach.intellijjj

import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.VcsRootChecker
import java.io.File

class JujutsuVcsRootChecker : VcsRootChecker() {
    @Deprecated("Deprecated in VcsRootChecker")
    override fun isRoot(path: String): Boolean {
        // Check for the existence of a .jj directory
        val jjDir = File(path, ".jj")
        return jjDir.exists() && jjDir.isDirectory
    }

    override fun getSupportedVcs(): VcsKey {
        // Get the VcsKey for Jujutsu
        return JujutsuVcs.getKey()
    }

    override fun isVcsDir(dirName: String): Boolean {
        // Check if the directory name is .jj
        return ".jj".equals(dirName, ignoreCase = true)
    }
}
