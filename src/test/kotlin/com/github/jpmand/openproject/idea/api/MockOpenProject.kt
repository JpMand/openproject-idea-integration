package com.github.jpmand.openproject.idea.api

import com.github.jpmand.openproject.idea.core.OpenProjectClientPool
import com.intellij.openapi.Disposable
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A scripted OpenProject server for platform tests: every connection the plugin opens until [parent] is disposed
 * talks to [handler] instead of the network.
 */
class MockOpenProject(parent: Disposable) {

    data class Request(val method: HttpMethod, val path: String, val body: String, val authorization: String?)

    val requests = CopyOnWriteArrayList<Request>()

    @Volatile
    var handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { notFound() }

    init {
        val engine = MockEngine { request ->
            val body = when (val content = request.body) {
                is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
                is OutgoingContent.NoContent -> ""
                else -> content.toByteArray().decodeToString()
            }
            requests += Request(request.method, request.url.encodedPath, body, request.headers[HttpHeaders.Authorization])
            handler(request)
        }
        OpenProjectClientPool.getInstance().useEngine(engine, parent)
    }

    fun requests(method: HttpMethod, path: String): List<Request> = requests.filter { it.method == method && it.path == path }

    companion object {
        /** The Authorization header for [token]. */
        fun basic(token: String): String = "Basic " + Base64.getEncoder().encodeToString("apikey:$token".toByteArray())

        fun MockRequestHandleScope.hal(json: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
            respond(json, status, headersOf(HttpHeaders.ContentType, "application/hal+json"))

        fun MockRequestHandleScope.notFound(): HttpResponseData =
            hal("""{"_type":"Error","errorIdentifier":"urn:openproject-org:api:v3:errors:NotFound","message":"Not found."}""", HttpStatusCode.NotFound)

        fun MockRequestHandleScope.error(status: HttpStatusCode, identifier: String, message: String): HttpResponseData =
            hal("""{"_type":"Error","errorIdentifier":"urn:openproject-org:api:v3:errors:$identifier","message":"$message"}""", status)

        /** A work package with the given status; [editable] adds the links OpenProject sends to users who may edit it. */
        fun workPackage(id: Long, subject: String, statusId: Int = 1, editable: Boolean = true): String {
            val edit = if (editable) {
                ""","update":{"href":"/api/v3/work_packages/$id/form","method":"post"},"updateImmediately":{"href":"/api/v3/work_packages/$id","method":"patch"}"""
            } else {
                ""
            }
            return """{"_type":"WorkPackage","id":$id,"lockVersion":3,"subject":"$subject",""" +
                """"_links":{"self":{"href":"/api/v3/work_packages/$id"},"status":{"href":"/api/v3/statuses/$statusId","title":"Status $statusId"}$edit}}"""
        }

        fun collection(vararg elements: String): String =
            """{"_type":"WorkPackageCollection","total":${elements.size},"count":${elements.size},"pageSize":20,"offset":1,""" +
                """"_embedded":{"elements":[${elements.joinToString(",")}]}}"""
    }
}
