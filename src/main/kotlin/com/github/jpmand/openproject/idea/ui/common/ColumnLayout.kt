package com.github.jpmand.openproject.idea.ui.common

import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager
import java.awt.LayoutManager2
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JViewport
import javax.swing.ScrollPaneConstants
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.border.Border

/**
 * Stacks the visible components top to bottom, each as wide as the container. Every component is given that
 * width before it is asked for its preferred height, so content that wraps (WrapLayout rows, wrapping text,
 * HTML) gets the height of its wrapped lines in the same layout pass: measured at a width it does not have yet,
 * it would report one line and the next row would be painted over it.
 */
class ColumnLayout : LayoutManager {

    override fun addLayoutComponent(name: String?, comp: Component?) = Unit

    override fun removeLayoutComponent(comp: Component?) = Unit

    override fun preferredLayoutSize(target: Container): Dimension {
        val insets = target.insets
        val width = contentWidth(target)
        val height = visible(target).sumOf { heightAt(it, width) }
        // A small width: the container takes the width it is given and must not widen a scroll pane's view.
        return Dimension(JBUI.scale(MIN_WIDTH) + insets.left + insets.right, height + insets.top + insets.bottom)
    }

    override fun minimumLayoutSize(target: Container): Dimension = preferredLayoutSize(target)

    override fun layoutContainer(target: Container) {
        val insets = target.insets
        val width = contentWidth(target)
        var y = insets.top
        visible(target).forEach { component ->
            val height = heightAt(component, width)
            component.setBounds(insets.left, y, width, height)
            y += height
        }
    }

    private fun visible(target: Container): List<Component> = target.components.filter { it.isVisible }

    /** The width for the components: the viewport's when [target] is a scroll pane's view (it is resized later). */
    private fun contentWidth(target: Container): Int {
        val insets = target.insets
        val width = (target.parent as? JViewport)?.width?.takeIf { it > 0 } ?: target.width
        return (width - insets.left - insets.right).coerceAtLeast(0)
    }

    companion object {
        private const val MIN_WIDTH = 80

        /** The preferred height of [component] when it is [width] pixels wide. */
        fun heightAt(component: Component, width: Int): Int {
            if (width > 0 && component.width != width) component.setSize(width, component.height.coerceAtLeast(1))
            return component.preferredSize.height
        }
    }
}

/**
 * A [ColumnLayout] panel to use as a scroll pane's view: always as wide as the viewport (it scrolls vertically
 * only), and at least as high, with the content at the top.
 */
class ScrollableColumnPanel : JPanel(ColumnLayout()), Scrollable {
    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = JBUI.scale(16)

    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int =
        if (orientation == SwingConstants.VERTICAL) visibleRect.height else visibleRect.width

    override fun getScrollableTracksViewportWidth(): Boolean = true

    override fun getScrollableTracksViewportHeight(): Boolean = (parent as? JViewport)?.let { it.height > preferredSize.height } ?: false

    companion object {
        /**
         * A scroll pane showing [content] as wide as the viewport, inside [border]: it scrolls vertically only, and text
         * in it wraps instead of widening the view.
         */
        fun scrollPane(content: JComponent, border: Border = JBUI.Borders.empty()): JBScrollPane =
            JBScrollPane(
                ScrollableColumnPanel().apply {
                    this.border = border
                    add(content)
                },
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
            ).apply { this.border = JBUI.Borders.empty() }
    }
}

/**
 * A title row: [LEADING] components (badge, id) on the left, the [TEXT] in the remaining width, where it wraps,
 * and an optional [TRAILING] button; leading and trailing are centered on the first line of the text. When less
 * than [minTextWidth] (unscaled) would be left for the text, it goes below the leading components instead.
 */
class TitleLayout(private val gap: Int = 8, private val minTextWidth: Int = 180) : LayoutManager2 {
    private var leading: Component? = null
    private var text: Component? = null
    private var trailing: Component? = null

    override fun addLayoutComponent(comp: Component, constraints: Any?) {
        when (constraints) {
            LEADING -> leading = comp
            TEXT -> text = comp
            TRAILING -> trailing = comp
            else -> throw IllegalArgumentException("Unknown constraint $constraints")
        }
    }

    override fun addLayoutComponent(name: String?, comp: Component) = addLayoutComponent(comp, name)

    override fun removeLayoutComponent(comp: Component) {
        if (leading === comp) leading = null
        if (text === comp) text = null
        if (trailing === comp) trailing = null
    }

    override fun preferredLayoutSize(target: Container): Dimension {
        val insets = target.insets
        val bounds = arrange(target)
        val height = bounds.values.maxOfOrNull { it.y + it.height } ?: insets.top
        return Dimension(JBUI.scale(80) + insets.left + insets.right, height + insets.bottom)
    }

    override fun minimumLayoutSize(target: Container): Dimension = preferredLayoutSize(target)

    override fun maximumLayoutSize(target: Container): Dimension = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)

    override fun getLayoutAlignmentX(target: Container): Float = 0f

    override fun getLayoutAlignmentY(target: Container): Float = 0f

    override fun invalidateLayout(target: Container) = Unit

    override fun layoutContainer(target: Container) {
        arrange(target).forEach { (component, bounds) -> component.bounds = bounds }
    }

    private fun arrange(target: Container): Map<Component, Rectangle> {
        val insets = target.insets
        val gap = JBUI.scale(gap)
        val width = (target.width - insets.left - insets.right).coerceAtLeast(0)
        val leading = leading?.takeIf { it.isVisible }
        val trailing = trailing?.takeIf { it.isVisible }
        val text = text?.takeIf { it.isVisible }
        val leadingSize = leading?.preferredSize ?: Dimension()
        val trailingSize = trailing?.preferredSize ?: Dimension()
        val trailingWidth = if (trailing != null) trailingSize.width + gap else 0
        // Too narrow beside the leading components: the text gets a line of its own below them.
        val below = leading != null && width - leadingSize.width - gap - trailingWidth < JBUI.scale(minTextWidth)
        val textX = insets.left + if (leading != null && !below) leadingSize.width + gap else 0
        val available = (width - (textX - insets.left) - trailingWidth).coerceAtLeast(JBUI.scale(40))
        // Read-only text is only as wide as it needs to be, so a trailing button follows a short title; an editor
        // takes all the room.
        val textWidth = (text as? JTextArea)?.let { minOf(available, naturalWidth(it)) } ?: available
        val textHeight = text?.let { ColumnLayout.heightAt(it, textWidth) } ?: 0
        // The height of the first line of the text: what leading and trailing are centered on.
        val line = text?.let { it.getFontMetrics(it.font).height + it.insetsHeight() }?.coerceAtMost(textHeight) ?: 0
        val result = linkedMapOf<Component, Rectangle>()
        if (below) {
            result[leading!!] = Rectangle(insets.left, insets.top, leadingSize.width, leadingSize.height)
            val top = insets.top + leadingSize.height + JBUI.scale(4)
            val band = maxOf(line, trailingSize.height)
            text?.let { result[it] = Rectangle(textX, top + (band - line) / 2, textWidth, textHeight) }
            trailing?.let { result[it] = Rectangle(textX + textWidth + gap, top + (band - trailingSize.height) / 2, trailingSize.width, trailingSize.height) }
            return result
        }
        val band = maxOf(line, leadingSize.height, trailingSize.height)
        leading?.let { result[it] = Rectangle(insets.left, insets.top + (band - leadingSize.height) / 2, leadingSize.width, leadingSize.height) }
        text?.let { result[it] = Rectangle(textX, insets.top + (band - line) / 2, textWidth, textHeight) }
        trailing?.let {
            result[it] = Rectangle(textX + textWidth + gap, insets.top + (band - trailingSize.height) / 2, trailingSize.width, trailingSize.height)
        }
        return result
    }

    private fun naturalWidth(text: JTextArea): Int {
        val metrics = text.getFontMetrics(text.font)
        val widest = text.text.lines().maxOfOrNull(metrics::stringWidth) ?: 0
        return widest + text.insets.left + text.insets.right + JBUI.scale(2)
    }

    private fun Component.insetsHeight(): Int = (this as? Container)?.insets?.let { it.top + it.bottom } ?: 0

    companion object {
        const val LEADING = "leading"
        const val TEXT = "text"
        const val TRAILING = "trailing"
    }
}
