package com.github.jpmand.openproject.idea.ui.detail

import com.github.jpmand.openproject.idea.OpenProjectBundle
import com.github.jpmand.openproject.idea.api.hal.HalLink
import com.github.jpmand.openproject.idea.api.model.WorkPackage
import com.github.jpmand.openproject.idea.core.OpenProjectProjectScope
import com.github.jpmand.openproject.idea.core.cancelJob
import com.github.jpmand.openproject.idea.core.describeError
import com.github.jpmand.openproject.idea.core.runCatchingUnlessCancelled
import com.github.jpmand.openproject.idea.ui.common.DIALOG_UI
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CheckBoxList
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

/** Chooses any number of [options] (a multi-value attribute such as a "[]User" custom field); [selected] are checked. */
class ChooseValuesDialog(project: Project, title: String, private val options: List<HalLink>, selected: Set<String>) : DialogWrapper(project) {

    private val list = CheckBoxList<HalLink>().apply {
        setItems(options) { it.title ?: it.href.orEmpty() }
        options.forEach { option -> setItemSelected(option, option.href in selected) }
    }

    init {
        this.title = title
        init()
    }

    override fun createCenterPanel(): JComponent =
        ScrollPaneFactory.createScrollPane(list).apply { preferredSize = JBUI.size(360, 260) }

    override fun getPreferredFocusedComponent(): JComponent = list

    /** The hrefs of the checked values, in the order of the options. */
    fun chosen(): List<String> = options.filter { list.isItemSelected(it) }.mapNotNull { it.href }
}

/**
 * Chooses a work package (e.g. the parent), searched by id or subject; [allowNone] offers "–" to unset it.
 * The result is a link to the chosen work package, with a null href for "–".
 */
class ChooseWorkPackageDialog(
    project: Project,
    title: String,
    private val allowNone: Boolean,
    private val search: suspend (String) -> List<WorkPackage>,
) : DialogWrapper(project) {

    private val cs = OpenProjectProjectScope.getInstance(project).childScope("ChooseWorkPackageDialog")
    private val searchField = JBTextField().apply { emptyText.text = OpenProjectBundle.message("detail.relations.search.hint") }
    private val candidates = CollectionListModel<HalLink>()
    private val candidateList = JBList(candidates).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = textListCellRenderer { it?.title.orEmpty() }
        emptyText.text = OpenProjectBundle.message("detail.relations.search.empty")
    }
    private var searchJob: Job? = null

    init {
        this.title = title
        Disposer.register(disposable) { cs.cancel() }
        searchField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = search()
        })
        init()
        search()
    }

    override fun createCenterPanel(): JComponent = panel {
        row { cell(searchField).align(Align.FILL) }
        row { cell(ScrollPaneFactory.createScrollPane(candidateList).apply { preferredSize = JBUI.size(460, 220) }).align(Align.FILL) }
    }

    override fun getPreferredFocusedComponent(): JComponent = searchField

    override fun doValidate(): ValidationInfo? =
        if (candidateList.selectedValue == null) ValidationInfo(OpenProjectBundle.message("detail.choose.work.package"), searchField) else null

    fun chosen(): HalLink = candidateList.selectedValue

    private fun search() {
        searchJob?.cancelJob()
        val query = searchField.text.trim()
        candidateList.setPaintBusy(true)
        searchJob = cs.launch {
            delay(250)
            val result = runCatchingUnlessCancelled { search(query) }
            val found = result.getOrDefault(emptyList()).map { workPackage ->
                HalLink(href = workPackage.links.href("self") ?: "/api/v3/work_packages/${workPackage.id}", title = "#${workPackage.id} ${workPackage.subject}")
            }
            val options = (if (allowNone) listOf(HalLink(href = null, title = OpenProjectBundle.message("detail.unassigned"))) else emptyList()) + found
            withContext(DIALOG_UI) {
                candidateList.setPaintBusy(false)
                candidateList.emptyText.text = searchMessage(result)
                val selected = candidateList.selectedValue
                candidates.replaceAll(options)
                options.indexOfFirst { it == selected }.takeIf { it >= 0 }?.let(candidateList::setSelectedIndex)
            }
        }
    }

    @TestOnly
    internal fun candidateTitles(): List<String> = candidates.items.map { it.title.orEmpty() }

    @TestOnly
    internal fun emptyText(): String = candidateList.emptyText.text
}

/** The text of an empty result list: no matches, or why the search failed. */
internal fun searchMessage(result: Result<*>): String =
    result.exceptionOrNull()?.let(::describeError) ?: OpenProjectBundle.message("detail.relations.search.empty")
