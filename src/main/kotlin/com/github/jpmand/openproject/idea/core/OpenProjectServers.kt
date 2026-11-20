package com.github.jpmand.openproject.idea.core

import com.github.jpmand.openproject.idea.api.OpenProjectUrls
import com.github.jpmand.openproject.idea.tasks.OpenProjectRepository
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.tasks.TaskManager
import com.intellij.tasks.config.TaskRepositoriesConfigurable
import com.intellij.tasks.impl.LocalTaskImpl
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting

/** An OpenProject server configured as a Task Server; [id] is its normalized base URL. */
data class OpenProjectServer(val id: String, val displayName: String, val repository: OpenProjectRepository)

/**
 * How the configured servers changed (by server id, the normalized URL): [changed] servers have another API token
 * (or anonymous access was switched), [removed] ones are gone (also when their URL changed: the new URL is [added]).
 */
data class ServerChanges(val changed: Set<String>, val removed: Set<String>, val added: Set<String>) {
    /** Servers whose work packages, permissions and cached data may no longer be valid. */
    val stale: Set<String> get() = changed + removed
}

/** Told on the EDT when the URL or API token of an OpenProject Task Server changed, or a server was added or removed. */
fun interface OpenProjectServersListener {
    fun serversChanged(changes: ServerChanges)

    companion object {
        @Topic.ProjectLevel
        @JvmField
        val TOPIC: Topic<OpenProjectServersListener> = Topic(OpenProjectServersListener::class.java, Topic.BroadcastDirection.NONE)
    }
}

/**
 * The OpenProject servers of a project: the Task Servers of type "OpenProject" (Settings | Tools | Tasks | Servers).
 * That configuration is the single source of URL and API token for every feature of the plugin.
 *
 * The Tasks plugin does not announce configuration changes, so [checkForChanges] compares the servers with what it
 * saw before whenever they may have changed (the settings were closed, a list is refreshed) and publishes
 * [OpenProjectServersListener.TOPIC].
 */
@Service(Service.Level.PROJECT)
class OpenProjectServers(private val project: Project, private val cs: CoroutineScope) {

    private val checking = Mutex()

    /**
     * Server id -> [OpenProjectRepository.connectionKey] as last seen; null until first looked at (when the tool window
     * or the server settings are opened: tokens are not read before they are needed).
     */
    @Volatile
    private var known: Map<String, String>? = null

    fun servers(): List<OpenProjectServer> =
        TaskManager.getManager(project).allRepositories
            .filterIsInstance<OpenProjectRepository>()
            .filter { it.url.isNotBlank() }
            .map { OpenProjectServer(OpenProjectUrls.normalizeBaseUrl(it.url), it.presentableName, it) }
            .distinctBy { it.id }

    fun find(serverId: String?): OpenProjectServer? =
        servers().let { all -> all.firstOrNull { it.id == serverId } ?: all.firstOrNull().takeIf { serverId == null } }

    /** Reads the token from the password safe, so it must not run on the EDT. */
    @RequiresBackgroundThread
    fun connection(server: OpenProjectServer): OpenProjectConnection = server.repository.connection()

    /** Opens Settings | Tools | Tasks | Servers. */
    fun openServerSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, TaskRepositoriesConfigurable::class.java)
        checkForChanges()
    }

    /** Looks for changed servers in the background (see [detectChanges]). */
    fun checkForChanges() {
        if (project.isDefault) return
        cs.launch { detectChanges() }
    }

    /**
     * Compares the servers with the ones seen before. After a change, the work packages of the active and other local
     * tasks get their server back (Tasks unlinks them from a changed server) and [OpenProjectServersListener.TOPIC]
     * is told on the EDT (once no modal dialog, such as the settings, is shown). Returns the changes, if any.
     */
    @VisibleForTesting
    suspend fun detectChanges(): ServerChanges? = checking.withLock {
        // The tokens come from the password safe.
        val current = withContext(Dispatchers.IO) { servers().associate { it.id to it.repository.connectionKey() } }
        val previous = known
        known = current
        if (previous == null) return@withLock null
        val changes = ServerChanges(
            changed = current.keys.filter { it in previous && previous[it] != current[it] }.toSet(),
            removed = previous.keys - current.keys,
            added = current.keys - previous.keys,
        ).takeIf { it.stale.isNotEmpty() || it.added.isNotEmpty() }
        withContext(Dispatchers.EDT) {
            if (project.isDisposed) return@withContext
            relinkTasks()
            ActiveWorkPackageTracker.getInstance(project).refresh()
            if (changes != null) {
                thisLogger().info("OpenProject servers changed: $changes")
                project.messageBus.syncPublisher(OpenProjectServersListener.TOPIC).serversChanged(changes)
            }
        }
        changes
    }

    /**
     * Any change of a Task Server replaces it, and Tasks then unlinks the local tasks of the old one: Close Task would no
     * longer offer to update the status. Links the work packages of local tasks to their current server again.
     */
    @RequiresEdt
    private fun relinkTasks() {
        val servers = servers()
        for (task in TaskManager.getManager(project).localTasks) {
            if (task.isDefault) continue
            val repository = task.repository
            if (repository != null && (repository !is OpenProjectRepository || servers.any { it.repository === repository })) continue
            val url = task.issueUrl ?: continue
            val server = servers.firstOrNull { url.startsWith(it.id + "/work_packages/") } ?: continue
            (task as? LocalTaskImpl)?.setRepository(server.repository)
        }
    }

    companion object {
        fun getInstance(project: Project): OpenProjectServers = project.service()
    }
}
