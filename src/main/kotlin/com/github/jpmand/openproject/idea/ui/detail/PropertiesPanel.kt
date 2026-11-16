package com.github.jpmand.openproject.idea.ui.detail

import com.github.jpmand.openproject.idea.OpenProjectBundle
import com.github.jpmand.openproject.idea.ui.common.ColumnLayout
import com.github.jpmand.openproject.idea.ui.common.DatePicker
import com.github.jpmand.openproject.idea.ui.common.ResponsiveGridLayout
import com.github.jpmand.openproject.idea.ui.list.Labels
import com.github.jpmand.openproject.idea.ui.list.WorkPackageCells
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.text.JTextComponent

/**
 * The attributes of a work package (incl. custom fields) in the groups of the work package form ("Details",
 * "People", ...), each group in up to three columns as the width allows. Empty attributes are shown too; those
 * the schema marks as writable can be changed with an editor that suits their type.
 */
class PropertiesPanel(
    /** Opens a chooser for a linked attribute ([EditorKind.LINK], [EditorKind.LINKS], [EditorKind.WORK_PACKAGE]). */
    private val onChoose: (attribute: String, kind: EditorKind, anchor: JComponent) -> Unit,
    /** Saves a property value; `done` is told whether that succeeded. */
    private val onSave: (attribute: String, value: JsonElement, done: (Boolean) -> Unit) -> Unit,
) : JPanel() {

    private var details: WorkPackageDetails? = null

    /** While a value is edited, updates are kept back so that the editor is not replaced. */
    private var editing = false

    init {
        // Groups measured at the panel's width: their column count, and so their height, depends on it.
        layout = ColumnLayout()
        isOpaque = false
        border = JBUI.Borders.empty(12)
    }

    fun show(details: WorkPackageDetails) {
        this.details = details
        if (!editing) rebuild()
    }

    private fun rebuild() {
        val details = details ?: return
        removeAll()
        add(JBLabel(OpenProjectBundle.message("detail.properties")).apply { font = JBFont.h4().asBold() })
        PropertyEditing.groups(details).forEach { group ->
            if (group.name.isNotBlank()) add(TitledSeparator(group.name))
            add(JPanel(ResponsiveGridLayout()).apply {
                isOpaque = false
                border = JBUI.Borders.empty(4, 0, 8, 0)
                group.attributes.forEach { attribute -> add(cell(details, attribute)) }
            })
        }
        revalidate()
        repaint()
    }

    private fun cell(details: WorkPackageDetails, attribute: String): JComponent {
        val cell = JPanel(BorderLayout()).apply { isOpaque = false }
        val name = details.field(attribute)?.name ?: Labels.humanize(attribute)
        cell.add(JBLabel(name).apply {
            foreground = UIUtil.getContextHelpForeground()
            font = JBFont.small()
            toolTipText = name
        }, BorderLayout.NORTH)
        cell.add(value(details, attribute, cell), BorderLayout.CENTER)
        return cell
    }

    private fun value(details: WorkPackageDetails, attribute: String, cell: JPanel): JComponent {
        val workPackage = details.workPackage
        val field = details.field(attribute)
        val kind = field?.let(PropertyEditing::kindOf)
        val writable = kind != null && details.isWritable(attribute)
        val required = field?.required == true
        val text = WorkPackageCells.text(workPackage, attribute)
        val shown = text.ifBlank { OpenProjectBundle.message("detail.unassigned") }
        if (kind == EditorKind.BOOLEAN) {
            return JBCheckBox(null, PropertyEditing.booleanValue(workPackage, attribute)).apply {
                isOpaque = false
                isEnabled = writable
                addActionListener {
                    isEnabled = false
                    onSave(attribute, JsonPrimitive(isSelected)) { success -> isEnabled = true; if (!success) isSelected = !isSelected }
                }
            }
        }
        if (kind == null || !writable) {
            return JBLabel(shown).apply {
                toolTipText = text.takeIf(String::isNotBlank)
                if (text.isBlank()) foreground = UIUtil.getContextHelpForeground()
            }
        }
        return ActionLink(shown).apply {
            toolTipText = OpenProjectBundle.message("detail.property.change", field.name) + if (text.isNotBlank()) ": $text" else ""
            when (kind) {
                EditorKind.LINK, EditorKind.LINKS, EditorKind.WORK_PACKAGE -> {
                    setDropDownLinkIcon()
                    addActionListener { onChoose(attribute, kind, this) }
                }
                EditorKind.DATE -> addActionListener {
                    DatePicker.chooseDate(this, PropertyEditing.dateValue(workPackage, attribute), clearable = !required) { date ->
                        onSave(attribute, PropertyEditing.date(date)) {}
                    }
                }
                EditorKind.DATE_TIME -> addActionListener {
                    DatePicker.chooseDateTime(this, PropertyEditing.dateTimeValue(workPackage, attribute), clearable = !required) { dateTime ->
                        onSave(attribute, PropertyEditing.dateTime(dateTime)) {}
                    }
                }
                else -> addActionListener { editText(cell, this, attribute, kind, required) }
            }
        }
    }

    /** Replaces the value in [cell] by a text field: Enter (Ctrl+Enter for long text) or leaving it saves, Escape cancels. */
    private fun editText(cell: JPanel, valueComponent: JComponent, attribute: String, kind: EditorKind, required: Boolean) {
        val workPackage = details?.workPackage ?: return
        editing = true
        val initial = PropertyEditing.editText(workPackage, attribute, kind)
        val editor: JTextComponent
        val component: JComponent
        if (kind == EditorKind.LONG_TEXT) {
            editor = JBTextArea(initial, 4, 20).apply { lineWrap = true; wrapStyleWord = true }
            component = JBScrollPane(editor)
            editor.toolTipText = OpenProjectBundle.message("detail.property.long.text.hint")
        } else {
            editor = JBTextField(initial)
            component = editor
            if (kind == EditorKind.DURATION) editor.emptyText.text = OpenProjectBundle.message("detail.property.duration.hint")
            if (kind == EditorKind.DAYS) editor.emptyText.text = OpenProjectBundle.message("detail.property.days.hint")
        }
        cell.remove(valueComponent)
        cell.add(component, BorderLayout.CENTER)
        cell.revalidate()
        cell.repaint()
        editor.requestFocusInWindow()
        if (kind != EditorKind.LONG_TEXT) editor.selectAll()

        var finished = false
        fun invalid() {
            component.putClientProperty("JComponent.outline", "error")
            editor.toolTipText = OpenProjectBundle.message("detail.property.invalid")
            component.repaint()
        }
        fun finish(save: Boolean, leaving: Boolean = false) {
            if (finished) return
            if (!save || editor.text == initial) {
                finished = true
                stopEditing()
                return
            }
            val value = PropertyEditing.parse(kind, editor.text, required)
            if (value == null) {
                if (leaving) {
                    finished = true
                    stopEditing()
                } else {
                    invalid()
                }
                return
            }
            finished = true
            editor.isEditable = false
            onSave(attribute, value) { success ->
                if (success) {
                    stopEditing()
                } else {
                    finished = false
                    editor.isEditable = true
                    editor.requestFocusInWindow()
                }
            }
        }
        editor.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                val submit = e.keyCode == KeyEvent.VK_ENTER && (kind != EditorKind.LONG_TEXT || e.isControlDown || e.isMetaDown)
                when {
                    submit -> { e.consume(); finish(save = true) }
                    e.keyCode == KeyEvent.VK_ESCAPE -> { e.consume(); finish(save = false) }
                }
            }
        })
        editor.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                if (!e.isTemporary && editor.isEditable) finish(save = true, leaving = true)
            }
        })
    }

    private fun stopEditing() {
        editing = false
        rebuild()
    }

    @TestOnly
    internal fun isEditing(): Boolean = editing
}
