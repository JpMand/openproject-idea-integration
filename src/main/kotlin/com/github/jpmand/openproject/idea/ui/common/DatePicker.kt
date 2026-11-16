package com.github.jpmand.openproject.idea.ui.common

import com.github.jpmand.openproject.idea.OpenProjectBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.InplaceButton
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerDateModel
import javax.swing.SwingConstants

/** A month calendar in a popup to pick a date, or a date and time. */
object DatePicker {

    /** Picks a date: [onChosen] gets the clicked day, or null for "Clear" (offered when [clearable]). */
    fun chooseDate(anchor: JComponent, initial: LocalDate?, clearable: Boolean, onChosen: (LocalDate?) -> Unit) {
        lateinit var popup: JBPopup
        val calendar = CalendarPanel(initial ?: LocalDate.now(), initial) { day -> popup.cancel(); onChosen(day) }
        val footer = footer(clearable, onToday = { popup.cancel(); onChosen(LocalDate.now()) }, onClear = { popup.cancel(); onChosen(null) })
        popup = show(anchor, JPanel(BorderLayout()).apply {
            add(calendar, BorderLayout.CENTER)
            add(footer, BorderLayout.SOUTH)
        }, calendar)
    }

    /** Picks a date and a time (in the IDE's time zone): applied with "Apply", or null for "Clear". */
    fun chooseDateTime(anchor: JComponent, initial: LocalDateTime?, clearable: Boolean, onChosen: (LocalDateTime?) -> Unit) {
        lateinit var popup: JBPopup
        var day = initial?.toLocalDate() ?: LocalDate.now()
        val calendar = CalendarPanel(day, day) { picked -> day = picked }
        calendar.selectOnClick = true
        val zone = ZoneId.systemDefault()
        val time = JSpinner(SpinnerDateModel()).apply {
            editor = JSpinner.DateEditor(this, "HH:mm")
            value = Date.from((initial ?: LocalDateTime.now().withSecond(0).withNano(0)).atZone(zone).toInstant())
        }
        fun chosenTime(): LocalTime = (time.value as Date).toInstant().atZone(zone).toLocalTime().withSecond(0).withNano(0)
        val apply = JButton(OpenProjectBundle.message("detail.date.apply")).apply {
            addActionListener { popup.cancel(); onChosen(LocalDateTime.of(day, chosenTime())) }
        }
        val footer = footer(clearable, onToday = { calendar.select(LocalDate.now()); day = LocalDate.now() }, onClear = { popup.cancel(); onChosen(null) })
        popup = show(anchor, JPanel(BorderLayout()).apply {
            add(calendar, BorderLayout.NORTH)
            add(JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4))).apply {
                add(JBLabel(OpenProjectBundle.message("detail.date.time")))
                add(time)
                add(apply)
            }, BorderLayout.CENTER)
            add(footer, BorderLayout.SOUTH)
        }, calendar)
    }

    private fun footer(clearable: Boolean, onToday: () -> Unit, onClear: () -> Unit): JComponent =
        JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(10), JBUI.scale(4))).apply {
            add(ActionLink(OpenProjectBundle.message("detail.date.today")) { onToday() })
            if (clearable) add(ActionLink(OpenProjectBundle.message("detail.date.clear")) { onClear() })
        }

    private fun show(anchor: JComponent, content: JComponent, focus: JComponent): JBPopup =
        JBPopupFactory.getInstance().createComponentPopupBuilder(content.apply { border = JBUI.Borders.empty(6) }, focus)
            .setRequestFocus(true)
            .setCancelOnClickOutside(true)
            .setCancelKeyEnabled(true)
            .createPopup()
            .also { it.showUnderneathOf(anchor) }
}

/** Days of one month in weeks (starting on the locale's first day of the week), with buttons to change the month. */
internal class CalendarPanel(
    start: LocalDate,
    private var selected: LocalDate?,
    private val onPick: (LocalDate) -> Unit,
) : JPanel(BorderLayout()) {

    /** Clicking a day only marks it (the date and time picker applies it with its time). */
    var selectOnClick = false

    private var month = YearMonth.from(start)
    private val title = JBLabel("", SwingConstants.CENTER).apply { font = JBFont.label().asBold() }
    private val days = JPanel(GridLayout(0, 7, JBUI.scale(2), JBUI.scale(2))).apply { isOpaque = false }
    private val locale = Locale.getDefault()
    private val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek

    init {
        isOpaque = false
        isFocusable = true
        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyBottom(4)
            add(InplaceButton(OpenProjectBundle.message("detail.date.previous"), AllIcons.General.ArrowLeft) { month = month.minusMonths(1); render() }, BorderLayout.WEST)
            add(title, BorderLayout.CENTER)
            add(InplaceButton(OpenProjectBundle.message("detail.date.next"), AllIcons.General.ArrowRight) { month = month.plusMonths(1); render() }, BorderLayout.EAST)
        }, BorderLayout.NORTH)
        add(days, BorderLayout.CENTER)
        render()
    }

    fun select(day: LocalDate) {
        selected = day
        month = YearMonth.from(day)
        render()
    }

    private fun render() {
        title.text = month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        days.removeAll()
        (0 until 7).map { firstDayOfWeek.plus(it.toLong()) }.forEach { dayOfWeek ->
            days.add(JBLabel(dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, locale), SwingConstants.CENTER).apply {
                foreground = UIUtil.getContextHelpForeground()
            })
        }
        val first = month.atDay(1)
        val leading = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        repeat(leading) { days.add(JBLabel()) }
        val today = LocalDate.now()
        (1..month.lengthOfMonth()).map(month::atDay).forEach { day -> days.add(dayLabel(day, today)) }
        revalidate()
        repaint()
    }

    private fun dayLabel(day: LocalDate, today: LocalDate): JComponent = JBLabel(day.dayOfMonth.toString(), SwingConstants.CENTER).apply {
        border = JBUI.Borders.empty(3, 6)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        if (day == today) font = font.deriveFont(java.awt.Font.BOLD)
        if (day == selected) {
            isOpaque = true
            background = JBUI.CurrentTheme.List.Selection.background(true)
            foreground = JBUI.CurrentTheme.List.Selection.foreground(true)
        }
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (selectOnClick) select(day)
                onPick(day)
            }
        })
    }
}
