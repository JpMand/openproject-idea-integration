package com.github.jpmand.openproject.idea.ui.list

import com.github.jpmand.openproject.idea.OpenProjectBundle
import com.github.jpmand.openproject.idea.api.model.WorkPackage
import com.github.jpmand.openproject.idea.ui.common.StatusColors
import com.intellij.icons.AllIcons
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableCellRenderer
import kotlin.math.roundToInt

/**
 * Cells of the work package table: the active task's row in bold with a marker, values that have an OpenProject
 * color (status, type, priority) in a rounded badge of that color, percentage columns (`percentageDone`,
 * `derivedPercentageDone`) with a progress bar behind the value, and the spent time in red when it is more than the
 * work (possible overtime; on a selected row a red left edge instead). Badge cells keep the table's background, so
 * selection, hover and stripes look as in any table of the theme, and the badge shows the server's exact color.
 */
class WorkPackageCellRenderer(
    /** The work package shown in a view row. */
    private val itemAt: (table: JTable, row: Int) -> WorkPackage?,
    /** The column id of a view column. */
    private val columnAt: (table: JTable, column: Int) -> String?,
    private val isActive: (WorkPackage) -> Boolean,
    /** The OpenProject color of a work package's value in a column, e.g. of its status or type (null: none). */
    private val cellColor: (item: WorkPackage, columnId: String) -> Color? = { _, _ -> null },
) : DefaultTableCellRenderer() {

    /** Progress of the cell being painted (0..100), null for other columns. */
    private var progress: Int? = null
    /** The OpenProject color of the value, painted as a badge behind the text. */
    private var badge: Color? = null
    /** Background painted instead of the table's (unselected cells only). */
    private var cellBackground: Color? = null
    /** Left edge painted on a selected cell. */
    private var edge: Color? = null
    private var selected = false

    override fun getTableCellRendererComponent(table: JTable, value: Any?, selected: Boolean, focused: Boolean, row: Int, column: Int): Component {
        // DefaultTableCellRenderer remembers colors set on it for every later cell; start from the table's colors.
        setForeground(null)
        setBackground(null)
        super.getTableCellRendererComponent(table, value, selected, false, row, column)
        val item = itemAt(table, row)
        val columnId = columnAt(table, column)
        val active = item != null && isActive(item)
        this.selected = selected
        font = if (active) font.deriveFont(Font.BOLD) else font.deriveFont(Font.PLAIN)
        icon = if (active && table.convertColumnIndexToModel(column) == 0) AllIcons.Vcs.History else null
        progress = if (item != null && columnId != null && WorkPackageCells.isPercentage(columnId)) WorkPackageCells.percentage(item, columnId) ?: 0 else null
        val overtime = item != null && columnId == WorkPackageCells.SPENT_TIME && WorkPackageCells.isOvertime(item)
        badge = if (item != null && columnId != null) cellColor(item, columnId) else null
        // Room for the badge's padding left of the text.
        border = JBUI.Borders.empty(0, if (badge != null) BADGE_INSET + BADGE_PADDING else 6)
        cellBackground = if (overtime && !selected) OVERTIME else null
        edge = if (overtime && selected) OVERTIME_EDGE else null
        badge?.let { foreground = StatusColors.textOn(it) }
        horizontalAlignment = if (progress != null) SwingConstants.CENTER else SwingConstants.LEADING
        toolTipText = if (overtime && item != null) {
            OpenProjectBundle.message("list.overtime.tooltip", WorkPackageCells.text(item, WorkPackageCells.SPENT_TIME), WorkPackageCells.text(item, WorkPackageCells.WORK))
        } else {
            null
        }
        return this
    }

    /**
     * Paints the background (the table's, or the overtime red), then bars, edges and badges, then the text. The
     * table may make the renderer opaque after it is configured (JBTable does for hovered and striped rows), so
     * this does not rely on the renderer being transparent.
     */
    override fun paintComponent(g: Graphics) {
        if (progress == null && cellBackground == null && edge == null && badge == null) {
            super.paintComponent(g)
            return
        }
        g.color = cellBackground ?: background
        g.fillRect(0, 0, width, height)
        val inset = JBUI.scale(2)
        progress?.let { progress ->
            val barWidth = ((width - 2 * inset) * progress.coerceIn(0, 100) / 100.0).roundToInt()
            if (barWidth > 0) {
                g.color = barColor()
                g.fillRect(inset, inset, barWidth, height - 2 * inset)
            }
        }
        edge?.let {
            g.color = it
            g.fillRect(0, 0, JBUI.scale(4), height)
        }
        badge?.let { paintBadge(g, it) }
        // The text only: the label UI's update() would fill the background over what was painted above.
        ui.paint(g, this)
    }

    /** A rounded badge around the (possibly shortened) text, like OpenProject's status bubble. */
    private fun paintBadge(g: Graphics, color: Color) {
        val text = text?.takeIf { it.isNotEmpty() } ?: return
        val insets = insets
        val view = Rectangle(insets.left, insets.top, width - insets.left - insets.right, height - insets.top - insets.bottom)
        val textBounds = Rectangle()
        SwingUtilities.layoutCompoundLabel(
            this, getFontMetrics(font), text, icon, verticalAlignment, horizontalAlignment,
            verticalTextPosition, horizontalTextPosition, view, Rectangle(), textBounds, iconTextGap,
        )
        val padding = JBUI.scale(BADGE_PADDING)
        val margin = JBUI.scale(BADGE_INSET)
        val x = (textBounds.x - padding).coerceAtLeast(margin)
        val right = (textBounds.x + textBounds.width + padding).coerceAtMost(width - margin)
        val y = JBUI.scale(1)
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = color
            val arc = JBUI.scale(10)
            g2.fillRoundRect(x, y, right - x, height - 2 * y, arc, arc)
        } finally {
            g2.dispose()
        }
    }

    private fun barColor(): Color = if (selected) ColorUtil.withAlpha(foreground, 0.25) else BAR

    companion object {
        /** OpenProject's green progress bar, toned down to stay readable behind the text. */
        val BAR: Color = JBColor(Color(0xBFE3C4), Color(0x34553A))

        /** Background of a spent time cell with possible overtime. */
        val OVERTIME: Color = JBColor(Color(0xF8C9C9), Color(0x6A2E30))

        /** Left edge of that cell on a selected row. */
        val OVERTIME_EDGE: Color = JBColor(Color(0xE55765), Color(0xDB5C5C))

        /** Space between the cell's left edge and a badge, and between the badge's edge and its text (unscaled). */
        private const val BADGE_INSET = 4
        private const val BADGE_PADDING = 6
    }
}
