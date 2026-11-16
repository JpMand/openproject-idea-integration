package com.github.jpmand.openproject.idea.ui.common

import com.intellij.ide.BrowserUtil
import com.intellij.ui.HyperlinkAdapter
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent

/**
 * Read-only view of server-rendered HTML (descriptions, comments, activity details). Links to work packages of
 * the same server are handed to [openWorkPackage], links to its attachments to [openAttachment]; everything else
 * opens in the browser.
 */
class HtmlView(
    private val baseUrl: () -> String?,
    private val openAttachment: ((Long) -> Unit)? = null,
    private val openWorkPackage: (Long) -> Unit,
) : JEditorPane() {

    init {
        editorKit = HTMLEditorKitBuilder().withWordWrapViewFactory().build()
        isEditable = false
        isOpaque = false
        border = JBUI.Borders.empty()
        putClientProperty(HONOR_DISPLAY_PROPERTIES, true)
        font = UIUtil.getLabelFont()
        addHyperlinkListener(object : HyperlinkAdapter() {
            override fun hyperlinkActivated(e: HyperlinkEvent) {
                open(e.description ?: e.url?.toString() ?: return)
            }
        })
    }

    fun setHtml(html: String?) {
        text = "<html><head><style>$CSS</style></head><body>${html.orEmpty()}</body></html>"
        caretPosition = 0
    }

    private fun open(href: String) {
        val base = baseUrl()
        val absolute = when {
            href.startsWith("/") && base != null -> base.substringBefore("://") + "://" + base.substringAfter("://").substringBefore('/') + href
            else -> href
        }
        workPackageId(absolute, base)?.let { return openWorkPackage(it) }
        openAttachment?.let { open -> attachmentId(absolute, base)?.let { return open(it) } }
        BrowserUtil.browse(absolute)
    }

    companion object {
        /** OpenProject's markup classes (op-uc-*) carry no styles here; keep lists and paragraphs readable. */
        private const val CSS = "ul { margin-left: 18px; list-style-type: disc; } ol { margin-left: 18px; } " +
            "p { margin-top: 2px; margin-bottom: 6px; } pre, code { font-family: monospace; }"

        private val workPackageLink = Regex("""/work_packages/(\d+)(?:[/?#].*)?$""")

        /** API (`/api/v3/attachments/12/content`) and web (`/attachments/12/notes.txt`) links of an attachment. */
        private val attachmentLink = Regex("""^(?:/api/v3)?/attachments/(\d+)(?:[/?#].*)?$""")

        fun attachmentId(url: String, baseUrl: String?): Long? {
            if (baseUrl == null || !url.startsWith(baseUrl)) return null
            return attachmentLink.find(url.removePrefix(baseUrl))?.groupValues?.get(1)?.toLongOrNull()
        }

        fun workPackageId(url: String, baseUrl: String?): Long? {
            if (baseUrl == null || !url.startsWith(baseUrl)) return null
            return workPackageLink.find(url.removePrefix(baseUrl))?.groupValues?.get(1)?.toLongOrNull()
        }
    }
}
