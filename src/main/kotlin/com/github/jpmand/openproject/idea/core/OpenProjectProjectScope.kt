package com.github.jpmand.openproject.idea.core

import com.github.jpmand.openproject.idea.OpenProjectBundle
import com.github.jpmand.openproject.idea.api.OpenProjectApiException
import com.github.jpmand.openproject.idea.api.OpenProjectHttpClient
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** The project-level coroutine scope of the plugin; editors and dialogs work in child scopes of it. */
@Service(Service.Level.PROJECT)
class OpenProjectProjectScope(val cs: CoroutineScope) {

    fun childScope(name: String): CoroutineScope =
        CoroutineScope(cs.coroutineContext + SupervisorJob(cs.coroutineContext.job) + CoroutineName(name))

    companion object {
        fun getInstance(project: Project): OpenProjectProjectScope = project.service()
    }
}

/**
 * Cancels the job, like `cancel()`. Compiled against 2026.3, `cancel()` calls `Job.cancel$default`, which the
 * coroutines library of 2026.1 and 2026.2 does not have (NoSuchMethodError there); `cancel(null)` works on all of them.
 */
fun Job.cancelJob() = cancel(null)

class NoServerConfiguredException : Exception(OpenProjectBundle.message("error.no.server"))

/** Resolves a configured server and its connection; reading the token happens off the EDT. */
suspend fun connect(project: Project, serverId: String?): Pair<OpenProjectServer, OpenProjectConnection> = withContext(Dispatchers.IO) {
    val server = OpenProjectServers.getInstance(project).find(serverId) ?: throw NoServerConfiguredException()
    server to server.repository.connection()
}

/** True when the user has to fix the Task Server configuration (no server, or the API token is rejected). */
fun needsServerSettings(error: Throwable): Boolean =
    error is NoServerConfiguredException || (error is OpenProjectApiException && error.isUnauthorized)

/** A message for the user explaining why an OpenProject call failed. */
fun describeError(error: Throwable): String = when {
    error is OpenProjectApiException -> error.message ?: OpenProjectBundle.message("error.unknown")
    error is NoServerConfiguredException -> error.message!!
    error is UnknownHostException || error is ConnectException -> OpenProjectBundle.message("error.unreachable", error.message.orEmpty())
    OpenProjectHttpClient.isTimeout(error) -> OpenProjectBundle.message("error.timeout")
    error is SSLException -> OpenProjectBundle.message("error.ssl", error.message.orEmpty())
    error is IOException -> OpenProjectBundle.message("error.io", error.message ?: error.javaClass.simpleName)
    else -> error.message ?: error.javaClass.simpleName
}
