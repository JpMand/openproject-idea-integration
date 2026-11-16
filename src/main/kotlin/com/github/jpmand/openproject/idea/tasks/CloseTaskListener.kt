package com.github.jpmand.openproject.idea.tasks

import com.github.jpmand.openproject.idea.OpenProjectBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.tasks.LocalTask
import com.intellij.tasks.TaskManager

/**
 * Confirms Tools | Tasks & Contexts | Close Task for OpenProject work packages. The Tasks plugin only marks the
 * local task closed (it stays the active task) and, when asked to, sets the status: without this notification
 * closing looks like nothing happened. Failures to set the status are reported by the Tasks plugin itself.
 */
class CloseTaskListener : AnActionListener {

    /** The active task when Close Task started, and whether it was closed already. */
    private var pending: Pair<LocalTask, Boolean>? = null

    override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
        if (event.actionManager.getId(action) != CLOSE_TASK_ACTION) return
        val task = event.project?.let { TaskManager.getManager(it).activeTask }?.takeIf { it.repository is OpenProjectRepository }
        pending = task?.let { it to it.isClosed }
    }

    override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
        if (event.actionManager.getId(action) != CLOSE_TASK_ACTION) return
        val (task, wasClosed) = pending ?: return
        pending = null
        val project = event.project ?: return
        // Cancelling the dialog leaves the task as it was.
        if (!task.isClosed || wasClosed) return
        val status = (task.repository as? OpenProjectRepository)?.takeStateChange(task.id)
        notifyClosed(project, task, status)
    }

    companion object {
        const val CLOSE_TASK_ACTION = "tasks.close"

        fun message(task: LocalTask, status: String?): String {
            val name = "${task.presentableId} ${task.summary}"
            return if (status == null) {
                OpenProjectBundle.message("task.closed", name)
            } else {
                OpenProjectBundle.message("task.closed.status", name, status)
            }
        }

        private fun notifyClosed(project: Project, task: LocalTask, status: String?) {
            val message = message(task, status)
            logger<CloseTaskListener>().info(message)
            NotificationGroupManager.getInstance().getNotificationGroup("OpenProject")
                .createNotification(message, NotificationType.INFORMATION)
                .notify(project)
        }
    }
}
