package com.github.jpmand.openproject.idea.tasks

import com.github.jpmand.openproject.idea.api.OpenProjectHttpClient
import com.github.jpmand.openproject.idea.core.OpenProjectClientPool
import com.github.jpmand.openproject.idea.core.describeError
import com.intellij.openapi.application.ApplicationManager
import com.intellij.tasks.TaskRepositoryType
import com.intellij.tasks.impl.LocalTaskImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.UIUtil
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.http.HttpConnectTimeoutException
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

class OpenProjectRepositoryTest : BasePlatformTestCase() {

    private fun type(): OpenProjectRepositoryType =
        TaskRepositoryType.getRepositoryTypes().filterIsInstance<OpenProjectRepositoryType>().single()

    fun `test repository type is registered`() {
        assertEquals("OpenProject", type().name)
        assertEquals(OpenProjectRepository::class.java, type().repositoryClass)
    }

    fun `test user agent carries the plugin version and IDE build`() {
        val userAgent = OpenProjectClientPool.userAgent()
        assertTrue(userAgent, Regex("""OpenProject-IntelliJ-Plugin/\d+\.\d+\.\d+\S* \(\w+-\d+\..+\)""").matches(userAgent))
    }

    fun `test settings survive xml serialization without the token`() {
        val repository = type().createRepository() as OpenProjectRepository
        repository.url = "https://op.example.com"
        repository.password = "secret-token"
        repository.projectId = 12
        repository.projectName = "Demo"
        repository.assignedToMeOnly = true
        repository.savedQueryId = 5
        repository.fullTextSearch = true

        val element = XmlSerializer.serialize(repository)
        val xml = JDOMUtil.write(element)
        assertEquals("OpenProject", element.name)
        assertFalse(xml, xml.contains("secret-token"))

        val restored = XmlSerializer.deserialize(element, OpenProjectRepository::class.java)
        assertEquals("https://op.example.com", restored.url)
        assertEquals(12L, restored.projectId)
        assertEquals("Demo", restored.projectName)
        assertTrue(restored.assignedToMeOnly)
        assertEquals(5L, restored.savedQueryId)
        assertTrue(restored.fullTextSearch)
        assertEquals(OpenProjectRepository.API_KEY_USER, restored.username)
    }

    fun `test clone and equals include the OpenProject settings`() {
        val repository = type().createRepository() as OpenProjectRepository
        repository.url = "https://op.example.com"
        repository.projectId = 3
        val copy = repository.clone()
        assertEquals(repository, copy)
        copy.assignedToMeOnly = true
        assertFalse(repository == copy)
    }

    fun `test editor builds and applies the OpenProject settings`() {
        val repository = type().createRepository() as OpenProjectRepository
        repository.projectId = 4
        repository.projectName = "Demo"
        repository.assignedToMeOnly = true
        val editor = type().createEditor(repository, project) {} as OpenProjectRepositoryEditor
        try {
            assertNotNull(editor.createComponent())
            editor.apply()
            assertEquals(4L, repository.projectId)
            assertEquals("Demo", repository.projectName)
            assertTrue(repository.assignedToMeOnly)
            assertEquals(OpenProjectRepository.API_KEY_USER, repository.username)
        } finally {
            com.intellij.openapi.util.Disposer.dispose(editor)
        }
    }

    fun `test the general settings scroll when the editor is shorter than they are`() {
        val editor = type().createEditor(type().createRepository() as OpenProjectRepository, project) {} as OpenProjectRepositoryEditor
        try {
            val component = editor.createComponent()
            // The height Settings | Tools | Tasks | Servers leaves below the list of servers.
            component.size = Dimension(600, 265)
            layOut(component)
            val tabs = UIUtil.findComponentOfType(component, JBTabbedPane::class.java)!!
            // Both tabs are kept: General (now scrolling) and Commit Message, with their own contents.
            assertEquals(listOf("General", "Commit Message"), (0 until tabs.tabCount).map(tabs::getTitleAt))
            assertFalse(tabs.getComponentAt(1) is JScrollPane)
            assertTrue("the Commit Message tab keeps its settings", UIUtil.findComponentsOfType(tabs.getComponentAt(1) as JComponent, JComponent::class.java).isNotEmpty())
            val general = tabs.getComponentAt(0)
            assertInstanceOf(general, JScrollPane::class.java)
            assertTrue("the General tab shows the server settings", UIUtil.findComponentsOfType((general as JScrollPane).viewport.view as JComponent, JButton::class.java).any { it.text == "Test" })
            assertTrue("the server settings are visible in the scroll pane", general.viewport.view.isVisible)
            val viewport = (general as JScrollPane).viewport
            val content = viewport.view as JComponent
            assertTrue("the settings are taller than the tab", content.height > viewport.height)

            // Test, the last row of the tab, can be scrolled into view.
            val test = UIUtil.findComponentsOfType(content, JButton::class.java).single { it.text == "Test" }
            val bounds = SwingUtilities.convertRectangle(test.parent, test.bounds, content)
            content.scrollRectToVisible(bounds)
            assertTrue(viewport.viewRect.contains(bounds))
        } finally {
            com.intellij.openapi.util.Disposer.dispose(editor)
        }
    }

    private fun layOut(component: Component) {
        if (component !is Container) return
        component.doLayout()
        component.components.forEach(::layOut)
    }

    fun `test presentable name shows host and project`() {
        val repository = type().createRepository() as OpenProjectRepository
        repository.url = "https://op.example.com/"
        assertEquals("op.example.com", repository.presentableName)
        repository.projectName = "Demo"
        assertEquals("op.example.com / Demo", repository.presentableName)
    }

    fun `test blocking calls from the EDT run in the background`() {
        assertTrue(ApplicationManager.getApplication().isDispatchThread)
        val ranOnEdt = OpenProjectRepository.blocking { ApplicationManager.getApplication().isDispatchThread }
        assertFalse(ranOnEdt)
        try {
            OpenProjectRepository.blocking<Unit> { error("server down") }
            fail("expected the failure to be rethrown")
        } catch (e: IllegalStateException) {
            assertEquals("server down", e.message)
        }
    }

    fun `test a server that does not answer in time is told apart from one that cannot be reached`() {
        val timeout = HttpRequestTimeoutException("https://op.example.com/api/v3/work_packages", 60_000)
        assertTrue(OpenProjectHttpClient.isTimeout(timeout))
        assertTrue(OpenProjectHttpClient.isTimeout(SocketTimeoutException("Read timed out")))
        assertTrue(OpenProjectHttpClient.isTimeout(HttpConnectTimeoutException("HTTP connect timed out")))
        assertFalse(OpenProjectHttpClient.isTimeout(ConnectException("Connection refused")))
        assertEquals("OpenProject did not answer in time. Check the connection and try again.", describeError(timeout))
        assertEquals("Cannot reach the OpenProject server (op.example.com).", describeError(UnknownHostException("op.example.com")))
    }

    fun `test closing a task is confirmed with its status`() {
        val task = LocalTaskImpl("42", "Fix the login")
        assertEquals("Closed task 42 Fix the login", CloseTaskListener.message(task, null))
        assertEquals("Closed task 42 Fix the login; its status is now Closed", CloseTaskListener.message(task, "Closed"))

        val repository = type().createRepository() as OpenProjectRepository
        repository.url = "https://op.example.com"
        assertNull(repository.takeStateChange("42"))
    }
}
