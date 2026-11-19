package com.github.jpmand.openproject.idea.tasks

import com.github.jpmand.openproject.idea.OpenProjectBundle
import com.github.jpmand.openproject.idea.api.Durations
import com.github.jpmand.openproject.idea.api.Filters
import com.github.jpmand.openproject.idea.api.OpenProjectApiException
import com.github.jpmand.openproject.idea.api.SortSpec
import com.github.jpmand.openproject.idea.api.WorkPackagePatch
import com.github.jpmand.openproject.idea.api.WorkPackageQuery
import com.github.jpmand.openproject.idea.api.model.WorkPackage
import com.github.jpmand.openproject.idea.core.OpenProjectClientPool
import com.github.jpmand.openproject.idea.core.OpenProjectConnection
import com.github.jpmand.openproject.idea.core.TimeEntries
import com.github.jpmand.openproject.idea.core.cancelJob
import com.github.jpmand.openproject.idea.core.describeError
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.tasks.CustomTaskState
import com.intellij.tasks.LocalTask
import com.intellij.tasks.Task
import com.intellij.tasks.TaskRepository
import com.intellij.tasks.TaskRepositoryType
import com.intellij.tasks.impl.BaseRepository
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.xmlb.annotations.Tag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * An OpenProject Task Server. The URL and the API token (stored as the password, in the password safe) are the
 * configuration every other feature of the plugin uses as well. Work packages are tasks; their numeric id is the
 * task id.
 */
@Tag("OpenProject")
class OpenProjectRepository : BaseRepository {

    /** Restricts the tasks to one project (0 = all projects). */
    var projectId: Long = 0

    var projectName: String? = null

    /** Only work packages assigned to the token's user. */
    var assignedToMeOnly: Boolean = false

    /** Saved OpenProject query whose filters are applied to the task search (0 = none). */
    var savedQueryId: Long = 0

    /** Search subject, description and comments instead of id and subject. */
    var fullTextSearch: Boolean = false

    /** Activity used when the Time Tracking plugin posts time (null = the server's default). */
    var defaultTimeEntryActivityHref: String? = null

    @Suppress("unused") // used by the XML serializer
    constructor() : super()

    constructor(type: TaskRepositoryType<*>) : super(type) {
        username = API_KEY_USER
    }

    private constructor(other: OpenProjectRepository) : super(other) {
        projectId = other.projectId
        projectName = other.projectName
        assignedToMeOnly = other.assignedToMeOnly
        savedQueryId = other.savedQueryId
        fullTextSearch = other.fullTextSearch
        defaultTimeEntryActivityHref = other.defaultTimeEntryActivityHref
    }

    override fun clone(): OpenProjectRepository = OpenProjectRepository(this)

    override fun getPresentableName(): String {
        val host = url.substringAfter("://").trimEnd('/')
        return if (projectName.isNullOrBlank()) host else "$host / $projectName"
    }

    override fun isConfigured(): Boolean = url.isNotBlank() && (isLoginAnonymously || !password.isNullOrBlank())

    override fun getFeatures(): Int = NATIVE_SEARCH or STATE_UPDATING or TIME_MANAGEMENT

    /** The shared client for this server and token. Reads the token from the password safe. */
    @RequiresBackgroundThread
    fun connection(): OpenProjectConnection =
        OpenProjectClientPool.getInstance().connection(url, if (isLoginAnonymously) null else password)

    override fun getIssues(query: String?, offset: Int, limit: Int, withClosed: Boolean): Array<Task> = blocking {
        val connection = connection()
        val (page, pageSize, skip) = TaskSearch.page(offset, limit)
        val meId = if (assignedToMeOnly) connection.me().id else null
        val baseFilters = if (savedQueryId > 0) Filters.fromQueryFilters(connection.api.query(savedQueryId).filters) else emptyList()
        val options = TaskSearch.Options(projectId.takeIf { it > 0 }, assignedToMeOnly, fullTextSearch, baseFilters)
        val result = connection.api.workPackages(
            WorkPackageQuery(
                filters = TaskSearch.filters(query, withClosed, meId, options),
                sortBy = listOf(SortSpec("updatedAt", ascending = false)),
                page = page,
                pageSize = pageSize,
            ),
        )
        result.elements.drop(skip).map { toTask(connection, it) }.toTypedArray()
    }

    override fun findTask(id: String): Task? = blocking {
        val normalized = TaskSearch.normalizeId(id) ?: return@blocking null
        val connection = connection()
        try {
            toTask(connection, connection.api.workPackage("/api/v3/work_packages/$normalized"))
        } catch (e: OpenProjectApiException) {
            if (e.isNotFound || e.isMissingPermission) null else throw e
        }
    }

    override fun extractId(taskName: String): String? = TaskSearch.normalizeId(taskName)

    override fun createCancellableConnection(): CancellableConnection = object : CancellableConnection() {
        @Volatile
        private var job: Job? = null

        override fun doTest() {
            blocking {
                coroutineScope {
                    job = coroutineContext[Job]
                    val connection = connection()
                    connection.invalidate()
                    val me = connection.api.me()
                    if (!isLoginAnonymously && me.type == "AnonymousUser") {
                        throw Exception("The API token was not accepted; OpenProject treated the request as anonymous.")
                    }
                }
            }
        }

        override fun cancel() {
            job?.cancelJob()
        }
    }

    /**
     * The statuses the workflow allows for the work package (its current one included), offered by "Update issue state"
     * of Open Task and Close Task. Without permission to change the status (no edit permission, or the status is not
     * writable for the user) there are none, so the status is left alone. Tasks loads them in the background and
     * reports any exception as an IDE error: a failure (e.g. the server cannot be reached) is only logged.
     */
    override fun getAvailableTaskStates(task: Task): Set<CustomTaskState> = try {
        blocking { availableStates(task) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LOG.warn("Could not load the statuses of #${task.id}: ${describeError(e)}", e.takeUnless { it is IOException || it is OpenProjectApiException })
        emptySet()
    }

    private suspend fun availableStates(task: Task): Set<CustomTaskState> {
        val api = connection().api
        val workPackage = api.workPackage(task.id.toLong())
        val form = try {
            api.workPackageForm(workPackage, WorkPackagePatch(workPackage.lockVersion))
        } catch (e: OpenProjectApiException) {
            if (!e.isMissingPermission && !e.isNotFound) throw e
            LOG.info("No permission to change #${task.id}: ${e.message}")
            return emptySet()
        }
        val status = form.schema["status"]?.takeIf { it.writable } ?: return emptySet()
        return status.allowedOptions
            .mapNotNull { option -> option.href?.let { CustomTaskState(it, option.title ?: it.substringAfterLast('/')) } }
            .toSet()
    }

    /**
     * Called by Open Task and Close Task on the EDT, under a modal progress (see [blocking]); they show a failure in an
     * error dialog and go on opening or closing the task. A work package that has the status already is left as it is.
     */
    override fun setTaskState(task: Task, state: CustomTaskState) {
        try {
            blocking {
                val api = connection().api
                suspend fun update() {
                    val fresh = api.workPackage(task.id.toLong())
                    if (statusId(fresh.links.href("status")) == statusId(state.id)) return
                    api.updateWorkPackage(fresh, WorkPackagePatch(fresh.lockVersion).link("status", state.id))
                }
                try {
                    update()
                } catch (e: OpenProjectApiException) {
                    // Someone else changed the work package between our read and write: re-read once.
                    if (e.isConflict) update() else throw e
                }
            }
        } catch (e: CancellationException) {
            if (!ApplicationManager.getApplication().isDispatchThread) throw e
            // The user cancelled the progress (e.g. a slow server); tell why the status stays as it was.
            throw TaskStateException(OpenProjectBundle.message("task.state.cancelled", task.presentableId, state.presentableName), e)
        } catch (e: OpenProjectApiException) {
            LOG.info("OpenProject did not set the status of #${task.id} to ${state.presentableName}: ${e.message}")
            // Not allowed: no permission, or the workflow of the user's roles has no such transition.
            if (e.isMissingPermission || e.isConstraintViolation) {
                throw TaskStateException(OpenProjectBundle.message("task.state.denied", task.presentableId, state.presentableName, e.message.orEmpty()), e)
            }
            throw e
        } catch (e: Exception) {
            LOG.warn("Could not set the status of #${task.id} to ${state.presentableName}: ${describeError(e)}", e.takeUnless { it is IOException })
            throw e
        }
        LOG.info("Status of #${task.id} set to ${state.presentableName}")
        stateChanges[stateKey(task.id)] = state.presentableName
    }

    /** A status update that was refused or cancelled; Tasks shows [message] to the user. */
    class TaskStateException(message: String, cause: Throwable) : Exception(message, cause)

    /** The status [setTaskState] last set for [taskId] (e.g. by Close Task), forgotten once taken. */
    fun takeStateChange(taskId: String): String? = stateChanges.remove(stateKey(taskId))

    private fun stateKey(taskId: String) = "${url.orEmpty().trimEnd('/')}#$taskId"

    override fun updateTimeSpent(task: LocalTask, timeSpent: String, comment: String) {
        val duration = parseTimeSpent(timeSpent) ?: throw IllegalArgumentException("Unsupported time format: $timeSpent")
        if (duration.isZero) return
        blocking {
            val api = connection().api
            val workPackage = api.workPackage(task.id.toLong())
            TimeEntries.log(api, workPackage, TimeEntries.Draft(duration, LocalDate.now(), comment, defaultTimeEntryActivityHref))
        }
    }

    private suspend fun toTask(connection: OpenProjectConnection, workPackage: WorkPackage): OpenProjectTask {
        val closed = connection.statusByHref(workPackage.links.href("status"))?.isClosed ?: false
        return OpenProjectTask(workPackage, this, connection.baseUrl, closed)
    }

    override fun equals(other: Any?): Boolean {
        if (!super.equals(other) || other !is OpenProjectRepository) return false
        return projectId == other.projectId &&
            projectName == other.projectName &&
            assignedToMeOnly == other.assignedToMeOnly &&
            savedQueryId == other.savedQueryId &&
            fullTextSearch == other.fullTextSearch &&
            defaultTimeEntryActivityHref == other.defaultTimeEntryActivityHref
    }

    override fun hashCode(): Int = url?.hashCode() ?: 0

    companion object {
        /** OpenProject expects HTTP Basic with this user name and the API token as password. */
        const val API_KEY_USER = "apikey"

        /** Parses the Tasks time tracking format "Xh Ym" (and anything [Durations.parseHuman] accepts). */
        fun parseTimeSpent(timeSpent: String): Duration? {
            TaskRepository.TIME_SPENT_PATTERN.matcher(timeSpent.trim()).takeIf { it.matches() }?.let { m ->
                return Duration.ofHours(m.group(1).toLong()).plusMinutes(m.group(2).toLong())
            }
            return Durations.parseHuman(timeSpent)
        }

        private val LOG = logger<OpenProjectRepository>()

        private val stateChanges = ConcurrentHashMap<String, String>()

        /** "7" for a status link (relative or absolute). */
        private fun statusId(href: String?): String? = href?.substringAfterLast("/statuses/", "")?.takeIf { it.isNotEmpty() }

        /**
         * Runs [action] and waits for it. The Tasks plugin calls some repository methods on the EDT (Close Task sets
         * the status there), where blocking is not allowed: then the work runs in the background under a modal
         * progress, and its exceptions are rethrown.
         */
        @VisibleForTesting
        internal fun <T> blocking(action: suspend kotlinx.coroutines.CoroutineScope.() -> T): T {
            if (!ApplicationManager.getApplication().isDispatchThread) return runBlockingMaybeCancellable(action)
            return ProgressManager.getInstance().runProcessWithProgressSynchronously(
                ThrowableComputable<T, Exception> { runBlockingMaybeCancellable(action) },
                OpenProjectBundle.message("task.progress"),
                true,
                null,
            )
        }
    }
}
