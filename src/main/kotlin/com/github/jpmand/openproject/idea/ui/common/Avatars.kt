package com.github.jpmand.openproject.idea.ui.common

import com.github.jpmand.openproject.idea.core.OpenProjectConnection
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import javax.swing.Icon
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * User avatars as OpenProject shows them: the image the server names in a user's `avatar` property (a Gravatar
 * when the server uses Gravatar, or an uploaded avatar), and the user's initials on a colored circle while it
 * loads or when there is none (groups, placeholder users, avatars switched off, no Gravatar).
 *
 * Icons are returned at once and never block; images load in the background, a few at a time, and are kept per
 * server and user. Components that show avatars repaint through [addListener] when one arrives. The API key is
 * only ever sent to the OpenProject host (see the HTTP client), never to Gravatar.
 */
@Service(Service.Level.APP)
class Avatars(private val cs: CoroutineScope) {

    private enum class State { NEW, LOADING, DONE }

    private class Entry {
        val state = AtomicReference(State.NEW)
        @Volatile var image: BufferedImage? = null
        @Volatile var doneAt: TimeSource.Monotonic.ValueTimeMark? = null
    }

    private val entries = ConcurrentHashMap<String, Entry>()
    private val limiter = Semaphore(4)
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    /**
     * The avatar of the principal at [href] (`/api/v3/users/7`, a group or a placeholder user) named [name], [size]
     * pixels (unscaled) wide; null when there is neither. [connection] is used when the image has to be loaded.
     */
    fun icon(serverId: String, href: String?, name: String?, size: Int = 16, connection: suspend () -> OpenProjectConnection): Icon? {
        val principal = href?.takeIf(::isPrincipal)
        if (principal == null && name.isNullOrBlank()) return null
        val entry = principal?.let { p -> entries.computeIfAbsent("$serverId|$p") { Entry() }.also { load(it, p, connection) } }
        return AvatarIcon(entry, name.orEmpty(), size)
    }

    /** Calls [listener] on the EDT whenever an avatar image has arrived, until [parent] is disposed. */
    fun addListener(parent: Disposable, listener: () -> Unit) {
        listeners += listener
        Disposer.register(parent) { listeners -= listener }
    }

    private fun load(entry: Entry, href: String, connection: suspend () -> OpenProjectConnection) {
        // Failed or missing avatars are tried again after a while (e.g. once a Gravatar was added).
        if (entry.state.get() == State.DONE && entry.image == null && (entry.doneAt?.elapsedNow() ?: RETRY) >= RETRY) {
            entry.state.compareAndSet(State.DONE, State.NEW)
        }
        if (!entry.state.compareAndSet(State.NEW, State.LOADING)) return
        cs.launch(Dispatchers.IO) {
            try {
                entry.image = limiter.withPermit { fetch(connection(), href) }
                entry.doneAt = TimeSource.Monotonic.markNow()
                entry.state.set(State.DONE)
                if (entry.image != null) fireLoaded()
            } catch (e: CancellationException) {
                entry.state.set(State.NEW)
                throw e
            } catch (e: Exception) {
                thisLogger().debug("No avatar for $href", e)
                entry.doneAt = TimeSource.Monotonic.markNow()
                entry.state.set(State.DONE)
            }
        }
    }

    private suspend fun fetch(connection: OpenProjectConnection, href: String): BufferedImage? {
        val principal = connection.cached("principal:$href", 60.minutes) { connection.api.getJson(href) }
        val url = (principal["avatar"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val bytes = ByteArrayOutputStream().also { connection.api.download(url, it) }.toByteArray()
        return ImageIO.read(ByteArrayInputStream(bytes))?.let(::shrink)
    }

    private fun fireLoaded() {
        ApplicationManager.getApplication().invokeLater({ listeners.forEach { it() } }, ModalityState.any())
    }

    companion object {
        private val RETRY = 10.minutes
        private const val STORED_SIZE = 64
        private val principalHref = Regex("""/api/v3/(users|groups|placeholder_users|principals)/\d+$""")

        fun getInstance(): Avatars = service()

        /** Links to users, groups and placeholder users. */
        fun isPrincipal(href: String): Boolean = principalHref.containsMatchIn(href)

        /** "Jane Doe" -> "JD", "admin" -> "A". */
        fun initials(name: String): String =
            name.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }

        /** The circle color of a name's initials: the same name always gets the same color. */
        fun colorOf(name: String): Color = PALETTE[abs(name.trim().lowercase().hashCode() % PALETTE.size)]

        private val PALETTE = listOf(
            JBColor(0x1A67A3, 0x3D7DB8), JBColor(0x2F9E44, 0x3C9E50), JBColor(0xE8590C, 0xD9682A),
            JBColor(0x845EF7, 0x7D63D9), JBColor(0xC2255C, 0xC2416D), JBColor(0x0C8599, 0x2A92A3),
            JBColor(0x5C940D, 0x6A9A28), JBColor(0xD6336C, 0xC94C78), JBColor(0x495057, 0x6C737A),
        )

        /** Avatars are drawn at most this large (64 px also covers 2x screens): smaller copies keep the cache small. */
        private fun shrink(image: BufferedImage): BufferedImage {
            if (image.width <= STORED_SIZE && image.height <= STORED_SIZE) return image
            val result = BufferedImage(STORED_SIZE, STORED_SIZE, BufferedImage.TYPE_INT_ARGB)
            val g = result.createGraphics()
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                g.drawImage(image, 0, 0, STORED_SIZE, STORED_SIZE, null)
            } finally {
                g.dispose()
            }
            return result
        }
    }

    /** A round avatar: the loaded image, or else the initials of [name] on their color. */
    private class AvatarIcon(private val entry: Entry?, private val name: String, private val size: Int) : Icon {
        override fun getIconWidth(): Int = JBUI.scale(size)

        override fun getIconHeight(): Int = JBUI.scale(size)

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val diameter = JBUI.scale(size)
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                val circle = Ellipse2D.Float(x.toFloat(), y.toFloat(), diameter.toFloat(), diameter.toFloat())
                val image = entry?.image
                if (image != null) {
                    g2.clip(circle)
                    g2.drawImage(image, x, y, diameter, diameter, null)
                    return
                }
                g2.color = colorOf(name)
                g2.fill(circle)
                val initials = initials(name).ifEmpty { "?" }
                g2.color = Color.WHITE
                g2.font = (c?.font ?: JBUI.Fonts.label()).deriveFont(Font.BOLD, diameter * if (initials.length > 1) 0.42f else 0.5f)
                val metrics = g2.fontMetrics
                val textX = x + (diameter - metrics.stringWidth(initials)) / 2
                val textY = y + (diameter - metrics.height) / 2 + metrics.ascent
                g2.drawString(initials, textX, textY)
            } finally {
                g2.dispose()
            }
        }
    }
}
