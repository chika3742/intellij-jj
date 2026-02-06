package net.chikach.intellijjj.repo

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsMappingListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.CommonProcessors
import com.intellij.vfs.AsyncVfsEventsListener
import com.intellij.vfs.AsyncVfsEventsPostProcessor
import net.chikach.intellijjj.JujutsuVcs
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

@Service(Service.Level.PROJECT)
class JujutsuRepositoryWatcher(
    private val project: Project,
    coroutineScope: CoroutineScope
) : Disposable, AsyncVfsEventsListener {
    private val log = Logger.getInstance(JujutsuRepositoryWatcher::class.java)
    private val localFs = LocalFileSystem.getInstance()

    @Volatile
    private var jjRoots: List<JjRoot> = emptyList()
    private var watchRequests: Set<LocalFileSystem.WatchRequest> = emptySet()

    init {
        updateRoots()
        AsyncVfsEventsPostProcessor.getInstance().addListener(this, coroutineScope)
        project.messageBus.connect(coroutineScope).subscribe(
            ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED,
            VcsMappingListener { updateRoots() }
        )
    }

    override fun dispose() {
        if (watchRequests.isNotEmpty()) {
            localFs.removeWatchedRoots(watchRequests)
            watchRequests = emptySet()
        }
    }

    override suspend fun filesChanged(events: List<VFileEvent>) {
        currentCoroutineContext().ensureActive()
        val rootsSnapshot = jjRoots
        if (events.isEmpty() || rootsSnapshot.isEmpty()) return

        val rootsToNotify = LinkedHashSet<VirtualFile>()
        for (event in events) {
            currentCoroutineContext().ensureActive()
            val eventPath = FileUtil.toSystemIndependentName(event.path)
            for (jjRoot in rootsSnapshot) {
                if (eventPath == jjRoot.path || eventPath.startsWith(jjRoot.pathPrefix)) {
                    rootsToNotify.add(jjRoot.root)
                }
            }
        }
        if (rootsToNotify.isEmpty()) return
        val publisher = project.messageBus.syncPublisher(JujutsuRepositoryChangeListener.TOPIC)
        for (root in rootsToNotify) {
            publisher.repositoryChanged(root)
        }
    }

    private fun updateRoots() {
        val vcs = JujutsuVcs.getInstance(project) ?: return
        val roots = ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(vcs)
        val newJjRoots = mutableListOf<JjRoot>()
        val newWatchRequests = mutableSetOf<LocalFileSystem.WatchRequest>()

        for (root in roots) {
            val jjDir = File(root.path, ".jj")
            if (!jjDir.isDirectory) continue
            val jjVfs = localFs.refreshAndFindFileByIoFile(jjDir)
            if (jjVfs != null) {
                VfsUtilCore.processFilesRecursively(jjVfs, CommonProcessors.alwaysTrue())
            }
            localFs.addRootToWatch(jjDir.path, true)?.let { newWatchRequests.add(it) }
            val jjPath = FileUtil.toSystemIndependentName(jjDir.path)
            newJjRoots.add(JjRoot(jjPath, "$jjPath/", root))
        }

        if (watchRequests.isNotEmpty()) {
            localFs.removeWatchedRoots(watchRequests)
        }
        watchRequests = newWatchRequests
        jjRoots = newJjRoots
        log.debug("Watching ${jjRoots.size} .jj roots")
    }

    private data class JjRoot(
        val path: String,
        val pathPrefix: String,
        val root: VirtualFile
    )
}
