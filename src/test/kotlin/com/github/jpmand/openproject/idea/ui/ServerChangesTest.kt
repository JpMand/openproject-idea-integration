package com.github.jpmand.openproject.idea.ui

import com.github.jpmand.openproject.idea.api.Fixtures
import com.github.jpmand.openproject.idea.api.MockOpenProject
import com.github.jpmand.openproject.idea.api.MockOpenProject.Companion.basic
import com.github.jpmand.openproject.idea.api.MockOpenProject.Companion.collection
import com.github.jpmand.openproject.idea.api.MockOpenProject.Companion.hal
import com.github.jpmand.openproject.idea.api.MockOpenProject.Companion.notFound
import com.github.jpmand.openproject.idea.api.MockOpenProject.Companion.workPackage
import com.github.jpmand.openproject.idea.api.OpenProjectApi
import com.github.jpmand.openproject.idea.core.ActiveWorkPackageTracker
import com.github.jpmand.openproject.idea.core.OpenProjectProjectScope
import com.github.jpmand.openproject.idea.core.OpenProjectServers
import com.github.jpmand.openproject.idea.core.ServerChanges
import com.github.jpmand.openproject.idea.tasks.OpenProjectRepository
import com.github.jpmand.openproject.idea.tasks.OpenProjectRepositoryType
import com.github.jpmand.openproject.idea.ui.list.ListViewState
import com.github.jpmand.openproject.idea.ui.list.WorkPackageListViews
import com.github.jpmand.openproject.idea.ui.toolwindow.OpenProjectToolWindowFactory
import com.github.jpmand.openproject.idea.ui.toolwindow.OpenProjectToolWindowManager
import com.github.jpmand.openproject.idea.ui.toolwindow.OpenProjectToolWindowManager.Companion.LIST
import com.github.jpmand.openproject.idea.ui.toolwindow.OpenProjectToolWindowManager.Companion.TOOL_WINDOW_ID
import com.github.jpmand.openproject.idea.ui.toolwindow.OpenProjectToolWindowManager.Companion.WORK_PACKAGE
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.tasks.TaskManager
import com.intellij.tasks.TaskRepositoryType
import com.intellij.tasks.impl.TaskManagerImpl
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.ktor.http.HttpMethod
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

/**
 * A changed URL or API token of the Task Server (Settings | Tools | Tasks | Servers) reaches the tool window and the
 * active task.
 */
class ServerChangesTest : BasePlatformTestCase() {

    private lateinit var server: MockOpenProject

    override fun setUp() {
        super.setUp()
        server = MockOpenProject(testRootDisposable)
        server.handler = { request ->
            if (request.url.encodedPath == "/api/v3/work_packages") hal(collection(workPackage(42, "Fix the login"))) else notFound()
        }
    }

    override fun tearDown() {
        try {
            ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.contentManager?.removeAllContents(true)
            TaskManager.getManager(project).let { manager ->
                manager.activateTask(manager.localTasks.first { it.isDefault }, false)
                manager.localTasks.filterNot { it.isDefault }.forEach(manager::removeTask)
            }
            setRepositories()
            detectChanges()
            // Lists store their view per server in the (shared) test project.
            WorkPackageListViews.getInstance(project).let { views -> STORED_VIEWS.forEach(views::remove) }
        } finally {
            super.tearDown()
        }
    }

    private fun repository(url: String = SERVER, token: String = "token"): OpenProjectRepository {
        val type = TaskRepositoryType.getRepositoryTypes().filterIsInstance<OpenProjectRepositoryType>().single()
        return (type.createRepository() as OpenProjectRepository).apply {
            this.url = url
            password = token
        }
    }

    /** As Settings | Tools | Tasks | Servers applies its (cloned) servers. */
    private fun setRepositories(vararg repositories: OpenProjectRepository) {
        (TaskManager.getManager(project) as TaskManagerImpl).setRepositories(repositories.toList())
    }

    /** Runs a check for changed servers and waits until it (and any check before it) is done. */
    private fun detectChanges(): ServerChanges? {
        val result = OpenProjectProjectScope.getInstance(project).cs.async { OpenProjectServers.getInstance(project).detectChanges() }
        PlatformTestUtil.waitWithEventsDispatching("server check", { result.isCompleted }, 10)
        return runBlocking { result.await() }
    }

    private fun manager(): OpenProjectToolWindowManager {
        val toolWindows = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindows.getToolWindow(TOOL_WINDOW_ID) ?: toolWindows.registerToolWindow(TOOL_WINDOW_ID) {
            anchor = ToolWindowAnchor.BOTTOM
            canCloseContent = true
        }
        if (toolWindow.contentManager.contentCount == 0) OpenProjectToolWindowFactory().createToolWindowContent(project, toolWindow)
        return OpenProjectToolWindowManager.getInstance(project)
    }

    private fun waitFor(what: String, condition: () -> Boolean) = PlatformTestUtil.waitWithEventsDispatching(what, condition, 10)

    fun `test a new token closes the work package tabs and loads the lists again`() {
        val configured = repository()
        setRepositories(configured)
        detectChanges()
        val manager = manager()
        manager.openWorkPackage(SERVER, 42)
        manager.openWorkPackage(SERVER, 7)
        manager.openList(SERVER, ListViewState(search = "login"), "login")
        val main = manager.listPanels().first()
        waitFor("main list") { main.loadedWorkPackages().isNotEmpty() }

        setRepositories(configured.clone().apply { password = "new-token" })
        // This check or the one the tool window started may notice it; the tabs show the outcome.
        detectChanges()

        assertEquals(listOf(LIST, LIST), manager.tabs().map { it.kind })
        assertEquals(SERVER, main.serverId)
        waitFor("lists loaded with the new token") {
            server.requests(HttpMethod.Get, "/api/v3/work_packages").count { it.authorization == basic("new-token") } >= 2
        }
        waitFor("rows") { main.loadedWorkPackages().isNotEmpty() }
    }

    fun `test a new URL moves the main list and closes the tabs of the old server`() {
        val configured = repository()
        setRepositories(configured)
        detectChanges()
        val manager = manager()
        manager.openWorkPackage(SERVER, 42)
        manager.openList(SERVER, ListViewState(search = "login"), "login")
        val main = manager.listPanels().first()
        main.showPreview(42)
        assertNotNull(main.preview())

        setRepositories(configured.clone().apply { url = "$NEW_SERVER/" })
        detectChanges()

        assertEquals(listOf(LIST), manager.tabs().map { it.kind })
        assertEquals(NEW_SERVER, main.serverId)
        assertNull(main.preview())
        waitFor("rows of the new server") { main.loadedWorkPackages().isNotEmpty() }
        assertTrue(server.requests.any { it.path == "/api/v3/work_packages" })
    }

    fun `test the active task stays linked to its server when the token changes`() {
        val configured = repository()
        setRepositories(configured)
        detectChanges()
        val workPackage = OpenProjectApi.decodeWorkPackage(Fixtures.json("work_package.json"))
        val servers = OpenProjectServers.getInstance(project)
        ActiveWorkPackageTracker.getInstance(project).activate(servers.servers().single(), workPackage)
        val active = TaskManager.getManager(project).activeTask
        assertSame(configured, active.repository)

        val changed = configured.clone().apply { password = "new-token" }
        setRepositories(changed)
        // Tasks unlinks tasks from servers it no longer has: Close Task would not offer to update the status.
        assertNull(active.repository)

        detectChanges()
        assertSame(changed, active.repository)
        assertEquals(workPackage.id, ActiveWorkPackageTracker.getInstance(project).active.value?.workPackageId)
    }

    fun `test changes are told by server`() {
        setRepositories(repository("https://a.example.com"), repository("https://b.example.com"))
        detectChanges()
        setRepositories(repository("https://a.example.com", token = "other"), repository("https://c.example.com"))
        assertEquals(
            ServerChanges(changed = setOf("https://a.example.com"), removed = setOf("https://b.example.com"), added = setOf("https://c.example.com")),
            detectChanges(),
        )
        assertNull(detectChanges())
    }

    private companion object {
        const val SERVER = "https://changes.example.com"
        const val NEW_SERVER = "https://new.changes.example.com"
        val STORED_VIEWS = listOf(SERVER, NEW_SERVER, "https://a.example.com", "https://c.example.com")
    }
}
