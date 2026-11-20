package com.github.jpmand.openproject.idea.ui

import com.github.jpmand.openproject.idea.api.FilterSpec
import com.github.jpmand.openproject.idea.api.MockOpenProject
import com.github.jpmand.openproject.idea.api.MockOpenProject.Companion.collection
import com.github.jpmand.openproject.idea.api.MockOpenProject.Companion.error
import com.github.jpmand.openproject.idea.api.MockOpenProject.Companion.hal
import com.github.jpmand.openproject.idea.api.MockOpenProject.Companion.notFound
import com.github.jpmand.openproject.idea.api.MockOpenProject.Companion.workPackage
import com.github.jpmand.openproject.idea.tasks.OpenProjectRepository
import com.github.jpmand.openproject.idea.tasks.OpenProjectRepositoryType
import com.github.jpmand.openproject.idea.ui.list.ListQueries
import com.github.jpmand.openproject.idea.ui.list.WorkPackageListPanel
import com.github.jpmand.openproject.idea.ui.list.WorkPackageListViews
import com.intellij.openapi.util.Disposer
import com.intellij.tasks.TaskManager
import com.intellij.tasks.TaskRepositoryType
import com.intellij.tasks.impl.TaskManagerImpl
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import java.net.ConnectException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JLabel

/** A slow server must not leave the work package list showing outdated rows. */
class SlowServerTest : BasePlatformTestCase() {

    private lateinit var server: MockOpenProject

    override fun setUp() {
        super.setUp()
        server = MockOpenProject(testRootDisposable)
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

    fun `test a slow answer does not replace the newer one`() {
        val calls = AtomicInteger()
        server.handler = { request ->
            when {
                request.url.encodedPath != "/api/v3/work_packages" -> notFound()
                calls.incrementAndGet() == 1 -> {
                    delay(1500)
                    hal(collection(workPackage(1, "slow and old")))
                }
                else -> hal(collection(workPackage(2, "new")))
            }
        }
        val list = WorkPackageListPanel(project, null, null)
        try {
            waitFor("first request") { calls.get() == 1 }
            list.reload()
            waitFor("newer rows") { list.loadedWorkPackages().map { it.id } == listOf(2L) }
            val slowAnswerDue = System.currentTimeMillis() + 2500
            waitFor("slow answer due") { System.currentTimeMillis() > slowAnswerDue }
            assertEquals(listOf(2L), list.loadedWorkPackages().map { it.id })
        } finally {
            Disposer.dispose(list)
        }
    }

    fun `test an unreachable server shows the error without the summary of the last page`() {
        val reachable = AtomicBoolean(true)
        server.handler = { request ->
            when {
                !reachable.get() -> throw ConnectException()
                request.url.encodedPath == "/api/v3/work_packages" -> hal(collection(workPackage(1, "first"), workPackage(2, "second")))
                else -> notFound()
            }
        }
        val list = WorkPackageListPanel(project, null, null)
        try {
            waitFor("rows") { list.loadedWorkPackages().size == 2 }
            assertEquals("1–2 of 2", list.pageSummary())
            reachable.set(false)
            list.reload()
            // Connection failures are retried (with backoff) for every request of the load.
            PlatformTestUtil.waitWithEventsDispatching("error shown", { list.pageSummary().isEmpty() }, 90)
            val texts = UIUtil.findComponentsOfType(list, JLabel::class.java).filter { it.isShowing || it.isVisible }.map { it.text.orEmpty() }
            assertTrue(texts.toString(), texts.any { it.contains("Cannot reach the OpenProject server") && "()" !in it })
        } finally {
            Disposer.dispose(list)
        }
    }

    fun `test quick filters show titles once the server answers`() {
        val authorized = AtomicBoolean(false)
        server.handler = { request ->
            when (request.url.encodedPath) {
                "/api/v3/work_packages" -> hal(collection(workPackage(1, "first")))
                // As without an API token: the projects cannot be read yet.
                "/api/v3/projects" -> if (authorized.get()) {
                    hal("""{"_type":"Collection","total":1,"count":1,"pageSize":100,"offset":1,""" +
                        """"_embedded":{"elements":[{"_type":"Project","id":1,"identifier":"demo-project","name":"Demo project"}]}}""")
                } else {
                    error(HttpStatusCode.Unauthorized, "Unauthenticated", "You need to be authenticated to access this resource.")
                }
                else -> notFound()
            }
        }
        val list = WorkPackageListPanel(project, null, null)
        try {
            waitFor("rows") { list.loadedWorkPackages().size == 1 }
            list.updateViewState { ListQueries.withQuickFilter(it, "project", FilterSpec("project", "=", listOf("1"))) }
            waitFor("project id shown") { "#1" in list.quickFilterTexts() }
            authorized.set(true)
            list.reload()
            waitFor("project title shown") { "Demo project" in list.quickFilterTexts() }
        } finally {
            Disposer.dispose(list)
        }
    }

    private companion object {
        const val SERVER = "https://slow.example.com"
    }
}
