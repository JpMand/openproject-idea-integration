package com.github.jpmand.openproject.idea.ui

import com.github.jpmand.openproject.idea.ui.common.ColumnLayout
import com.github.jpmand.openproject.idea.ui.common.TitleLayout
import com.intellij.util.ui.WrapLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JPanel
import javax.swing.JTextArea

class ColumnLayoutTest {

    private fun box(width: Int, height: Int) = JPanel().apply { preferredSize = Dimension(width, height) }

    @Test
    fun `a wrapping row gets its wrapped height in the first layout pass`() {
        val wrapping = JPanel(WrapLayout(FlowLayout.LEFT, 0, 0)).apply { repeat(5) { add(box(100, 20)) } }
        val next = box(50, 30)
        val column = JPanel(ColumnLayout()).apply {
            add(wrapping)
            add(next)
            setSize(250, 1000)
        }
        // one pass, the rows never laid out before: two boxes per line at 250 px, so three lines
        column.doLayout()
        assertEquals(250, wrapping.width)
        assertTrue("wrapped height ${wrapping.height}", wrapping.height >= 60)
        assertEquals(wrapping.height, next.y)
        assertEquals(wrapping.height + 30, column.preferredSize.height)
        // narrower: one box per line
        column.setSize(150, 1000)
        column.doLayout()
        assertTrue(wrapping.height >= 100)
        assertEquals(wrapping.height, next.y)
    }

    @Test
    fun `the title wraps beside the leading components or below them when narrow`() {
        val leading = box(80, 20)
        val text = JTextArea("Create wireframes for the new landing page of the website").apply { lineWrap = true; wrapStyleWord = true }
        val trailing = box(16, 16)
        val row = JPanel(TitleLayout(gap = 8, minTextWidth = 180)).apply {
            add(leading, TitleLayout.LEADING)
            add(text, TitleLayout.TEXT)
            add(trailing, TitleLayout.TRAILING)
        }
        row.setSize(400, 500)
        row.doLayout()
        assertEquals(88, text.x)
        assertTrue(text.width <= 400 - 88 - 24)
        assertEquals(trailing.x, text.x + text.width + 8)
        assertEquals(row.preferredSize.height, maxOf(text.y + text.height, leading.y + leading.height))

        row.setSize(250, 500)
        row.doLayout()
        // 250 - 80 - 8 - 24 < 180: the text starts on a line of its own
        assertEquals(0, text.x)
        assertTrue(text.y >= leading.y + leading.height)
        assertTrue("wraps on more lines", text.height > text.getFontMetrics(text.font).height)
    }
}
