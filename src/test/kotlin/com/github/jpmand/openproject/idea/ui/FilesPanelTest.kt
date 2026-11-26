package com.github.jpmand.openproject.idea.ui

import com.github.jpmand.openproject.idea.api.model.Attachment
import com.github.jpmand.openproject.idea.ui.detail.FilesPanel
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent

/** A file of the Files tab opens on double-click and Enter, as with "Open" in its toolbar. */
class FilesPanelTest : BasePlatformTestCase() {

    private val opened = mutableListOf<Long>()

    private fun panel() = FilesPanel(onUpload = {}, onUploadFiles = {}, onDownload = {}, onOpen = { opened += it.id }, onDelete = {}).apply {
        show(listOf(Attachment(id = 3, fileName = "notes.txt"), Attachment(id = 4, fileName = "slides.pdf")), canAttach = true)
        setSize(600, 300)
        doLayout()
        table().setSize(600, 200)
    }

    fun `test enter opens the selected file`() {
        val table = panel().table()
        table.selectionModel.setSelectionInterval(1, 1)
        val enter = KeyEvent(table, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER, '\n')
        table.keyListeners.forEach { it.keyPressed(enter) }
        assertEquals(listOf(4L), opened)
    }

    fun `test double-click opens the file`() {
        val table = panel().table()
        table.selectionModel.setSelectionInterval(0, 0)
        val cell = table.getCellRect(0, 0, true)
        val x = cell.x + 5
        val y = cell.y + 5
        val now = System.currentTimeMillis()
        listOf(
            MouseEvent(table, MouseEvent.MOUSE_PRESSED, now, 0, x, y, 1, false, MouseEvent.BUTTON1),
            MouseEvent(table, MouseEvent.MOUSE_RELEASED, now, 0, x, y, 1, false, MouseEvent.BUTTON1),
            MouseEvent(table, MouseEvent.MOUSE_CLICKED, now, 0, x, y, 1, false, MouseEvent.BUTTON1),
            MouseEvent(table, MouseEvent.MOUSE_PRESSED, now + 50, 0, x, y, 2, false, MouseEvent.BUTTON1),
            MouseEvent(table, MouseEvent.MOUSE_RELEASED, now + 50, 0, x, y, 2, false, MouseEvent.BUTTON1),
            MouseEvent(table, MouseEvent.MOUSE_CLICKED, now + 50, 0, x, y, 2, false, MouseEvent.BUTTON1),
        ).forEach { event ->
            // Headless tests cannot dispatch mouse events: the double-click listener gets them directly.
            table.mouseListeners.filter { it.javaClass.name.contains("ClickListener") }.forEach { listener ->
                when (event.id) {
                    MouseEvent.MOUSE_PRESSED -> listener.mousePressed(event)
                    MouseEvent.MOUSE_RELEASED -> listener.mouseReleased(event)
                    else -> listener.mouseClicked(event)
                }
            }
        }
        assertEquals(listOf(3L), opened)
    }

    fun `test nothing is opened without a selection`() {
        val table = panel().table()
        table.clearSelection()
        val enter = KeyEvent(table, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER, '\n')
        table.keyListeners.forEach { it.keyPressed(enter) }
        assertEmpty(opened)
    }
}
