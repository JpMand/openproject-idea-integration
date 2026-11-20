package com.github.jpmand.openproject.idea.tasks

import com.github.jpmand.openproject.idea.OpenProjectBundle
import com.github.jpmand.openproject.idea.core.OpenProjectProjectScope
import com.github.jpmand.openproject.idea.core.OpenProjectServers
import com.github.jpmand.openproject.idea.core.cancelJob
import com.github.jpmand.openproject.idea.ui.common.DIALOG_UI
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.tasks.config.BaseRepositoryEditor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.Consumer
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * Settings of an OpenProject Task Server: URL, API token (the password field) and how tasks are searched.
 * Projects and saved queries are loaded from the server once the URL and token work.
 */
class OpenProjectRepositoryEditor(
    project: Project,
    repository: OpenProjectRepository,
    changeListener: Consumer<in OpenProjectRepository>,
) : BaseRepositoryEditor<OpenProjectRepository>(project, repository, changeListener) {

    private data class Item(val id: Long, val name: String) {
        override fun toString(): String = name
    }

    // Created in createCustomPanel(), which the base constructor calls before this class is initialized.
    private lateinit var projectCombo: ComboBox<Item>
    private lateinit var queryCombo: ComboBox<Item>
    private lateinit var assignedToMeCheckBox: JBCheckBox
    private lateinit var fullTextCheckBox: JBCheckBox

    private val cs = OpenProjectProjectScope.getInstance(project).childScope("OpenProjectRepositoryEditor")
    private var loadJob: Job? = null

    init {
        myUsernameLabel.isVisible = false
        myUserNameText.isVisible = false
        myUseHttpAuthenticationCheckBox.isVisible = false
        myPasswordLabel.text = OpenProjectBundle.message("repository.editor.token")
        myPasswordText.toolTipText = OpenProjectBundle.message("repository.editor.token.hint")
        myURLText.toolTipText = OpenProjectBundle.message("repository.editor.url.hint")
        installListener(projectCombo)
        installListener(queryCombo)
        installListener(assignedToMeCheckBox)
        installListener(fullTextCheckBox)
        scrollGeneralTab()
        // Remembers the servers as they were before any change made here.
        OpenProjectServers.getInstance(project).checkForChanges()
        reloadServerData()
    }

    override fun createCustomPanel(): JComponent {
        val repository = myRepository
        projectCombo = ComboBox(DefaultComboBoxModel(arrayOf(allProjects(), currentProject(repository)).distinct().toTypedArray())).apply {
            selectedItem = currentProject(repository)
        }
        queryCombo = ComboBox(DefaultComboBoxModel(arrayOf(noQuery(), currentQuery(repository)).distinct().toTypedArray())).apply {
            selectedItem = currentQuery(repository)
        }
        assignedToMeCheckBox = JBCheckBox(OpenProjectBundle.message("repository.editor.assigned.to.me"), repository.assignedToMeOnly)
        fullTextCheckBox = JBCheckBox(OpenProjectBundle.message("repository.editor.full.text"), repository.fullTextSearch)
        return panel {
            row(OpenProjectBundle.message("repository.editor.project")) { cell(projectCombo) }
            row(OpenProjectBundle.message("repository.editor.query")) { cell(queryCombo) }
            row { cell(assignedToMeCheckBox) }
            row { cell(fullTextCheckBox).comment(OpenProjectBundle.message("repository.editor.full.text.comment")) }
        }
    }

    /**
     * Settings | Tools | Tasks | Servers gives the editor only the height left below the list of servers, which cut off
     * the end of the General tab (the project settings, Use proxy and Test). The tab scrolls instead. Its form sets the
     * preferred size to (-1, -1), which would make it as small as the visible area: it is computed from the layout again.
     */
    private fun scrollGeneralTab() {
        val index = (0 until myTabbedPane.tabCount).firstOrNull { SwingUtilities.isDescendingFrom(myCustomPanel, myTabbedPane.getComponentAt(it)) } ?: return
        val general = myTabbedPane.getComponentAt(index) as? JComponent ?: return
        general.preferredSize = null
        myTabbedPane.setComponentAt(index, ScrollPaneFactory.createScrollPane(general, true).apply {
            verticalScrollBar.unitIncrement = JBUI.scale(16)
        })
    }

    override fun apply() {
        val project = projectCombo.selectedItem as? Item
        val query = queryCombo.selectedItem as? Item
        myRepository.projectId = project?.id ?: 0
        myRepository.projectName = project?.takeIf { it.id > 0 }?.name
        myRepository.savedQueryId = query?.id ?: 0
        myRepository.assignedToMeOnly = assignedToMeCheckBox.isSelected
        myRepository.fullTextSearch = fullTextCheckBox.isSelected
        myRepository.username = OpenProjectRepository.API_KEY_USER
        super.apply()
    }

    override fun afterTestConnection(connectionSuccessful: Boolean) {
        super.afterTestConnection(connectionSuccessful)
        if (connectionSuccessful) reloadServerData()
    }

    /** Called when the settings are closed (also after OK): lists and tabs follow a changed URL or token. */
    override fun dispose() {
        cs.cancel()
        OpenProjectServers.getInstance(myProject).checkForChanges()
        super.dispose()
    }

    /**
     * Loads projects and saved queries in the background; failures just leave the combos as they are. A newer load
     * (after another successful connection test) replaces a pending one, and closing the settings cancels it.
     */
    private fun reloadServerData() {
        val repository = myRepository.clone()
        loadJob?.cancelJob()
        loadJob = cs.launch {
            val loaded = try {
                withContext(Dispatchers.IO) {
                    if (!repository.isConfigured) return@withContext null
                    val api = repository.connection().api
                    api.projects().map { Item(it.id, it.name) } to api.queries().filter { it.id != null }.map { Item(it.id!!, it.name.orEmpty()) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                thisLogger().info("Could not load OpenProject projects and queries", e)
                null
            } ?: return@launch
            // The settings dialog is modal.
            withContext(DIALOG_UI) {
                fill(projectCombo, listOf(allProjects()) + loaded.first, myRepository.projectId)
                fill(queryCombo, listOf(noQuery()) + loaded.second, myRepository.savedQueryId)
            }
        }
    }

    private fun fill(combo: ComboBox<Item>, items: List<Item>, selectedId: Long) {
        val selected = (combo.selectedItem as? Item)?.id ?: selectedId
        combo.model = DefaultComboBoxModel(items.toTypedArray())
        combo.selectedItem = items.firstOrNull { it.id == selected } ?: items.first()
    }

    private fun allProjects() = Item(0, OpenProjectBundle.message("repository.editor.project.all"))

    private fun noQuery() = Item(0, OpenProjectBundle.message("repository.editor.query.none"))

    private fun currentProject(repository: OpenProjectRepository) =
        if (repository.projectId > 0) Item(repository.projectId, repository.projectName ?: "#${repository.projectId}") else allProjects()

    private fun currentQuery(repository: OpenProjectRepository) =
        if (repository.savedQueryId > 0) Item(repository.savedQueryId, "#${repository.savedQueryId}") else noQuery()
}
