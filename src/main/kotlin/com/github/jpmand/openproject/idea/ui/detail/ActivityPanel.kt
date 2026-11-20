package com.github.jpmand.openproject.idea.ui.detail

import com.github.jpmand.openproject.idea.OpenProjectBundle
import com.github.jpmand.openproject.idea.api.model.Activity
import com.github.jpmand.openproject.idea.ui.common.ColumnLayout
import com.github.jpmand.openproject.idea.ui.common.HtmlView
import com.github.jpmand.openproject.idea.ui.common.MarkdownField
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.time.Instant
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/** The activity of a work package (changes and comments, oldest first) with a comment box at the bottom. */
class ActivityPanel(
    private val project: Project,
    private val html: () -> HtmlView,
    /** The avatar of the user at an href with a name. */
    private val avatar: (href: String?, name: String) -> Icon? = { _, _ -> null },
    private val onComment: (text: String, internal: Boolean, done: (Boolean) -> Unit) -> Unit,
) : JPanel(ColumnLayout()) {

    // Column layouts all the way down to the HTML: each part is measured at the width of the tab, so wrapped
    // comments and changes get the height of their lines.
    private val items = JPanel(ColumnLayout()).apply {
        isOpaque = false
    }
    private val composer = MarkdownField(project, "", 4).apply {
        // The prompt is the greyed hint of the empty box, kept while it has the focus, instead of a label above it.
        setPlaceholder(OpenProjectBundle.message("detail.comment.title"))
        setShowPlaceholderWhenFocused(true)
        addSettingsProvider { editor -> editor.setPlaceholderAttributes(TextAttributes().apply { foregroundColor = UIUtil.getContextHelpForeground() }) }
        border = JBUI.Borders.customLine(JBColor.border(), 1)
    }
    private val internalBox = JBCheckBox(OpenProjectBundle.message("detail.comment.internal")).apply { isVisible = false }
    private val submit = JButton(OpenProjectBundle.message("detail.comment.submit"))

    init {
        isOpaque = false
        add(items)
        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(12)
            add(composer, BorderLayout.CENTER)
            // No horizontal gap before the button: it lines up with the comment box.
            add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
                border = JBUI.Borders.emptyTop(6)
                add(submit)
                add(internalBox.apply { border = JBUI.Borders.emptyLeft(12) })
            }, BorderLayout.SOUTH)
        })
        submit.addActionListener {
            val text = composer.text.trim()
            if (text.isEmpty()) return@addActionListener
            // Disabling the focused button moves the focus on (to the subject of the page): it comes back here when done.
            submit.isEnabled = false
            onComment(text, internalBox.isSelected) { success ->
                submit.isEnabled = true
                if (success) {
                    composer.text = ""
                    internalBox.isSelected = false
                }
                composer.requestFocusInWindow()
            }
        }
    }

    fun show(activities: List<Activity>, userNames: Map<String, String>, canComment: Boolean, internalCommentsAvailable: Boolean) {
        items.removeAll()
        if (activities.isEmpty()) items.add(JBLabel(OpenProjectBundle.message("detail.activity.empty")).apply { foreground = UIUtil.getContextHelpForeground() })
        activities.forEach { items.add(item(it, userNames)) }
        composer.isEnabled = canComment
        submit.isEnabled = canComment
        internalBox.isVisible = canComment && internalCommentsAvailable
        items.revalidate()
        items.repaint()
    }

    private fun item(activity: Activity, userNames: Map<String, String>): JComponent {
        val panel = JPanel(ColumnLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.compound(JBUI.Borders.customLineTop(JBColor.border()), JBUI.Borders.empty(8, 0))
        }
        val user = activity.links.href("user")?.let(userNames::get) ?: activity.links.title("user") ?: OpenProjectBundle.message("detail.activity.someone")
        val time = activity.createdAt?.let { runCatching { DateFormatUtil.formatPrettyDateTime(Instant.parse(it).toEpochMilli()) }.getOrNull() }.orEmpty()
        val header = buildString {
            append("<b>").append(StringUtil.escapeXmlEntities(user)).append("</b>")
            if (time.isNotEmpty()) append("&nbsp;&nbsp;<span>").append(time).append("</span>")
            if (activity.internal == true) append("&nbsp;&nbsp;").append(OpenProjectBundle.message("detail.comment.internal.marker"))
        }
        panel.add(JBLabel("<html>$header</html>", avatar(activity.links.href("user"), user), JBLabel.LEFT).apply {
            iconTextGap = JBUI.scale(6)
        })
        val body = buildString {
            activity.comment?.html?.takeIf { activity.isComment }?.let(::append)
            if (activity.details.isNotEmpty()) {
                append("<ul>")
                activity.details.forEach { detail -> append("<li>").append(detail.html ?: detail.raw.orEmpty()).append("</li>") }
                append("</ul>")
            }
        }
        if (body.isNotEmpty()) panel.add(html().apply { setHtml(body) })
        return panel
    }
}
