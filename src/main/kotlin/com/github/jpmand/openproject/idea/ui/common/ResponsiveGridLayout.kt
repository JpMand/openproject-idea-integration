package com.github.jpmand.openproject.idea.ui.common

import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager
import javax.swing.SwingUtilities

/**
 * Lays out its components in equal columns, as many as fit the available width (at least [minColumnWidth] each,
 * unscaled) but no more than [maxColumns]; each row is as high as its highest component. The preferred height
 * depends on the width the container gets, so a change of the column count revalidates the container.
 */
class ResponsiveGridLayout(
    private val maxColumns: Int = 3,
    private val minColumnWidth: Int = 200,
    private val hgap: Int = 16,
    private val vgap: Int = 8,
) : LayoutManager {

    /** The column count the last preferred size was computed for. */
    private var preferredColumns = -1

    /** Columns for a content width of [width] pixels. */
    fun columnsFor(width: Int): Int {
        val column = JBUI.scale(minColumnWidth)
        val gap = JBUI.scale(hgap)
        return ((width + gap) / (column + gap)).coerceIn(1, maxColumns.coerceAtLeast(1))
    }

    override fun addLayoutComponent(name: String?, comp: Component?) = Unit

    override fun removeLayoutComponent(comp: Component?) = Unit

    override fun preferredLayoutSize(target: Container): Dimension {
        val insets = target.insets
        val columns = columnsFor(availableWidth(target) - insets.left - insets.right)
        preferredColumns = columns
        // Only one column's width: asking for more would keep a scroll pane from narrowing the view to its width.
        return Dimension(JBUI.scale(minColumnWidth) + insets.left + insets.right, rowHeights(target, columns).let { heights ->
            heights.sum() + (heights.size - 1).coerceAtLeast(0) * JBUI.scale(vgap)
        } + insets.top + insets.bottom)
    }

    override fun minimumLayoutSize(target: Container): Dimension {
        val insets = target.insets
        return Dimension(JBUI.scale(minColumnWidth) + insets.left + insets.right, preferredLayoutSize(target).height)
    }

    override fun layoutContainer(target: Container) {
        val insets = target.insets
        val width = target.width - insets.left - insets.right
        val columns = columnsFor(width)
        val gap = JBUI.scale(hgap)
        val columnWidth = ((width - (columns - 1) * gap) / columns).coerceAtLeast(0)
        var y = insets.top
        rowHeights(target, columns).forEachIndexed { row, height ->
            visible(target).drop(row * columns).take(columns).forEachIndexed { column, component ->
                component.setBounds(insets.left + column * (columnWidth + gap), y, columnWidth, height)
            }
            y += height + JBUI.scale(vgap)
        }
        if (columns != preferredColumns && preferredColumns != -1) {
            // The preferred height was computed for another width: let the parents ask again.
            preferredColumns = columns
            SwingUtilities.invokeLater { target.revalidate(); target.repaint() }
        }
    }

    private fun visible(target: Container): List<Component> = target.components.filter { it.isVisible }

    private fun rowHeights(target: Container, columns: Int): List<Int> =
        visible(target).chunked(columns).map { row -> row.maxOf { it.preferredSize.height } }

    /** The container's width, or before its first layout the width of the nearest ancestor that has one. */
    private fun availableWidth(target: Container): Int {
        var container: Container? = target
        while (container != null && container.width == 0) container = container.parent
        return container?.width ?: Int.MAX_VALUE
    }
}
