package com.github.jpmand.openproject.idea.ui.detail

import com.github.jpmand.openproject.idea.api.Filters
import com.github.jpmand.openproject.idea.api.OpenProjectApiException
import com.github.jpmand.openproject.idea.api.WorkPackageQuery
import com.github.jpmand.openproject.idea.api.WorkPackagePatch
import com.github.jpmand.openproject.idea.api.hal.HalLink
import com.github.jpmand.openproject.idea.api.model.Activity
import com.github.jpmand.openproject.idea.api.model.Attachment
import com.github.jpmand.openproject.idea.api.model.Principal
import com.github.jpmand.openproject.idea.api.model.Relation
import com.github.jpmand.openproject.idea.api.model.AttributeGroup
import com.github.jpmand.openproject.idea.api.model.Schema
import com.github.jpmand.openproject.idea.api.model.SchemaField
import com.github.jpmand.openproject.idea.api.model.WorkPackage
import com.github.jpmand.openproject.idea.core.OpenProjectConnection
import com.github.jpmand.openproject.idea.core.OpenProjectServer
import com.github.jpmand.openproject.idea.core.connect
import com.github.jpmand.openproject.idea.ui.WorkPackageRef
import com.github.jpmand.openproject.idea.ui.common.StatusColors
import com.intellij.openapi.project.Project
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.awt.Color
import java.io.OutputStream
import kotlin.time.Duration.Companion.minutes

/** Everything the work package page shows. */
data class WorkPackageDetails(
    val server: OpenProjectServer,
    val workPackage: WorkPackage,
    val schema: Schema?,
    val activities: List<Activity>,
    val relations: List<Relation>,
    val attachments: List<Attachment>,
    /** Display names of the users in [activities] (activity links carry no titles). */
    val userNames: Map<String, String> = emptyMap(),
    /** Status id -> OpenProject color, for the status badge and chooser. */
    val statusColors: Map<Long, Color> = emptyMap(),
    /** Type id -> OpenProject color, for the type badge and chooser. */
    val typeColors: Map<Long, Color> = emptyMap(),
    /**
     * The schema of the work package's edit form (null without edit permission): unlike [schema], which is shared by
     * all work packages of the project and type, it tells which attributes of this one can be changed.
     */
    val formSchema: Schema? = null,
) {
    val statusColor: Color? get() = StatusColors.of(workPackage, statusColors)
    val typeColor: Color? get() = StatusColors.of(workPackage, "type", typeColors)

    /** The schema field of [attribute], preferring the edit form's. */
    fun field(attribute: String): SchemaField? = formSchema?.get(attribute) ?: schema?.get(attribute)

    /** The form groups of the attributes ("Details", "People", ...); empty when the server sends none. */
    val attributeGroups: List<AttributeGroup> get() = schema?.attributeGroups.orEmpty().ifEmpty { formSchema?.attributeGroups.orEmpty() }

    /** The user may edit the work package and the schema marks [attribute] as writable. */
    fun isWritable(attribute: String): Boolean = canEdit && field(attribute)?.writable == true

    val ref: WorkPackageRef get() = WorkPackageRef(server, workPackage)
    val canEdit: Boolean get() = workPackage.links.has("update") || workPackage.links.has("updateImmediately")
    val canComment: Boolean get() = workPackage.links.has("addComment")
    val canAttach: Boolean get() = workPackage.links.has("addAttachment")
    val canRelate: Boolean get() = workPackage.links.has("addRelation")
}

/** The work package was changed on the server since it was loaded; the page now shows the server version. */
class ConcurrentChangeException(cause: OpenProjectApiException) : Exception(cause.message, cause)

/**
 * Loads a work package with its activity, relations and files, and performs the edits of the work package page.
 * Every write follows the action links the server offers and sends the current `lockVersion`; on a conflict the
 * page is reloaded and [ConcurrentChangeException] is thrown so the change can be reapplied.
 */
class WorkPackageDetailController(private val project: Project, private val serverId: String, private val workPackageId: Long) {

    private val mutex = Mutex()
    private val _details = MutableStateFlow<WorkPackageDetails?>(null)
    val details: StateFlow<WorkPackageDetails?> = _details.asStateFlow()

    @Volatile
    private var connection: OpenProjectConnection? = null

    /** The connection of the work package's server; a new one when the token changed since it was obtained. */
    suspend fun connection(): OpenProjectConnection =
        connection?.takeUnless { it.isClosed } ?: connect(project, serverId).second.also { connection = it }

    suspend fun load(): WorkPackageDetails = mutex.withLock { loadUnlocked() }

    private suspend fun loadUnlocked(): WorkPackageDetails = coroutineScope {
        val (server, connection) = connect(project, serverId)
        this@WorkPackageDetailController.connection = connection
        val api = connection.api
        val workPackage = api.workPackage(workPackageId)
        val schema = async {
            workPackage.links.href("schema")?.let { href -> runCatching { connection.cached("schema:$href", 30.minutes) { api.workPackageSchema(href) } }.getOrNull() }
        }
        val activities = async { runCatching { api.activities(workPackage) }.getOrDefault(emptyList()) }
        val relations = async { runCatching { api.relations(workPackage) }.getOrDefault(emptyList()) }
        val attachments = async { runCatching { api.attachments(workPackage) }.getOrDefault(emptyList()) }
        val statusColors = async { runCatching { StatusColors.of(connection.statuses()) }.getOrDefault(emptyMap()) }
        val typeColors = async { runCatching { StatusColors.ofTypes(connection.types()) }.getOrDefault(emptyMap()) }
        val formSchema = async {
            if (!workPackage.links.has("update") && !workPackage.links.has("updateImmediately")) return@async null
            runCatching { api.workPackageForm(workPackage, WorkPackagePatch(workPackage.lockVersion)).schema }.getOrNull()
        }
        val loadedActivities = activities.await()
        val userNames = loadedActivities.mapNotNull { it.links.href("user") }.distinct()
            .map { href -> async { href to runCatching { connection.cached("principal:$href", 60.minutes) { api.getJson(href) } }.getOrNull() } }
            .mapNotNull { pending ->
                val (href, json) = pending.await()
                ((json?.get("name") as? JsonPrimitive)?.contentOrNull)?.let { href to it }
            }
            .toMap()
        WorkPackageDetails(
            server, workPackage, schema.await(), loadedActivities, relations.await(), attachments.await(), userNames,
            statusColors.await(), typeColors.await(), formSchema.await(),
        ).also { _details.value = it }
    }

    private fun current(): WorkPackageDetails = _details.value ?: error("Work package not loaded")

    /** Applies a change built by [change] to the latest work package (subject, description, status, ...). */
    suspend fun update(change: (WorkPackagePatch) -> Unit): WorkPackage = mutex.withLock {
        val api = connection().api
        val workPackage = current().workPackage
        val patch = WorkPackagePatch(workPackage.lockVersion).apply(change)
        try {
            val updated = api.updateWorkPackage(workPackage, patch)
            loadUnlocked()
            updated
        } catch (e: OpenProjectApiException) {
            if (e.isConflict) {
                loadUnlocked()
                throw ConcurrentChangeException(e)
            }
            throw e
        }
    }

    /** Statuses the workflow allows next, from the work package form. */
    suspend fun allowedStatuses(): List<HalLink> = allowedValues("status")

    suspend fun allowedPriorities(): List<HalLink> = allowedValues("priority")

    /** Types the work package can be changed to. */
    suspend fun allowedTypes(): List<HalLink> = allowedValues("type")

    /**
     * The values [attribute] can be set to, from a fresh work package form: listed in the schema or, for
     * users, versions and the like, fetched from the collection the schema links.
     */
    suspend fun allowedOptions(attribute: String): List<HalLink> {
        val workPackage = current().workPackage
        val field = connection().api.workPackageForm(workPackage, WorkPackagePatch(workPackage.lockVersion)).schema[attribute] ?: return emptyList()
        return field.allowedOptions
            .ifEmpty { field.allowedValuesHref?.let { connection().api.allowedValues(it) }.orEmpty() }
            .ifEmpty {
                if (attribute != "assignee" && attribute != "responsible") return@ifEmpty emptyList()
                availableAssignees().map { HalLink(href = it.links.href("self") ?: "/api/v3/users/${it.id}", title = it.name) }
            }
    }

    /** Work packages matching [query] (id or subject), e.g. to choose a parent; never this one. */
    suspend fun searchWorkPackages(query: String, pageSize: Int = 20): List<WorkPackage> {
        val filters = listOf(Filters.anyStatus()) + listOfNotNull(query.trim().takeIf { it.isNotEmpty() }?.let(Filters::subjectOrId))
        return connection().api.workPackages(WorkPackageQuery(filters = filters, pageSize = pageSize)).elements.filter { it.id != workPackageId }
    }

    /** Users the work package can be assigned to. */
    suspend fun availableAssignees(): List<Principal> = connection().api.availableAssignees(current().workPackage)

    private suspend fun allowedValues(attribute: String): List<HalLink> {
        val workPackage = current().workPackage
        val form = connection().api.workPackageForm(workPackage, WorkPackagePatch(workPackage.lockVersion))
        return form.schema[attribute]?.allowedOptions.orEmpty()
    }

    suspend fun addComment(text: String, internal: Boolean): Activity = mutex.withLock {
        val activity = connection().api.addComment(current().workPackage, text, internal)
        loadUnlocked()
        activity
    }

    suspend fun upload(fileName: String, contentType: String?, bytes: ByteArray, description: String? = null): Attachment = mutex.withLock {
        val attachment = connection().api.uploadAttachment(current().workPackage, fileName, contentType, bytes, description)
        loadUnlocked()
        attachment
    }

    suspend fun deleteAttachment(attachment: Attachment) = mutex.withLock {
        connection().api.deleteAttachment(attachment)
        loadUnlocked()
    }

    /** An attachment by id: one of this work package's, or else loaded from the server (e.g. linked in a comment). */
    suspend fun attachment(id: Long): Attachment =
        current().attachments.firstOrNull { it.id == id } ?: connection().api.attachment(id)

    suspend fun download(attachment: Attachment, sink: OutputStream) {
        val href = attachment.links.href("downloadLocation") ?: attachment.links.href("staticDownloadLocation") ?: error("No download link")
        connection().api.download(href, sink)
    }

    suspend fun relationCandidates(query: String, type: String): List<WorkPackage> =
        connection().api.relationCandidates(current().workPackage, query, type)

    suspend fun addRelation(type: String, to: WorkPackage, description: String?, lag: Int?): Relation = mutex.withLock {
        val toHref = to.links.href("self") ?: "/api/v3/work_packages/${to.id}"
        val relation = connection().api.createRelation(current().workPackage, type, toHref, description, lag)
        loadUnlocked()
        relation
    }

    suspend fun deleteRelation(relation: Relation) = mutex.withLock {
        connection().api.deleteRelation(relation)
        loadUnlocked()
    }

    suspend fun renderMarkdown(raw: String): String = connection().api.renderMarkdown(raw, current().workPackage)
}
