package com.github.jpmand.openproject.idea.it

import com.github.jpmand.openproject.idea.api.WorkPackagePatch
import com.github.jpmand.openproject.idea.api.model.WorkPackage
import com.github.jpmand.openproject.idea.core.OpenProjectServers
import com.github.jpmand.openproject.idea.tasks.OpenProjectRepository
import com.github.jpmand.openproject.idea.tasks.OpenProjectRepositoryType
import com.github.jpmand.openproject.idea.ui.common.HtmlView
import com.github.jpmand.openproject.idea.ui.detail.ConcurrentChangeException
import com.github.jpmand.openproject.idea.ui.detail.EditorKind
import com.github.jpmand.openproject.idea.ui.detail.PropertyEditing
import com.github.jpmand.openproject.idea.ui.detail.RelationsPanel
import com.github.jpmand.openproject.idea.ui.detail.WorkPackageDetailPanel
import com.github.jpmand.openproject.idea.ui.detail.WorkPackageHierarchy
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.tasks.TaskManager
import com.intellij.tasks.TaskRepositoryType
import com.intellij.tasks.impl.TaskManagerImpl
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import javax.swing.event.HyperlinkEvent

/** The work package page against a real OpenProject: every edit it offers. */
class DetailPanelIntegrationTest : BasePlatformTestCase() {

    private lateinit var serverId: String
    private lateinit var workPackage: WorkPackage
    private lateinit var editor: WorkPackageDetailPanel

    override fun setUp() {
        super.setUp()
        OpenProjectIntegration.assumeConfigured()
        val type = TaskRepositoryType.getRepositoryTypes().filterIsInstance<OpenProjectRepositoryType>().single()
        val repository = (type.createRepository() as OpenProjectRepository).apply {
            url = OpenProjectIntegration.url
            password = OpenProjectIntegration.token
        }
        (TaskManager.getManager(project) as TaskManagerImpl).setRepositories(listOf(repository))
        serverId = OpenProjectServers.getInstance(project).servers().single().id
        OpenProjectIntegration.newApi().use { api ->
            OpenProjectIntegration.cleanUp(api, PREFIX)
            workPackage = runBlocking { OpenProjectIntegration.createWorkPackage(api, PREFIX + "page") }
        }
        editor = WorkPackageDetailPanel(project, serverId, workPackage.id, WorkPackageDetailPanel.Layout.TAB)
        PlatformTestUtil.waitWithEventsDispatching("details", { editor.details() != null }, 60)
        // seeded statuses have colors, shown as the status badge
        assertNotNull(editor.details()!!.statusColor)
    }

    override fun tearDown() {
        try {
            if (::editor.isInitialized) Disposer.dispose(editor)
            if (OpenProjectIntegration.url != null) {
                OpenProjectIntegration.newApi().use { OpenProjectIntegration.cleanUp(it, PREFIX) }
                (TaskManager.getManager(project) as TaskManagerImpl).setRepositories(emptyList<OpenProjectRepository>())
            }
        } finally {
            super.tearDown()
        }
    }

    private val controller get() = editor.controller

    fun `test edit subject description status assignee and priority`() = runBlocking {
        controller.update { it.subject(PREFIX + "renamed") }
        assertEquals(PREFIX + "renamed", controller.details.value!!.workPackage.subject)

        controller.update { it.description("Updated **from the IDE**") }
        assertEquals("Updated **from the IDE**", controller.details.value!!.workPackage.description?.raw)
        assertTrue(controller.renderMarkdown("**x**").contains("<strong>x</strong>"))

        val status = controller.allowedStatuses().first { it.href != controller.details.value!!.workPackage.links.href("status") }
        controller.update { it.link("status", status.href) }
        assertEquals(status.href, controller.details.value!!.workPackage.links.href("status"))

        val me = controller.availableAssignees().first()
        controller.update { it.link("assignee", me.links.href("self")) }
        assertEquals(me.name, controller.details.value!!.workPackage.assigneeTitle)
        controller.update { it.link("assignee", null) }
        assertNull(controller.details.value!!.workPackage.links.href("assignee"))

        val priority = controller.allowedPriorities().first { it.href != controller.details.value!!.workPackage.links.href("priority") }
        controller.update { it.link("priority", priority.href) }
        assertEquals(priority.href, controller.details.value!!.workPackage.links.href("priority"))
    }

    fun `test conflicting change reloads and can be reapplied`() = runBlocking {
        OpenProjectIntegration.newApi().use { api ->
            val fresh = api.workPackage(workPackage.id)
            api.updateWorkPackage(fresh, WorkPackagePatch(fresh.lockVersion).subject(PREFIX + "changed elsewhere"))
        }
        try {
            controller.update { it.subject(PREFIX + "mine") }
            fail("expected a conflict")
        } catch (_: ConcurrentChangeException) {
            assertEquals(PREFIX + "changed elsewhere", controller.details.value!!.workPackage.subject)
        }
        controller.update { it.subject(PREFIX + "mine") }
        assertEquals(PREFIX + "mine", controller.details.value!!.workPackage.subject)
    }

    fun `test comment files and relations`() = runBlocking {
        controller.addComment("Looks good **to me**", internal = false)
        assertTrue(controller.details.value!!.activities.any { it.isComment && it.comment?.raw == "Looks good **to me**" })

        val attachment = controller.upload("notes.txt", "text/plain", "attachment body".toByteArray())
        assertTrue(controller.details.value!!.attachments.any { it.id == attachment.id })
        val out = ByteArrayOutputStream()
        controller.download(controller.details.value!!.attachments.first { it.id == attachment.id }, out)
        assertEquals("attachment body", out.toString(Charsets.UTF_8))
        controller.deleteAttachment(controller.details.value!!.attachments.first { it.id == attachment.id })
        assertFalse(controller.details.value!!.attachments.any { it.id == attachment.id })

        val other = OpenProjectIntegration.newApi().use { OpenProjectIntegration.createWorkPackage(it, PREFIX + "other") }
        val candidate = controller.relationCandidates(PREFIX + "other", "blocks").first { it.id == other.id }
        val relation = controller.addRelation("blocks", candidate, "needed first", null)
        val related = RelationsPanel.related(controller.details.value!!.workPackage, controller.details.value!!.relations)
        assertTrue(related.any { it.relation?.id == relation.id && it.label == RelationsPanel.label("blocks") && it.target.id == other.id.toString() })
        controller.deleteRelation(controller.details.value!!.relations.first { it.id == relation.id })
        assertTrue(controller.details.value!!.relations.none { it.id == relation.id })
    }

    fun `test hierarchy groups type and typed property edits`() = runBlocking {
        val details = controller.details.value!!
        // the form schema tells what this work package allows; the server groups the attributes
        assertNotNull(details.formSchema)
        assertTrue(details.isWritable("startDate"))
        assertFalse(details.isWritable("spentTime"))
        val groups = PropertyEditing.groups(details)
        assertTrue(groups.size > 1)
        assertTrue(groups.flatMap { it.attributes }.containsAll(listOf("assignee", "startDate", "dueDate", "estimatedTime")))
        assertNotNull(details.typeColor)

        controller.update { it.property("startDate", PropertyEditing.date(LocalDate.of(2026, 3, 2))) }
        assertEquals("2026-03-02", controller.details.value!!.workPackage.startDate)
        controller.update { it.property("startDate", PropertyEditing.date(null)) }
        assertNull(controller.details.value!!.workPackage.startDate)
        controller.update { it.property("estimatedTime", PropertyEditing.parse(EditorKind.DURATION, "1h 30m", required = false)!!) }
        assertEquals("PT1H30M", controller.details.value!!.workPackage.estimatedTime)

        // linked values come from the form, listed or fetched from the collection it links
        val assignee = controller.allowedOptions("assignee").first()
        controller.update { it.link("assignee", assignee.href) }
        assertEquals(assignee.href, controller.details.value!!.workPackage.links.href("assignee"))
        assertTrue(controller.allowedOptions("priority").isNotEmpty())

        val type = controller.allowedTypes().first { it.href != controller.details.value!!.workPackage.links.href("type") && it.title != "Milestone" }
        controller.update { it.link("type", type.href) }
        assertEquals(type.title, controller.details.value!!.workPackage.typeTitle)

        // a child shows its parent in the hierarchy
        val parent = OpenProjectIntegration.newApi().use { OpenProjectIntegration.createWorkPackage(it, PREFIX + "parent") }
        assertTrue(controller.searchWorkPackages(parent.id.toString()).any { it.id == parent.id })
        controller.update { it.link("parent", "/api/v3/work_packages/${parent.id}") }
        val hierarchy = WorkPackageHierarchy.of(controller.details.value!!.workPackage)
        assertEquals(listOf(parent.id, workPackage.id), hierarchy.map { it.workPackageId })
        assertEquals("#${parent.id} ${PREFIX}parent", hierarchy.first().text)
    }

    fun `test attachment links in the activity open the file in an editor`() {
        val attachment = runBlocking { controller.upload("linked.txt", "text/plain", "linked body".toByteArray()) }
        assertEquals(attachment.id, runBlocking { controller.attachment(attachment.id) }.id)
        PlatformTestUtil.waitWithEventsDispatching("activity", { htmlViews().any { "linked.txt" in it.text } }, 30)
        val view = htmlViews().first { "linked.txt" in it.text }
        val href = Regex("""href="([^"]*attachments/${attachment.id}[^"]*)"""").find(view.text)!!.groupValues[1]
        view.fireHyperlinkUpdate(HyperlinkEvent(view, HyperlinkEvent.EventType.ACTIVATED, null, href))
        val editors = FileEditorManager.getInstance(project)
        PlatformTestUtil.waitWithEventsDispatching("editor", { editors.openFiles.any { it.name == "linked.txt" } }, 30)
        assertEquals("linked body", String(editors.openFiles.first { it.name == "linked.txt" }.contentsToByteArray()))
        editors.openFiles.forEach(editors::closeFile)
    }

    private fun htmlViews(): List<HtmlView> = UIUtil.uiTraverser(editor).filter(HtmlView::class.java).toList()

    private companion object {
        const val PREFIX = "IT-detail "
    }
}
