package com.github.jpmand.openproject.idea.tasks

import com.github.jpmand.openproject.idea.api.MockOpenProject
import com.github.jpmand.openproject.idea.api.MockOpenProject.Companion.error
import com.github.jpmand.openproject.idea.api.MockOpenProject.Companion.hal
import com.github.jpmand.openproject.idea.api.MockOpenProject.Companion.notFound
import com.github.jpmand.openproject.idea.api.MockOpenProject.Companion.workPackage
import com.intellij.tasks.CustomTaskState
import com.intellij.tasks.TaskRepositoryType
import com.intellij.tasks.impl.LocalTaskImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

/**
 * "Update issue state" of Open Task and Close Task: the statuses offered and setting one, also when the user may not
 * change the status or the server does not answer.
 */
class TaskStatesTest : BasePlatformTestCase() {

    private lateinit var server: MockOpenProject
    private lateinit var repository: OpenProjectRepository
    private val task = LocalTaskImpl("42", "Fix the login")
    private val closed = CustomTaskState("/api/v3/statuses/12", "Closed")

    override fun setUp() {
        super.setUp()
        server = MockOpenProject(testRootDisposable)
        val type = TaskRepositoryType.getRepositoryTypes().filterIsInstance<OpenProjectRepositoryType>().single()
        repository = (type.createRepository() as OpenProjectRepository).apply {
            url = "https://op.example.com"
            password = "token"
        }
    }

    private fun refusal(action: () -> Unit): OpenProjectRepository.TaskStateException {
        try {
            action()
        } catch (e: OpenProjectRepository.TaskStateException) {
            return e
        }
        throw AssertionError("expected the status change to be refused")
    }

    private fun form(writable: Boolean) = """{"_type":"Form","_embedded":{"payload":{"lockVersion":3},"schema":{"_type":"Schema",
        "status":{"type":"Status","name":"Status","required":true,"hasDefault":true,"writable":$writable,"location":"_links",
        "_links":{"allowedValues":[{"href":"/api/v3/statuses/1","title":"New"},{"href":"/api/v3/statuses/7","title":"In progress"},
        {"href":"/api/v3/statuses/12","title":"Closed"}]}}},"validationErrors":{}},"_links":{}}"""

    fun `test the statuses the workflow allows are offered`() {
        server.handler = { request ->
            when (request.method to request.url.encodedPath) {
                HttpMethod.Get to "/api/v3/work_packages/42" -> hal(workPackage(42, "Fix the login"))
                HttpMethod.Post to "/api/v3/work_packages/42/form" -> hal(form(writable = true))
                else -> notFound()
            }
        }
        val states = repository.getAvailableTaskStates(task)
        assertEquals(listOf("New", "In progress", "Closed"), states.map { it.presentableName })
        assertEquals("/api/v3/statuses/12", states.last().id)
    }

    fun `test no statuses are offered without permission to edit the work package`() {
        server.handler = { request ->
            when (request.method to request.url.encodedPath) {
                HttpMethod.Get to "/api/v3/work_packages/42" -> hal(workPackage(42, "Fix the login", editable = false))
                HttpMethod.Post to "/api/v3/work_packages/42/form" ->
                    error(HttpStatusCode.Forbidden, "MissingPermission", "You are not authorized to access this resource.")
                else -> notFound()
            }
        }
        // Tasks reports exceptions of this call as IDE errors: there must be none.
        assertEmpty(repository.getAvailableTaskStates(task))
    }

    fun `test no statuses are offered when the status is read-only for the user`() {
        server.handler = { request ->
            when (request.method to request.url.encodedPath) {
                HttpMethod.Get to "/api/v3/work_packages/42" -> hal(workPackage(42, "Fix the login"))
                HttpMethod.Post to "/api/v3/work_packages/42/form" -> hal(form(writable = false))
                else -> notFound()
            }
        }
        assertEmpty(repository.getAvailableTaskStates(task))
    }

    fun `test a server that does not answer in time offers no statuses and is not asked again`() {
        server.handler = { request -> throw HttpRequestTimeoutException(request.url.toString(), 60_000) }
        assertEmpty(repository.getAvailableTaskStates(task))
        assertEquals(1, server.requests.size)
    }

    fun `test setting the status`() {
        server.handler = { request ->
            when (request.method to request.url.encodedPath) {
                HttpMethod.Get to "/api/v3/work_packages/42" -> hal(workPackage(42, "Fix the login", statusId = 7))
                HttpMethod.Patch to "/api/v3/work_packages/42" -> hal(workPackage(42, "Fix the login", statusId = 12))
                else -> notFound()
            }
        }
        repository.setTaskState(task, closed)
        val patch = server.requests(HttpMethod.Patch, "/api/v3/work_packages/42").single()
        assertTrue(patch.body, patch.body.contains("\"lockVersion\":3") && patch.body.contains("/api/v3/statuses/12"))
        assertEquals("Closed", repository.takeStateChange("42"))
    }

    fun `test a work package that has the status already is left alone`() {
        server.handler = { request ->
            when (request.method to request.url.encodedPath) {
                HttpMethod.Get to "/api/v3/work_packages/42" -> hal(workPackage(42, "Fix the login", statusId = 12, editable = false))
                else -> error(HttpStatusCode.Forbidden, "MissingPermission", "You are not authorized to access this resource.")
            }
        }
        repository.setTaskState(task, closed)
        assertEmpty(server.requests(HttpMethod.Patch, "/api/v3/work_packages/42"))
        assertEquals("Closed", repository.takeStateChange("42"))
    }

    fun `test a status change the user may not make is explained`() {
        server.handler = { request ->
            when (request.method to request.url.encodedPath) {
                HttpMethod.Get to "/api/v3/work_packages/42" -> hal(workPackage(42, "Fix the login", statusId = 7))
                HttpMethod.Patch to "/api/v3/work_packages/42" ->
                    error(HttpStatusCode.Forbidden, "MissingPermission", "You are not authorized to access this resource.")
                else -> notFound()
            }
        }
        val error = refusal { repository.setTaskState(task, closed) }
        assertEquals("OpenProject did not accept the status 'Closed' for 42: You are not authorized to access this resource.", error.message)
        assertNull(repository.takeStateChange("42"))
    }

    fun `test a status the workflow does not allow is explained`() {
        val message = "Status is invalid because no valid transition exists from old to new status for the current user's roles."
        server.handler = { request ->
            when (request.method to request.url.encodedPath) {
                HttpMethod.Get to "/api/v3/work_packages/42" -> hal(workPackage(42, "Fix the login", statusId = 1))
                HttpMethod.Patch to "/api/v3/work_packages/42" -> error(HttpStatusCode.UnprocessableEntity, "PropertyConstraintViolation", message)
                else -> notFound()
            }
        }
        val error = refusal { repository.setTaskState(task, closed) }
        assertTrue(error.message!!, error.message!!.endsWith(message))
        assertNull(repository.takeStateChange("42"))
    }
}
