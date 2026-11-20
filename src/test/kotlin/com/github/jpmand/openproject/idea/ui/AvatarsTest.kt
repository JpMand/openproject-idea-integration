package com.github.jpmand.openproject.idea.ui

import com.github.jpmand.openproject.idea.api.OpenProjectApi
import com.github.jpmand.openproject.idea.api.OpenProjectClientConfig
import com.github.jpmand.openproject.idea.api.OpenProjectHttpClient
import com.github.jpmand.openproject.idea.core.OpenProjectConnection
import com.github.jpmand.openproject.idea.ui.common.Avatars
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.JLabel

class AvatarsTest : BasePlatformTestCase() {

    private val requests = CopyOnWriteArrayList<HttpRequestData>()

    private fun png(color: Color): ByteArray {
        val image = BufferedImage(80, 80, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply { this.color = color; fillRect(0, 0, 80, 80); dispose() }
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    private fun user(id: Int, name: String, avatar: String?) =
        """{"_type":"User","id":$id,"name":"$name"${avatar?.let { ""","avatar":"$it"""" } ?: ""},"_links":{"self":{"href":"/api/v3/users/$id"}}}"""

    private val connection by lazy {
        val engine = MockEngine { request ->
            requests += request
            val json = headersOf(HttpHeaders.ContentType, "application/hal+json")
            when (request.url.host to request.url.encodedPath) {
                "op.example.com" to "/api/v3/users/7" -> respond(user(7, "Jane Doe", "https://secure.gravatar.com/avatar/jane?default=404"), headers = json)
                "op.example.com" to "/api/v3/users/8" -> respond(user(8, "John Smith", "https://secure.gravatar.com/avatar/john?default=404"), headers = json)
                "op.example.com" to "/api/v3/users/9" -> respond(user(9, "No Avatars", null), headers = json)
                "secure.gravatar.com" to "/avatar/jane" -> respond(png(Color.RED), headers = headersOf(HttpHeaders.ContentType, "image/png"))
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val config = OpenProjectClientConfig(baseUrl = "https://op.example.com", apiToken = "secret")
        OpenProjectConnection(OpenProjectApi(OpenProjectHttpClient.create(config, engine), config.baseUrl))
    }

    private fun paint(icon: Icon): BufferedImage {
        val image = BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
        image.createGraphics().also { icon.paintIcon(JLabel(), it, 0, 0); it.dispose() }
        return image
    }

    fun `test the server's avatar replaces the initials once loaded`() {
        val server = "https://op.example.com#loads"
        var loaded = 0
        Avatars.getInstance().addListener(testRootDisposable) { loaded++ }
        val icon = Avatars.getInstance().icon(server, "/api/v3/users/7", "Jane Doe", 16) { connection }!!
        assertEquals(16, icon.iconWidth)
        PlatformTestUtil.waitWithEventsDispatching("avatar", { loaded > 0 }, 10)
        // drawn round: the image in the middle, transparent in the corner
        assertEquals(Color.RED, Color(paint(icon).getRGB(8, 8)))
        assertEquals(0, paint(icon).getRGB(0, 0) ushr 24)
        // icons asked for later use the loaded image at once
        assertEquals(Color.RED, Color(paint(Avatars.getInstance().icon(server, "/api/v3/users/7", "Jane Doe", 24) { connection }!!).getRGB(12, 12)))

        // the API key goes to OpenProject only, never to Gravatar
        val toServer = requests.filter { it.url.host == "op.example.com" }
        val toGravatar = requests.filter { it.url.host == "secure.gravatar.com" }
        assertTrue(toServer.isNotEmpty() && toServer.all { it.headers[HttpHeaders.Authorization] != null })
        assertTrue(toGravatar.isNotEmpty() && toGravatar.none { it.headers[HttpHeaders.Authorization] != null })
    }

    fun `test users without an avatar keep their initials`() {
        val server = "https://op.example.com#initials"
        val avatars = Avatars.getInstance()
        val noGravatar = avatars.icon(server, "/api/v3/users/8", "John Smith", 16) { connection }!!
        val switchedOff = avatars.icon(server, "/api/v3/users/9", "No Avatars", 16) { connection }!!
        PlatformTestUtil.waitWithEventsDispatching("requests", { requests.any { it.url.encodedPath == "/avatar/john" } && requests.any { it.url.encodedPath == "/api/v3/users/9" } }, 10)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        // the circle in the name's color (sampled left of the initials)
        assertEquals(Avatars.colorOf("John Smith"), Color(paint(noGravatar).getRGB(2, 8)))
        assertEquals(Avatars.colorOf("No Avatars"), Color(paint(switchedOff).getRGB(2, 8)))
        // groups and other principals without a link to a user: initials, no request
        val group = avatars.icon(server, null, "Developers", 16) { connection }!!
        assertEquals(Avatars.colorOf("Developers"), Color(paint(group).getRGB(2, 8)))
        assertNull(avatars.icon(server, null, null, 16) { connection })
        assertNull(avatars.icon(server, "/api/v3/projects/3", "", 16) { connection })
    }

    fun `test initials and principal links`() {
        assertEquals("JD", Avatars.initials("Jane Doe"))
        assertEquals("JM", Avatars.initials(" Jane  Mary Doe "))
        assertEquals("A", Avatars.initials("admin"))
        assertEquals("", Avatars.initials("  "))
        assertEquals(Avatars.colorOf("Jane Doe"), Avatars.colorOf("jane doe"))
        assertTrue(Avatars.isPrincipal("/api/v3/users/7"))
        assertTrue(Avatars.isPrincipal("/api/v3/groups/3"))
        assertTrue(Avatars.isPrincipal("/api/v3/placeholder_users/5"))
        assertFalse(Avatars.isPrincipal("/api/v3/projects/7"))
        assertFalse(Avatars.isPrincipal("urn:openproject-org:api:v3:undisclosed"))
    }
}
