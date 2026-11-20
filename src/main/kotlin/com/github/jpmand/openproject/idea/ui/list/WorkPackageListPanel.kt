package com.github.jpmand.openproject.idea.ui.list

import com.github.jpmand.openproject.idea.OpenProjectBundle
import com.github.jpmand.openproject.idea.api.Filters
import com.github.jpmand.openproject.idea.api.OpenProjectApiException
import com.github.jpmand.openproject.idea.api.SortSpec
import com.github.jpmand.openproject.idea.api.WorkPackagePage
import com.github.jpmand.openproject.idea.api.WorkPackageQuery
import com.github.jpmand.openproject.idea.api.model.Query
import com.github.jpmand.openproject.idea.api.model.WorkPackage
import com.github.jpmand.openproject.idea.core.ActiveWorkPackage
import com.github.jpmand.openproject.idea.core.ActiveWorkPackageTracker
import com.github.jpmand.openproject.idea.core.OpenProjectConnection
import com.github.jpmand.openproject.idea.core.OpenProjectProjectScope
import com.github.jpmand.openproject.idea.core.OpenProjectServer
import com.github.jpmand.openproject.idea.core.OpenProjectServers
import com.github.jpmand.openproject.idea.core.ServerChanges
import com.github.jpmand.openproject.idea.core.WorkPackageChange
import com.github.jpmand.openproject.idea.core.WorkPackageChangesListener
import com.github.jpmand.openproject.idea.core.connect
import com.github.jpmand.openproject.idea.core.describeError
import com.github.jpmand.openproject.idea.ui.OpenProjectDataKeys
import com.github.jpmand.openproject.idea.ui.WorkPackageRef
import com.github.jpmand.openproject.idea.ui.common.Avatars
import com.github.jpmand.openproject.idea.ui.common.StatusColors
import com.github.jpmand.openproject.idea.ui.common.StatusPanel
import com.github.jpmand.openproject.idea.ui.detail.WorkPackageDetailPanel
import com.github.jpmand.openproject.idea.ui.toolwindow.OpenProjectToolWindowManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.WrapLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.ChangeEvent
import javax.swing.event.DocumentEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.table.TableCellRenderer

/**
 * A work package table of a server, modelled on OpenProject's own table: configurable columns (header drag,
 * Configure View), server-side sorting by header click, global search, quick filter dropdowns, the full filter
 * bar, saved OpenProject queries and paging. Clicking a row previews the work package on the right; double-click
 * or "Open in New Tab" opens it in a tab of its own. The active task's work package is highlighted and shown in a
 * banner.
 *
 * [requestedServerId] null means the first configured server; the view is stored under [viewKey] (null: the
 * server id, i.e. the server's main list).
 */
@OptIn(FlowPreview::class)
class WorkPackageListPanel(
    private val project: Project,
    private val requestedServerId: String?,
    private val viewKey: String?,
) : JPanel(BorderLayout()), Disposable {

    private data class SavedView(val id: Long, val name: String) {
        override fun toString() = name
    }

    private inner class Column(val id: String, title: String) : ColumnInfo<WorkPackage, String>(title) {
        override fun valueOf(item: WorkPackage): String = WorkPackageCells.tableText(item, id)
        override fun getRenderer(item: WorkPackage?): TableCellRenderer = cellRenderer
    }

    private val cs = OpenProjectProjectScope.getInstance(project).childScope("WorkPackageListPanel")
    private val views = WorkPackageListViews.getInstance(project)

    /**
     * The server shown; null until one is configured. The main list (no [requestedServerId]) follows the configured
     * servers: it moves to the first one when its server is removed or its URL changes.
     */
    @Volatile var serverId: String? = OpenProjectServers.getInstance(project).find(requestedServerId)?.id ?: requestedServerId
        private set
    private val storageKey: String? get() = viewKey ?: serverId
    private val viewState = MutableStateFlow(initialState())

    @Volatile private var server: OpenProjectServer? = null
    @Volatile private var connection: OpenProjectConnection? = null
    @Volatile private var metadata: ListMetadata = ListMetadata.FALLBACK
    @Volatile private var active: ActiveWorkPackage? = null
    private var columnIds: List<String> = emptyList()
    private var updatingColumns = false
    private var columnsDirty = false
    private var syncing = false
    private var savedViewsLoaded = false

    /** The server's saved queries; the views dropdown shows those of the selected project (see [showSavedViews]). */
    private var savedQueries: List<Query> = emptyList()
    private var lastPage: WorkPackagePage? = null
    @Volatile private var pageSizes: List<Int> = listOf(20, 50, 100)

    private val model = ListTableModel<WorkPackage>()
    private val table = WorkPackageTable(model).apply {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        setShowGrid(false)
        tableHeader.reorderingAllowed = true
        autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
    }
    private val cellRenderer = WorkPackageCellRenderer(
        itemAt = { table, row -> model.items.getOrNull(table.convertRowIndexToModel(row)) },
        columnAt = { table, column -> columnIds.getOrNull(table.convertColumnIndexToModel(column)) },
        isActive = { it.id == active?.workPackageId },
        cellColor = ::cellColor,
        valueIcon = ::avatar,
    )

    /** Column -> id of the linked resource -> its OpenProject color: statuses, types and priorities have colors. */
    @Volatile private var columnColors: Map<String, Map<Long, Color>> = emptyMap()

    /** Attribute -> type from the work package schemas of the loaded pages (custom field column widths). */
    private var schemaTypes: Map<String, String> = emptyMap()

    private val searchField = SearchTextField(false).apply {
        textEditor.emptyText.text = OpenProjectBundle.message("list.search.hint")
        textEditor.columns = 22
    }
    private val quickFilters = QuickFilterDef.ALL.map { def ->
        QuickFilterLink(def, cs, ::loadResourceOptions) { filter -> viewState.update { ListQueries.withQuickFilter(it, def.name, filter) } }
    }
    private val viewsCombo = ComboBox(DefaultComboBoxModel(arrayOf(defaultView()))).apply {
        toolTipText = OpenProjectBundle.message("list.views.tooltip")
    }

    private val filterBar = FilterBar(cs, { metadata }, ::loadOptions) { filters ->
        viewState.update { ListQueries.withFilters(it, filters) }
    }
    private var showFilters = false
    private val filterPanel = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.customLineBottom(JBColor.border())
        add(filterBar, BorderLayout.CENTER)
        isVisible = false
    }
    private val queryError = JBLabel().apply {
        foreground = JBColor.RED
        border = JBUI.Borders.empty(4, 8)
        isVisible = false
    }
    private val banner = ActiveBanner()
    private val status = StatusPanel()
    private val center = JPanel(BorderLayout())
    private val footerLabel = JBLabel()
    private val loadingLabel = JBLabel(AnimatedIcon.Default.INSTANCE).apply { isVisible = false }
    private var preview: WorkPackageDetailPanel? = null
    private var restoringSelection = false
    private val splitter = OnePixelSplitter(false, "OpenProject.List.Preview", 0.55f)
    /** Refresh, retry and server changes: the current query is loaded again (the newest load wins, see init). */
    private val reloadRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val previousLink = ActionLink(OpenProjectBundle.message("list.page.previous")) { viewState.update { it.copy(page = (it.page - 1).coerceAtLeast(1)) } }
    private val nextLink = ActionLink(OpenProjectBundle.message("list.page.next")) { viewState.update { it.copy(page = it.page + 1) } }

    init {
        // Avatars arrive after the rows are shown.
        Avatars.getInstance().addListener(this) { table.repaint() }
        val listArea = JPanel(BorderLayout()).apply {
            add(center, BorderLayout.CENTER)
            add(footer(), BorderLayout.SOUTH)
        }
        splitter.firstComponent = listArea
        add(UiDataProvider.wrapComponent(JPanel(BorderLayout()).apply {
            add(JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(header())
                add(filterPanel)
                add(queryError)
                add(banner)
            }, BorderLayout.NORTH)
            add(splitter, BorderLayout.CENTER)
        }) { sink ->
            val selected = table.selectedObject
            val server = server
            sink[OpenProjectDataKeys.WORK_PACKAGE] = if (selected != null && server != null) WorkPackageRef(server, selected) else null
            sink[OpenProjectDataKeys.REFRESHER] = Runnable { reload() }
            sink[OpenProjectDataKeys.LIST_PANEL] = this
        }, BorderLayout.CENTER)

        installTableBehaviour()
        installControls()
        rebuildColumns(viewState.value)
        showFilters = viewState.value.filters.any { it.name !in QUICK_FILTER_NAMES }
        filterPanel.isVisible = showFilters
        center.add(status, BorderLayout.CENTER)
        status.loading()

        cs.launch {
            // One load at a time: a newer query or reload cancels a pending one, so a slow answer never replaces a newer one.
            merge(
                viewState.map(ListQueries::toQuery).distinctUntilChanged().debounce(250),
                reloadRequests.map { ListQueries.toQuery(viewState.value) },
            ).collectLatest { query -> load(query) }
        }
        cs.launch(Dispatchers.EDT) {
            viewState.collect { state ->
                storageKey?.let { views.put(it, state) }
                syncControls(state)
                if (state.columns != columnIds) rebuildColumns(state)
                table.tableHeader.repaint()
            }
        }
        cs.launch {
            ActiveWorkPackageTracker.getInstance(project).active.collect {
                withContext(Dispatchers.EDT) { showActive() }
            }
        }
        project.messageBus.connect(this).subscribe(WorkPackageChangesListener.TOPIC, WorkPackageChangesListener(::workPackageChanged))
    }

    /**
     * A work package was changed on its page, by Log Time or by a task status update: its row shows the new state. The
     * rows stay where they are (no new query), so the list does not jump while it is being worked with.
     */
    private fun workPackageChanged(change: WorkPackageChange) {
        if (change.serverId != serverId || model.items.none { it.id == change.workPackageId }) return
        val workPackage = change.workPackage
        if (workPackage != null) {
            replaceRow(workPackage)
            return
        }
        cs.launch {
            val fresh = try {
                currentConnection().api.workPackage(change.workPackageId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                thisLogger().info("Could not load #${change.workPackageId} after a change", e)
                return@launch
            }
            withContext(Dispatchers.EDT) { if (change.serverId == serverId) replaceRow(fresh) }
        }
    }

    private fun replaceRow(workPackage: WorkPackage) {
        val index = model.items.indexOfFirst { it.id == workPackage.id }.takeIf { it >= 0 } ?: return
        model.setItem(index, workPackage)
    }

    private fun initialState(): ListViewState {
        val key = storageKey ?: return defaults()
        return views.get(key, defaults()).copy(page = 1)
    }

    /** A new view: the open work packages of the project configured for the Task Server. */
    private fun defaults(): ListViewState = ListViewState.defaults(projectId())

    private fun projectId(): Long = (server ?: OpenProjectServers.getInstance(project).find(serverId))?.repository?.projectId ?: 0

    /** Loads the list again (Refresh, Retry); notices Task Server changes made since. */
    fun reload() {
        OpenProjectServers.getInstance(project).checkForChanges()
        if (!adoptConfiguredServer()) connection?.invalidate()
        reloadRequests.tryEmit(Unit)
    }

    /**
     * The URL or API token of Task Servers changed (see [OpenProjectServers]): what was loaded with the old ones is
     * dropped, the main list moves to another server if its own is gone, and the list is loaded again.
     */
    fun serversChanged(changes: ServerChanges) {
        val moved = adoptConfiguredServer()
        if (!moved && serverId !in changes.stale) return
        if (!moved) forgetServer()
        reloadRequests.tryEmit(Unit)
    }

    // ---- loading ----

    private suspend fun load(requestedQuery: WorkPackageQuery) {
        // The server this load is for; results for another one (the list moved meanwhile) are dropped.
        val (loading, query) = withContext(Dispatchers.EDT) {
            loadingLabel.isVisible = true
            val moved = adoptConfiguredServer()
            serverId to if (moved) ListQueries.toQuery(viewState.value) else requestedQuery
        }
        fun current() = serverId == loading
        try {
            val (server, connection) = connect(project, loading)
            if (!current()) return
            this.server = server
            this.connection = connection
            val loadedMetadata = ListMetadata.load(connection)
            runCatching { connection.configuration().perPageOptions }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { pageSizes = it }
            val metadataChanged = loadedMetadata !== metadata
            metadata = loadedMetadata
            val page = connection.api.workPackages(query)
            val colors = loadColumnColors(connection)
            withContext(Dispatchers.EDT) {
                if (!current()) return@withContext
                columnColors = colors
                val types = schemaTypes + page.schemas.flatMap { it.fields.values }.associate { it.attribute to it.type }.filterKeys { it !in schemaTypes }
                val typesChanged = types != schemaTypes
                schemaTypes = types
                if (metadataChanged) {
                    filterBar.refreshMetadata()
                    rebuildColumns(viewState.value)
                } else if (typesChanged) {
                    applyMinimumWidths()
                }
                if (!savedViewsLoaded) loadSavedViews(connection)
                show(page)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OpenProjectApiException) {
            withContext(Dispatchers.EDT) {
                if (!current()) return@withContext
                if (e.errorIdentifier == OpenProjectApiException.INVALID_QUERY || e.status == 400) {
                    // The rows of the previous query would not match the filters shown.
                    queryError.text = e.message
                    queryError.isVisible = true
                    model.items = emptyList()
                    footerLabel.text = ""
                    showStatus { status.empty(OpenProjectBundle.message("list.empty")) }
                } else {
                    showError(e)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.EDT) { if (current()) showError(e) }
        } finally {
            withContext(Dispatchers.EDT + NonCancellable) { loadingLabel.isVisible = false }
        }
    }

    private fun show(page: WorkPackagePage) {
        lastPage = page
        queryError.isVisible = false
        val selectedId = table.selectedObject?.id
        model.items = page.elements
        restoringSelection = true
        try {
            selectedId?.let { id -> model.items.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { table.selectionModel.setSelectionInterval(it, it) } }
        } finally {
            restoringSelection = false
        }
        val pages = ListQueries.pageCount(page.total, page.pageSize)
        val from = if (page.total == 0) 0 else (page.page - 1) * page.pageSize + 1
        val to = ((page.page - 1) * page.pageSize + page.elements.size)
        footerLabel.text = OpenProjectBundle.message("list.page.summary", from, to, page.total)
        previousLink.isVisible = pages > 1
        nextLink.isVisible = pages > 1
        previousLink.isEnabled = page.page > 1
        nextLink.isEnabled = page.page < pages
        if (page.elements.isEmpty()) showStatus { status.empty(OpenProjectBundle.message("list.empty")) } else showTable()
    }

    private suspend fun loadResourceOptions(elementType: String): List<ValueOption> =
        loadOptions(FilterMeta(elementType, elementType, emptyMap()), "=", elementType)

    /** The connection of the shown server; a new one when the token changed since it was obtained. */
    private suspend fun currentConnection(): OpenProjectConnection =
        connection?.takeUnless { it.isClosed } ?: connect(project, serverId).second

    private suspend fun loadOptions(filter: FilterMeta, operator: String, elementType: String): List<ValueOption> {
        val connection = currentConnection()
        fun named(id: Long, name: String) = ValueOption(id.toString(), name)
        return when (elementType) {
            "Status" -> connection.statuses().map { ValueOption(it.id.toString(), it.name, StatusColors.parse(it.color)) }
            "Type" -> connection.types().map { ValueOption(it.id.toString(), it.name, StatusColors.parse(it.color)) }
            "Priority" -> connection.priorities().map { ValueOption(it.id.toString(), it.name, StatusColors.parse(it.color)) }
            "Project" -> connection.projects().map { named(it.id, it.name) }
            "Version" -> connection.cached("versions") { connection.api.versions() }.map { named(it.id, it.name) }
            "User", "Principal", "Group", "PlaceholderUser" ->
                listOf(ValueOption("me", OpenProjectBundle.message("filter.me"))) +
                    connection.cached("principals") { connection.api.principals(pageSize = 500) }.map { named(it.id, it.name) }
            else -> filter.allowedValues[operator].orEmpty().mapNotNull { link -> link.id?.let { ValueOption(it, link.title ?: it) } }
        }
    }

    private fun loadSavedViews(connection: OpenProjectConnection) {
        savedViewsLoaded = true
        cs.launch {
            val queries = runCatching { connection.api.queries() }.getOrNull() ?: return@launch
            withContext(Dispatchers.EDT) {
                savedQueries = queries
                syncing = true
                try {
                    showSavedViews(viewState.value)
                } finally {
                    syncing = false
                }
            }
        }
    }

    /** Fills the views dropdown with the saved queries of the list's project (or the global ones) and selects the applied one. */
    private fun showSavedViews(state: ListViewState) {
        val items = listOf(defaultView()) + ListQueries.savedViews(savedQueries, ListQueries.selectedProjects(state), state.savedQueryId)
            .map { SavedView(it.id!!, (if (it.starred) "★ " else "") + it.name.orEmpty()) }
        val model = viewsCombo.model
        if ((0 until model.size).map(model::getElementAt) != items) viewsCombo.model = DefaultComboBoxModel(items.toTypedArray())
        val selected = items.firstOrNull { it.id == state.savedQueryId } ?: items.first()
        if (viewsCombo.selectedItem != selected) viewsCombo.selectedItem = selected
    }

    private fun applySavedView(view: SavedView) {
        if (view.id == 0L) {
            viewState.update { ListQueries.reset(it, defaults()) }
            return
        }
        cs.launch {
            try {
                val query = currentConnection().api.query(view.id)
                val columns = query.links.list("columns").mapNotNull { it.id }
                val sortBy = query.links.list("sortBy").mapNotNull { it.id }.map { SortSpec(it.substringBeforeLast('-'), !it.endsWith("-desc")) }
                viewState.update {
                    it.copy(
                        columns = columns.ifEmpty { it.columns },
                        sortBy = sortBy.ifEmpty { ListViewState.DEFAULT_SORT },
                        // A query of a project shows that project; others get the Task Server's project, like new views.
                        filters = ListQueries.withDefaultProject(Filters.fromQueryFilters(query.filters), query.links["project"]?.id?.toLongOrNull() ?: projectId()),
                        search = "",
                        page = 1,
                        savedQueryId = view.id,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.EDT) {
                    queryError.text = describeError(e)
                    queryError.isVisible = true
                }
            }
        }
    }

    // ---- UI ----

    private fun header(): JComponent {
        val group = DefaultActionGroup(
            ActionManager.getInstance().getAction("OpenProject.Refresh"),
            object : DumbAwareAction(OpenProjectBundle.message("list.configure"), null, AllIcons.General.GearPlain) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun actionPerformed(e: AnActionEvent) = configureView()
            },
            object : DumbAwareToggleAction(OpenProjectBundle.message("list.filters"), null, AllIcons.General.Filter) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun isSelected(e: AnActionEvent) = showFilters
                override fun setSelected(e: AnActionEvent, state: Boolean) {
                    showFilters = state
                    filterPanel.isVisible = state
                }
                override fun update(e: AnActionEvent) {
                    super.update(e)
                    val count = viewState.value.filters.size
                    e.presentation.text = if (count == 0) OpenProjectBundle.message("list.filters") else OpenProjectBundle.message("list.filters.count", count)
                }
            },
            object : DumbAwareAction(OpenProjectBundle.message("list.reset"), null, AllIcons.General.Reset) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun actionPerformed(e: AnActionEvent) = viewState.update { ListQueries.reset(it, defaults()) }
            },
            object : DumbAwareAction(OpenProjectBundle.message("list.open.as.tab"), OpenProjectBundle.message("list.open.as.tab.description"), AllIcons.Actions.OpenNewTab) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun actionPerformed(e: AnActionEvent) = openAsNewTab()
            },
            Separator.getInstance(),
            ActionManager.getInstance().getAction("OpenProject.LogTime"),
        )
        val toolbar = ActionManager.getInstance().createActionToolbar("OpenProject.WorkPackageList", group, true)
        toolbar.targetComponent = table
        val controls = JPanel(WrapLayout(FlowLayout.LEFT, JBUI.scale(12), JBUI.scale(3))).apply {
            isOpaque = false
            add(searchField)
            quickFilters.forEach(::add)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
                add(JBLabel(OpenProjectBundle.message("list.views")).apply {
                    foreground = UIUtil.getContextHelpForeground()
                    border = JBUI.Borders.emptyRight(4)
                })
                add(viewsCombo)
            })
        }
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.customLineBottom(JBColor.border())
            add(toolbar.component, BorderLayout.WEST)
            add(controls, BorderLayout.CENTER)
        }
    }

    private fun footer(): JComponent = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(10), JBUI.scale(4))).apply {
        border = JBUI.Borders.customLineTop(JBColor.border())
        add(footerLabel)
        add(previousLink)
        add(nextLink)
        add(loadingLabel)
    }

    private fun installControls() {
        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                if (!syncing) viewState.update { ListQueries.withSearch(it, searchField.text) }
            }
        })
        viewsCombo.addActionListener {
            if (!syncing) (viewsCombo.selectedItem as? SavedView)?.let(::applySavedView)
        }
    }

    private fun syncControls(state: ListViewState) {
        syncing = true
        try {
            if (searchField.text != state.search && !searchField.textEditor.hasFocus()) searchField.text = state.search
            quickFilters.forEach { it.show(state.filters) }
            showSavedViews(state)
            filterBar.setFilters(state.filters)
        } finally {
            syncing = false
        }
    }

    private fun installTableBehaviour() {
        TableSpeedSearch.installOn(table)
        table.selectionModel.addListSelectionListener { e ->
            if (!e.valueIsAdjusting && !restoringSelection && preview != null) table.selectedObject?.let { showPreview(it.id) }
        }
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e) && e.clickCount == 1) table.selectedObject?.let { showPreview(it.id) }
            }
        })
        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean = openSelected()
        }.installOn(table)
        table.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && openSelected()) e.consume()
            }
        })
        PopupHandler.installPopupMenu(table, "OpenProject.WorkPackagePopup", "OpenProject.WorkPackageListPopup")

        val header = table.tableHeader
        val original = header.defaultRenderer
        header.defaultRenderer = TableCellRenderer { tbl, value, selected, focused, row, column ->
            val component = original.getTableCellRendererComponent(tbl, value, selected, focused, row, column)
            (component as? JLabel)?.let { label ->
                val id = columnIds.getOrNull(tbl.convertColumnIndexToModel(column))
                val sorts = viewState.value.sortBy
                val index = sorts.indexOfFirst { it.attribute == id }
                label.icon = when {
                    index < 0 -> null
                    sorts[index].ascending -> AllIcons.General.ArrowUp
                    else -> AllIcons.General.ArrowDown
                }
                label.horizontalTextPosition = JLabel.LEFT
                if (index > 0) label.text = "${label.text} ${index + 1}"
                label.toolTipText = if (id != null && metadata.isSortable(id)) OpenProjectBundle.message("list.sort.tooltip") else null
            }
            component
        }
        header.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (!SwingUtilities.isLeftMouseButton(e) || e.clickCount != 1 || header.resizingColumn != null) return
                val viewColumn = header.columnAtPoint(e.point).takeIf { it >= 0 } ?: return
                val id = columnIds.getOrNull(table.convertColumnIndexToModel(viewColumn)) ?: return
                if (metadata.isSortable(id)) viewState.update { ListQueries.toggleSort(it, id, additive = e.isShiftDown) }
            }

            override fun mouseReleased(e: MouseEvent) {
                if (columnsDirty) commitColumnLayout()
            }
        })
        table.columnModel.addColumnModelListener(object : TableColumnModelListener {
            override fun columnMoved(e: TableColumnModelEvent) {
                if (!updatingColumns && e.fromIndex != e.toIndex) columnsDirty = true
            }

            override fun columnMarginChanged(e: ChangeEvent) {
                if (!updatingColumns && header.resizingColumn != null) columnsDirty = true
            }

            override fun columnAdded(e: TableColumnModelEvent) = Unit
            override fun columnRemoved(e: TableColumnModelEvent) = Unit
            override fun columnSelectionChanged(e: ListSelectionEvent) = Unit
        })
        PopupHandler.installPopupMenu(header, DefaultActionGroup(
            object : DumbAwareAction(OpenProjectBundle.message("list.column.hide")) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun actionPerformed(e: AnActionEvent) {
                    val point = header.mousePosition ?: return
                    val id = columnIds.getOrNull(table.convertColumnIndexToModel(header.columnAtPoint(point))) ?: return
                    viewState.update { ListQueries.withColumns(it, it.columns - id) }
                }
            },
            object : DumbAwareAction(OpenProjectBundle.message("list.configure")) {
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
                override fun actionPerformed(e: AnActionEvent) = configureView()
            },
        ), "OpenProject.WorkPackageListHeader")
    }

    /** Persists column order and widths after the user dragged or resized header columns. */
    private fun commitColumnLayout() {
        columnsDirty = false
        val order = (0 until table.columnCount).mapNotNull { columnIds.getOrNull(table.convertColumnIndexToModel(it)) }
        val widths = (0 until table.columnCount).associate { view ->
            columnIds[table.convertColumnIndexToModel(view)] to table.columnModel.getColumn(view).width
        }
        viewState.update { it.copy(columns = order, widths = it.widths + widths) }
    }

    private fun rebuildColumns(state: ListViewState) {
        updatingColumns = true
        try {
            columnIds = state.columns
            model.columnInfos = state.columns.map { Column(it, metadata.columnTitle(it)) }.toTypedArray()
            table.setModelAndUpdateColumns(model)
            state.columns.forEachIndexed { index, id ->
                val column = table.columnModel.getColumn(index)
                column.minWidth = minimumWidth(id)
                column.preferredWidth = maxOf(state.widths[id] ?: defaultWidth(id), column.minWidth)
            }
        } finally {
            updatingColumns = false
        }
    }

    /** Values must stay readable: a column is never narrower than its typical value (percent, duration, date). */
    private fun minimumWidth(column: String): Int =
        ColumnWidths.minWidth(ColumnWidths.kind(column, schemaTypes[column]), table.getFontMetrics(table.font))

    private fun applyMinimumWidths() {
        updatingColumns = true
        try {
            for (view in 0 until table.columnCount) {
                val column = table.columnModel.getColumn(view)
                val id = columnIds.getOrNull(column.modelIndex) ?: continue
                column.minWidth = minimumWidth(id)
                if (column.preferredWidth < column.minWidth) column.preferredWidth = column.minWidth
            }
        } finally {
            updatingColumns = false
        }
    }

    private fun defaultWidth(column: String): Int = JBUI.scale(
        when (column) {
            "id" -> 70
            "subject" -> 360
            "percentageDone", "derivedPercentageDone" -> 90
            else -> 120
        },
    )

    private fun configureView() {
        val dialog = ConfigureViewDialog(project, metadata, viewState.value, pageSizes)
        if (dialog.showAndGet()) viewState.value = dialog.result().copy(savedQueryId = 0)
    }

    private fun openSelected(): Boolean {
        val selected = table.selectedObject ?: return false
        val serverId = serverId ?: return false
        OpenProjectToolWindowManager.getInstance(project).openWorkPackage(serverId, selected.id)
        return true
    }

    /** Shows [workPackageId] next to the list ("Open on the Right"); a row click does the same. */
    fun showPreview(workPackageId: Long) {
        val serverId = serverId ?: return
        val current = preview
        if (current != null && current.workPackageId == workPackageId && current.serverId == serverId) return
        current?.let(Disposer::dispose)
        val panel = WorkPackageDetailPanel(project, serverId, workPackageId, WorkPackageDetailPanel.Layout.PREVIEW, onClose = ::closePreview)
        Disposer.register(this, panel)
        preview = panel
        splitter.secondComponent = panel
        splitter.revalidate()
    }

    fun closePreview() {
        dropPreview()
        table.requestFocusInWindow()
    }

    private fun dropPreview() {
        preview?.let(Disposer::dispose)
        preview = null
        splitter.secondComponent = null
        splitter.revalidate()
    }

    /** Opens the current filters, sorting and columns in a new list tab. */
    private fun openAsNewTab() {
        val serverId = serverId ?: return
        val title = (viewsCombo.selectedItem as? SavedView)?.takeIf { it.id != 0L }?.name?.removePrefix("★ ")
            ?: viewState.value.search.trim().takeIf { it.isNotEmpty() }
        OpenProjectToolWindowManager.getInstance(project).openList(serverId, viewState.value.copy(page = 1), title)
    }

    /**
     * The main list shows the first configured server when its own is not (or no longer) configured: a server
     * configured after the list was opened, or a changed URL. Returns whether the list moved to another server.
     */
    private fun adoptConfiguredServer(): Boolean {
        if (requestedServerId != null) return false
        val servers = OpenProjectServers.getInstance(project)
        if (serverId != null && servers.find(serverId) != null) return false
        val found = servers.find(null)?.id ?: return false
        if (found == serverId) return false
        serverId = found
        forgetServer()
        // The view stored for that server, or a new one.
        viewState.value = initialState()
        return true
    }

    /** Drops everything loaded from the server (rows, preview, metadata, saved views) until the list is loaded again. */
    private fun forgetServer() {
        server = null
        connection = null
        metadata = ListMetadata.FALLBACK
        columnColors = emptyMap()
        schemaTypes = emptyMap()
        savedQueries = emptyList()
        savedViewsLoaded = false
        lastPage = null
        dropPreview()
        model.items = emptyList()
        footerLabel.text = ""
        previousLink.isVisible = false
        nextLink.isVisible = false
        queryError.isVisible = false
        filterBar.refreshMetadata()
        showActive()
        showStatus { status.loading() }
    }

    /** The banner and row highlight of the active task, when it is a work package of this list's server. */
    private fun showActive() {
        active = ActiveWorkPackageTracker.getInstance(project).active.value?.takeIf { it.serverId == serverId }
        banner.show(active)
        table.repaint()
    }

    private fun locate(workPackageId: Long) {
        val index = model.items.indexOfFirst { it.id == workPackageId }
        if (index >= 0) {
            val row = table.convertRowIndexToView(index)
            table.selectionModel.setSelectionInterval(row, row)
            table.scrollRectToVisible(table.getCellRect(row, 0, true))
            table.requestFocusInWindow()
        } else {
            viewState.update { it.copy(search = "#$workPackageId", page = 1) }
        }
    }

    private fun showTable() {
        if (center.componentCount == 1 && center.getComponent(0) !== status) return
        center.removeAll()
        center.add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER)
        center.revalidate()
        center.repaint()
    }

    /** A failed load: the error with Retry; the page summary and paging of the last results no longer apply. */
    private fun showError(error: Throwable) {
        footerLabel.text = ""
        previousLink.isVisible = false
        nextLink.isVisible = false
        showStatus { status.error(project, error) { reload() } }
    }

    private fun showStatus(update: () -> Unit) {
        update()
        if (status.parent != center) {
            center.removeAll()
            center.add(status, BorderLayout.CENTER)
            center.revalidate()
            center.repaint()
        }
    }

    private fun defaultView() = SavedView(0, OpenProjectBundle.message("list.views.default"))

    /** "Working on #12 Subject" with shortcuts, shown while the active task is a work package of this server. */
    private inner class ActiveBanner : JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(10), JBUI.scale(4))) {
        private val label = JBLabel(AllIcons.Vcs.History)

        init {
            background = JBUI.CurrentTheme.Banner.INFO_BACKGROUND
            border = JBUI.Borders.customLineBottom(JBUI.CurrentTheme.Banner.INFO_BORDER_COLOR)
            add(label)
            add(ActionLink(OpenProjectBundle.message("list.active.open")) {
                active?.let { OpenProjectToolWindowManager.getInstance(project).openWorkPackage(it.serverId, it.workPackageId) }
            })
            add(ActionLink(OpenProjectBundle.message("list.active.log.time")) { e ->
                val action = ActionManager.getInstance().getAction("OpenProject.LogTime")
                ActionManager.getInstance().tryToExecute(action, null, e.source as JComponent, ActionPlaces.EDITOR_TOOLBAR, true)
            })
            add(ActionLink(OpenProjectBundle.message("list.active.locate")) { active?.let { locate(it.workPackageId) } })
            isVisible = false
        }

        fun show(active: ActiveWorkPackage?) {
            isVisible = active != null
            if (active != null) {
                label.text = OpenProjectBundle.message("list.active.banner", active.presentableId, active.summary)
                label.foreground = UIUtil.getLabelForeground()
            }
        }
    }

    @TestOnly
    internal fun loadedWorkPackages(): List<WorkPackage> = model.items

    @TestOnly
    internal fun pageSummary(): String = footerLabel.text

    @TestOnly
    internal fun viewState(): ListViewState = viewState.value

    @TestOnly
    internal fun savedViewNames(): List<String> = (0 until viewsCombo.model.size).map { viewsCombo.model.getElementAt(it).name }

    @TestOnly
    internal fun updateViewState(transform: (ListViewState) -> ListViewState) = viewState.update(transform)

    @TestOnly
    internal fun visibleColumnTitles(): List<String> = (0 until table.columnCount).map { table.getColumnName(it) }

    @TestOnly
    internal fun preview(): WorkPackageDetailPanel? = preview

    /** The color of [workPackage]'s value in [columnId] (status, type, priority), when the server defines one. */
    internal fun cellColor(workPackage: WorkPackage, columnId: String): Color? =
        columnColors[columnId]?.let { StatusColors.of(workPackage, columnId, it) }

    /** The avatar of the user (or group) in a single-principal column: assignee, author, accountable, user custom fields. */
    private fun avatar(workPackage: WorkPackage, columnId: String): Icon? {
        val link = workPackage.links.list(columnId).singleOrNull() ?: return null
        val href = link.href?.takeIf(Avatars::isPrincipal) ?: return null
        val server = serverId ?: return null
        return Avatars.getInstance().icon(server, href, link.title) { connection?.takeUnless { it.isClosed } ?: connect(project, server).second }
    }

    /** The colors of statuses, types and priorities (cached by the connection); a failing one is left out. */
    private suspend fun loadColumnColors(connection: OpenProjectConnection): Map<String, Map<Long, Color>> = coroutineScope {
        val statuses = async { runCatching { StatusColors.of(connection.statuses()) }.getOrNull() }
        val types = async { runCatching { StatusColors.ofTypes(connection.types()) }.getOrNull() }
        val priorities = async { runCatching { StatusColors.ofPriorities(connection.priorities()) }.getOrNull() }
        mapOf("status" to statuses.await(), "type" to types.await(), "priority" to priorities.await())
            .mapNotNull { (column, colors) -> colors?.let { column to it } }
            .toMap()
    }

    @TestOnly
    internal fun columnMinimumWidths(): Map<String, Int> =
        (0 until table.columnCount).associate { columnIds[table.columnModel.getColumn(it).modelIndex] to table.columnModel.getColumn(it).minWidth }

    @TestOnly
    internal fun quickFilterTexts(): List<String> = quickFilters.map { it.text() }

    @TestOnly
    internal fun select(workPackageId: Long) {
        val index = model.items.indexOfFirst { it.id == workPackageId }
        table.selectionModel.setSelectionInterval(index, index)
    }

    override fun dispose() {
        cs.cancel()
    }

    private companion object {
        val QUICK_FILTER_NAMES = QuickFilterDef.ALL.map { it.name }.toSet()
    }
}
