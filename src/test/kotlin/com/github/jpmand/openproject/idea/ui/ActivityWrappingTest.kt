package com.github.jpmand.openproject.idea.ui

import com.github.jpmand.openproject.idea.api.hal.Formattable
import com.github.jpmand.openproject.idea.api.model.Activity
import com.github.jpmand.openproject.idea.ui.common.HtmlView
import com.github.jpmand.openproject.idea.ui.common.ScrollableColumnPanel
import com.github.jpmand.openproject.idea.ui.detail.ActivityPanel
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Container
import javax.swing.ScrollPaneConstants

/** Long comments and changes in the Activity tab wrap at the width of the tab instead of scrolling sideways. */
class ActivityWrappingTest : BasePlatformTestCase() {

    fun `test a long comment wraps at the width of the tab`() {
        val words = "Slides from the keynote and the three workshop sessions are uploaded; the recordings will follow once the video team has finished editing them."
        val activity = Activity(
            type = "Activity::Comment",
            id = 1,
            comment = Formattable(format = "markdown", raw = words, html = "<p>$words $words</p>"),
            details = listOf(Formattable(raw = "Subject changed", html = "<strong>Subject</strong> changed from <i>$words</i> to <i>$words</i>")),
        )
        val panel = ActivityPanel(project, { HtmlView({ null }) {} }) { _, _, done -> done(true) }
        panel.show(listOf(activity), emptyMap(), canComment = true, internalCommentsAvailable = false)
        val scrollPane = ScrollableColumnPanel.scrollPane(panel, JBUI.Borders.empty(8, 12))
        assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, scrollPane.horizontalScrollBarPolicy)

        scrollPane.setSize(300, 400)
        layOut(scrollPane)
        val viewport = scrollPane.viewport
        assertEquals("the view is as wide as the tab", viewport.width, viewport.view.width)
        val html = UIUtil.findComponentsOfType(panel, HtmlView::class.java)
        assertEquals(1, html.size)
        assertTrue("the text is not wider than the tab: ${html.single().width}", html.single().width <= viewport.width)
        val lineHeight = html.single().getFontMetrics(html.single().font).height
        assertTrue("wrapped onto several lines: ${html.single().height}", html.single().height > 6 * lineHeight)

        // Wider: fewer lines.
        val narrowHeight = html.single().height
        scrollPane.setSize(900, 400)
        layOut(scrollPane)
        assertTrue(html.single().height < narrowHeight)
        assertEquals(viewport.width, viewport.view.width)
    }

    private fun layOut(component: Component) {
        if (component !is Container) return
        component.doLayout()
        component.components.forEach(::layOut)
    }
}
