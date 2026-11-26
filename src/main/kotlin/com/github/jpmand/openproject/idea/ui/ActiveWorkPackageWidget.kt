package com.github.jpmand.openproject.idea.ui

import com.github.jpmand.openproject.idea.OpenProjectBundle
import com.github.jpmand.openproject.idea.actions.LogTimeAction
import com.github.jpmand.openproject.idea.core.ActiveWorkPackage
import com.github.jpmand.openproject.idea.core.ActiveWorkPackageTracker
import com.github.jpmand.openproject.idea.core.OpenProjectProjectScope
import com.github.jpmand.openproject.idea.core.TimeEntries
import com.github.jpmand.openproject.idea.core.cancelJob
import com.github.jpmand.openproject.idea.core.WorkPackageChange
import com.github.jpmand.openproject.idea.core.WorkPackageChangesListener
import com.github.jpmand.openproject.idea.core.connect
import com.github.jpmand.openproject.idea.ui.toolwindow.OpenProjectToolWindowManager
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.Icon

/** Shows the work package of the active task in the status bar; a click offers what can be done with it. */
class ActiveWorkPackageWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = ActiveWorkPackageWidget.ID

    override fun getDisplayName(): String = OpenProjectBundle.message("widget.name")

    override fun createWidget(project: Project): StatusBarWidget = ActiveWorkPackageWidget(project)
}

// No compatibility bridges: they would override StatusBarWidget's deprecated getPresentation(PlatformType).
@JvmDefaultWithoutCompatibility
class ActiveWorkPackageWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {

    private val cs = OpenProjectProjectScope.getInstance(project).childScope("ActiveWorkPackageWidget")
    private val tracker get() = ActiveWorkPackageTracker.getInstance(project)

    /**
     * Whether time can be logged on the active work package: null while unknown (not loaded yet, or the load failed).
     * Log Time loads the work package again anyway, so only a known "no" greys it out.
     */
    @Volatile
    private var canLogTime: Boolean? = null
    private var permissionLoad: Job? = null
    private var statusBar: StatusBar? = null

    init {
        project.messageBus.connect(this).subscribe(WorkPackageChangesListener.TOPIC, WorkPackageChangesListener(::workPackageChanged))
        cs.launch {
            tracker.active.collect { active ->
                withContext(Dispatchers.EDT) {
                    loadLogTimePermission(active)
                    statusBar?.updateWidget(ID)
                }
            }
        }
    }

    override fun ID(): String = ID

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    /** "AGILE-1 Subject", shortened with an ellipsis: a long subject would take the room of the other widgets. */
    override fun getText(): String = tracker.active.value
        ?.let { StringUtil.shortenTextWithEllipsis(OpenProjectBundle.message("widget.text", it.presentableId, it.summary), MAX_TEXT_LENGTH, 0, true) }
        .orEmpty()

    override fun getTooltipText(): String? = tracker.active.value?.let { OpenProjectBundle.message("widget.tooltip", it.presentableId, it.summary) }

    override fun getAlignment(): Float = 0f

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer { event ->
        val component = event.component ?: return@Consumer
        if (tracker.active.value == null) return@Consumer
        val popup = JBPopupFactory.getInstance().createActionGroupPopup(
            null,
            actions(),
            DataManager.getInstance().getDataContext(component),
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
            true,
        )
        // Above the status bar, like the popups of the IDE's own widgets.
        popup.show(RelativePoint(component, Point(0, -popup.content.preferredSize.height)))
    }

    /** Open Work Package, Open in Browser and Log Time for the active task's work package. */
    internal fun actions(): ActionGroup = DefaultActionGroup(
        ActiveAction(OpenProjectBundle.message("widget.action.open"), AllIcons.Actions.OpenNewTab) { active ->
            OpenProjectToolWindowManager.getInstance(project).openWorkPackage(active.serverId, active.workPackageId)
        },
        ActiveAction(OpenProjectBundle.message("widget.action.browser"), AllIcons.General.Web) { active ->
            BrowserUtil.browse("${active.serverId}/work_packages/${active.workPackageId}")
        },
        ActiveAction(OpenProjectBundle.message("widget.action.log.time"), AllIcons.Vcs.History, enabled = { canLogTime != false }) { active ->
            LogTimeAction.logTime(project, active.serverId, active.workPackageId)
        },
    )

    /**
     * Finds out in the background whether time can be logged on [active], for Log Time of the popup. Called on the EDT,
     * where the result is stored too: a load cancelled meanwhile (another task was activated) does not overwrite it.
     */
    private fun loadLogTimePermission(active: ActiveWorkPackage?) {
        permissionLoad?.cancelJob()
        canLogTime = null
        if (active == null) return
        permissionLoad = cs.launch {
            try {
                val (_, connection) = connect(project, active.serverId)
                val allowed = TimeEntries.canLogTime(connection.api.workPackage(active.workPackageId))
                withContext(Dispatchers.EDT) { canLogTime = allowed }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                thisLogger().info("Could not load ${active.presentableId} to find out whether time can be logged on it", e)
            }
        }
    }

    /** Time tracking may have been switched on or off for the active work package meanwhile (told on the EDT). */
    private fun workPackageChanged(change: WorkPackageChange) {
        val active = tracker.active.value ?: return
        if (change.serverId != active.serverId || change.workPackageId != active.workPackageId) return
        val workPackage = change.workPackage
        if (workPackage != null) {
            permissionLoad?.cancelJob()
            canLogTime = TimeEntries.canLogTime(workPackage)
        } else {
            loadLogTimePermission(active)
        }
    }

    /** Whether Log Time of the popup is enabled; null while that is not known. */
    @TestOnly
    internal fun canLogTime(): Boolean? = canLogTime

    override fun dispose() {
        cs.cancel()
    }

    private inner class ActiveAction(
        text: String,
        icon: Icon,
        private val enabled: () -> Boolean = { true },
        private val perform: (ActiveWorkPackage) -> Unit,
    ) : DumbAwareAction(text, null, icon) {
        override fun getActionUpdateThread() = ActionUpdateThread.BGT

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = tracker.active.value != null && enabled()
        }

        override fun actionPerformed(e: AnActionEvent) {
            tracker.active.value?.let(perform)
        }
    }

    companion object {
        const val ID = "OpenProject.ActiveWorkPackage"

        /** The longest text shown in the status bar, ellipsis included. */
        internal const val MAX_TEXT_LENGTH = 60
    }
}
