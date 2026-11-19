package com.github.jpmand.openproject.idea.core

import com.github.jpmand.openproject.idea.api.OpenProjectApi
import com.github.jpmand.openproject.idea.api.OpenProjectClientConfig
import com.github.jpmand.openproject.idea.api.OpenProjectHttpClient
import com.github.jpmand.openproject.idea.api.OpenProjectUrls
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.util.net.ssl.CertificateManager
import io.ktor.client.engine.HttpClientEngine
import org.jetbrains.annotations.TestOnly
import java.net.Authenticator
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * Shares one HTTP client per (server, token) across the Tasks connector, editors and actions.
 * A new token for the same server replaces (and closes) the previous client.
 */
@Service(Service.Level.APP)
class OpenProjectClientPool : Disposable {

    private data class Key(val baseUrl: String, val tokenHash: String)

    private val connections = ConcurrentHashMap<Key, OpenProjectConnection>()

    /** The HTTP engine of new clients; null: the JDK HTTP client. */
    @Volatile
    private var engine: HttpClientEngine? = null

    fun connection(baseUrl: String, token: String?): OpenProjectConnection {
        val normalized = OpenProjectUrls.normalizeBaseUrl(baseUrl)
        val key = Key(normalized, hash(token.orEmpty()))
        connections[key]?.let { return it }
        synchronized(this) {
            connections[key]?.let { return it }
            connections.keys.filter { it.baseUrl == normalized }.forEach { stale -> connections.remove(stale)?.close() }
            val connection = OpenProjectConnection(OpenProjectApi(OpenProjectHttpClient.create(clientConfig(normalized, token), engine), normalized))
            connections[key] = connection
            return connection
        }
    }

    override fun dispose() {
        closeAll()
    }

    private fun closeAll() {
        synchronized(this) {
            connections.values.forEach { it.close() }
            connections.clear()
        }
    }

    /** Makes new connections use [engine] (e.g. a Ktor MockEngine) until [parent] is disposed. */
    @TestOnly
    fun useEngine(engine: HttpClientEngine, parent: Disposable) {
        closeAll()
        this.engine = engine
        Disposer.register(parent) {
            this.engine = null
            closeAll()
        }
    }

    private fun clientConfig(baseUrl: String, token: String?) = OpenProjectClientConfig(
        baseUrl = baseUrl,
        apiToken = token,
        userAgent = userAgent(),
        sslContext = runCatching { CertificateManager.getInstance().sslContext }.getOrNull(),
        authenticator = Authenticator.getDefault(),
    )

    companion object {
        fun getInstance(): OpenProjectClientPool = service()

        /** The server and a hash of the token: what tells connections apart. */
        fun key(baseUrl: String, token: String?): String = OpenProjectUrls.normalizeBaseUrl(baseUrl) + "|" + hash(token.orEmpty())

        private fun hash(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

        /** The plugin version, stamped into plugin.properties by the build. */
        private val pluginVersion: String by lazy {
            runCatching {
                OpenProjectClientPool::class.java.getResourceAsStream("plugin.properties")?.use { Properties().apply { load(it) }.getProperty("version") }
            }.getOrNull()?.takeIf { it.isNotBlank() && !it.startsWith("$") } ?: "dev"
        }

        fun userAgent(): String {
            val version = pluginVersion
            val build = runCatching { ApplicationInfo.getInstance().build.asString() }.getOrDefault("unknown")
            return "OpenProject-IntelliJ-Plugin/$version ($build)"
        }
    }
}
