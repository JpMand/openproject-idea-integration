package com.github.jpmand.openproject.idea.core

import com.github.jpmand.openproject.idea.api.OpenProjectUrls
import com.github.jpmand.openproject.idea.api.model.WorkPackage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.messages.Topic

/**
 * A work package was changed through the plugin: on its page, by Log Time or by a status update of Open Task or
 * Close Task. [workPackage] is its new state when known, null when it has to be loaded again; [source] is the one who
 * changed it (it shows the change already).
 */
data class WorkPackageChange(
    val serverId: String,
    val workPackageId: Long,
    val workPackage: WorkPackage? = null,
    val source: Any? = null,
)

/** Told on the EDT, once no modal dialog is shown, about [WorkPackageChange]s, so that lists and pages showing the work package catch up. */
fun interface WorkPackageChangesListener {
    fun workPackageChanged(change: WorkPackageChange)

    companion object {
        @Topic.ProjectLevel
        @JvmField
        val TOPIC: Topic<WorkPackageChangesListener> = Topic(WorkPackageChangesListener::class.java, Topic.BroadcastDirection.NONE)

        /** Tells the lists and pages of [project] about [change]; may be called from any thread. */
        fun publish(project: Project, change: WorkPackageChange) {
            ApplicationManager.getApplication().invokeLater({
                project.messageBus.syncPublisher(TOPIC).workPackageChanged(change)
            }, ModalityState.nonModal(), project.disposed)
        }

        /** Tells every open project about a change of a work package of the server at [serverUrl] (Tasks does not know the project). */
        fun publishToAll(serverUrl: String, workPackageId: Long) {
            val serverId = OpenProjectUrls.normalizeBaseUrl(serverUrl)
            ProjectManager.getInstance().openProjects.forEach { publish(it, WorkPackageChange(serverId, workPackageId)) }
        }
    }
}
