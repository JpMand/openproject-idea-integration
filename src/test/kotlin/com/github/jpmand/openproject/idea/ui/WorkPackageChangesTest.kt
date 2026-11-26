package com.github.jpmand.openproject.idea.ui

import com.github.jpmand.openproject.idea.api.MockOpenProject
import com.github.jpmand.openproject.idea.api.MockOpenProject.Companion.collection
import com.github.jpmand.openproject.idea.api.MockOpenProject.Companion.hal
import com.github.jpmand.openproject.idea.api.MockOpenProject.Companion.notFound
import com.github.jpmand.openproject.idea.api.MockOpenProject.Companion.workPackage
import com.github.jpmand.openproject.idea.api.OpenProjectApi
import com.github.jpmand.openproject.idea.api.hal.HalJson
import com.github.jpmand.openproject.idea.core.ActiveWorkPackageTracker
import com.github.jpmand.openproject.idea.core.OpenProjectServers
import com.github.jpmand.openproject.idea.core.WorkPackageChange
import com.github.jpmand.openproject.idea.core.WorkPackageChangesListener
import com.github.jpmand.openproject.idea.tasks.OpenProjectRepository
import com.github.jpmand.openproject.idea.tasks.OpenProjectRepositoryType
import com.github.jpmand.openproject.idea.ui.detail.WorkPackageDetailPanel
import com.github.jpmand.openproject.idea.ui.list.WorkPackageListPanel
import com.github.jpmand.openproject.idea.ui.list.WorkPackageListViews
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.tasks.TaskManager
import com.intellij.tasks.TaskRepositoryType
import com.intellij.tasks.impl.TaskManagerImpl
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.jsonObject

/** A work package changed on its page, by Log Time or by a task status update is shown changed in lists and its other pages. */
class WorkPackageChangesTest : BasePlatformTestCase() {

    private lateinit var server: MockOpenProject

    @Volatile
    private var subject = "Fix the login"

    override fun setUp() {
        super.setUp()
        server = MockOpenProject(testRootDisposable)
        server.handler = { request ->
            when (request.url.encodedPath) {
                "/api/v3/work_packages" -> hal(collection(workPackage(42, subject), workPackage(7, "Write the docs")))
                "/api/v3/work_packages/42" -> hal(workPackage(42, subject))
                "/api/v3/work_packages/7" -> hal(workPackage(7, "Write the docs"))
                "/api/v3/work_packages/8" -> hal(workPackage(8, "Track the time").replace("\"_links\":{", "\"_links\":{$LOG_TIME"))
                else -> notFound()
            }
        }
        val type = TaskRepositoryType.getRepositoryTypes().filterIsInstance<OpenProjectRepositoryType>().single()
        val repository = (type.createRepository() as OpenProjectRepository).apply {
            url = SERVER
            password = "token"
        }
        (TaskManager.getManager(project) as TaskManagerImpl).setRepositories(listOf(repository))
    }

    override fun tearDown() {
        try {
            (TaskManager.getManager(project) as TaskManagerImpl).setRepositories(emptyList<OpenProjectRepository>())
            // Lists store their view per server in the (shared) test project.
            WorkPackageListViews.getInstance(project).remove(SERVER)
        } finally {
            super.tearDown()
        }
    }

    private fun waitFor(what: String, condition: () -> Boolean) = PlatformTestUtil.waitWithEventsDispatching(what, condition, 10)

    private fun loads(id: Long) = server.requests(HttpMethod.Get, "/api/v3/work_packages/$id").size

    private fun publish(change: WorkPackageChange) {
        WorkPackageChangesListener.publish(project, change)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    private fun subjects(list: WorkPackageListPanel) = list.loadedWorkPackages().map { it.subject }

    fun `test a changed work package replaces its row in place`() {
        val list = WorkPackageListPanel(project, null, null)
        try {
            waitFor("rows") { subjects(list) == listOf("Fix the login", "Write the docs") }
            val changed = list.loadedWorkPackages().first().copy(subject = "Fix the login page")
            publish(WorkPackageChange(SERVER, 42, changed, source = this))
            assertEquals(listOf("Fix the login page", "Write the docs"), subjects(list))
            // The row was replaced, not the list loaded again.
            assertEquals(1, server.requests(HttpMethod.Get, "/api/v3/work_packages").size)
        } finally {
            Disposer.dispose(list)
        }
    }

    fun `test a change without the new state loads the row again`() {
        val list = WorkPackageListPanel(project, null, null)
        try {
            waitFor("rows") { list.loadedWorkPackages().size == 2 }
            subject = "Fix the login (closed)"
            // As Open Task and Close Task report a status they set.
            WorkPackageChangesListener.publishToAll("$SERVER/", 42)
            waitFor("changed row") { subjects(list) == listOf("Fix the login (closed)", "Write the docs") }
            assertEquals(1, loads(42))
        } finally {
            Disposer.dispose(list)
        }
    }

    fun `test changes of work packages the list does not show are ignored`() {
        val list = WorkPackageListPanel(project, null, null)
        try {
            waitFor("rows") { list.loadedWorkPackages().size == 2 }
            publish(WorkPackageChange(SERVER, 99))
            publish(WorkPackageChange("https://other.example.com", 42))
            val settled = System.currentTimeMillis() + 500
            waitFor("settled") { System.currentTimeMillis() > settled }
            assertEquals(0, loads(99) + loads(42))
            assertEquals(listOf("Fix the login", "Write the docs"), subjects(list))
        } finally {
            Disposer.dispose(list)
        }
    }

    fun `test other pages of a changed work package load it again`() {
        val page = WorkPackageDetailPanel(project, SERVER, 42, WorkPackageDetailPanel.Layout.TAB)
        val preview = WorkPackageDetailPanel(project, SERVER, 42, WorkPackageDetailPanel.Layout.PREVIEW)
        try {
            waitFor("both pages") { page.controller.details.value != null && preview.controller.details.value != null }
            assertEquals(2, loads(42))
            subject = "Fix the login page"
            // Changed on the tab: the preview catches up, the tab (which made the change) does not load it again.
            publish(WorkPackageChange(SERVER, 42, page.controller.details.value!!.workPackage, source = page.controller))
            waitFor("preview reloaded") { preview.controller.details.value?.workPackage?.subject == "Fix the login page" }
            val settled = System.currentTimeMillis() + 500
            waitFor("settled") { System.currentTimeMillis() > settled }
            assertEquals(3, loads(42))
            assertEquals("Fix the login", page.controller.details.value!!.workPackage.subject)
        } finally {
            Disposer.dispose(page)
            Disposer.dispose(preview)
        }
    }

    fun `test log time of the banner is for the active task, not the selected row`() {
        val list = WorkPackageListPanel(project, null, null)
        try {
            waitFor("rows") { list.loadedWorkPackages().size == 2 }
            val active = list.loadedWorkPackages().single { it.id == 7L }
            ActiveWorkPackageTracker.getInstance(project).activate(OpenProjectServers.getInstance(project).servers().single(), active)
            // The banner (and its Log Time link) shows the active task once the list has followed the activation.
            waitFor("banner") { list.bannerWorkPackage()?.workPackageId == 7L }
            list.select(42)
            // #7 has no "logTime" link: Log Time loads it and tells that time cannot be logged, without a dialog.
            list.logTimeFromBanner()
            waitFor("active work package loaded") { loads(7) == 1 }
            assertEquals(0, loads(42))
        } finally {
            Disposer.dispose(list)
            TaskManager.getManager(project).let { manager ->
                manager.activateTask(manager.localTasks.first { it.isDefault }, false)
                manager.localTasks.filterNot { it.isDefault }.forEach(manager::removeTask)
            }
        }
    }

    fun `test log time of the status bar widget is greyed out when time cannot be logged`() {
        val servers = OpenProjectServers.getInstance(project).servers().single()
        val tracker = ActiveWorkPackageTracker.getInstance(project)
        val widget = ActiveWorkPackageWidget(project)
        try {
            assertNull(widget.canLogTime())
            // #7 has no "logTime" link: Log Time is greyed out once the widget has loaded it.
            tracker.activate(servers, decode(workPackage(7, "Write the docs")))
            waitFor("#7 loaded") { widget.canLogTime() == false }
            assertFalse(logTimeEnabled(widget))
            // #8 has one.
            tracker.activate(servers, decode(workPackage(8, "Track the time")))
            waitFor("#8 loaded") { widget.canLogTime() == true }
            assertTrue(logTimeEnabled(widget))
            // A change of the active work package tells again whether time can be logged.
            publish(WorkPackageChange(SERVER, 8, decode(workPackage(8, "Track the time"))))
            assertEquals(false, widget.canLogTime())
        } finally {
            Disposer.dispose(widget)
            TaskManager.getManager(project).let { manager ->
                manager.activateTask(manager.localTasks.first { it.isDefault }, false)
                manager.localTasks.filterNot { it.isDefault }.forEach(manager::removeTask)
            }
        }
    }

    private fun decode(json: String) = OpenProjectApi.decodeWorkPackage(HalJson.read.parseToJsonElement(json).jsonObject)

    private fun logTimeEnabled(widget: ActiveWorkPackageWidget): Boolean {
        val logTime = widget.actions().getChildren(null).last()
        val event = AnActionEvent.createEvent(logTime, DataContext.EMPTY_CONTEXT, null, ActionPlaces.POPUP, ActionUiKind.POPUP, null)
        logTime.update(event)
        return event.presentation.isEnabled
    }

    private companion object {
        const val SERVER = "https://changes.example.com"
        const val LOG_TIME = """"logTime":{"href":"/api/v3/time_entries","method":"post"},"""
    }
}
