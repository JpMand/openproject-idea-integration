package com.github.jpmand.openproject.idea.ui

import com.github.jpmand.openproject.idea.api.Fixtures
import com.github.jpmand.openproject.idea.api.OpenProjectApi
import com.github.jpmand.openproject.idea.ui.detail.AddRelationDialog
import com.github.jpmand.openproject.idea.ui.detail.ChooseWorkPackageDialog
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.net.ConnectException

/**
 * Dialogs that search in the background must show the results while they are open: they are modal, and EDT work
 * without a modality would only run after the dialog is closed.
 */
class ModalDialogsTest : BasePlatformTestCase() {

    private val workPackage by lazy { OpenProjectApi.decodeWorkPackage(Fixtures.json("work_package.json")) }

    /** Shows [dialog] as if it were open (modal) until [condition] holds. */
    private fun whileModal(dialog: DialogWrapper, what: String, condition: () -> Boolean) {
        LaterInvocator.enterModal(dialog)
        try {
            PlatformTestUtil.waitWithEventsDispatching(what, condition, 10)
        } finally {
            LaterInvocator.leaveModal(dialog)
            Disposer.dispose(dialog.disposable)
        }
    }

    fun `test work package search results appear while the dialog is open`() {
        val dialog = ChooseWorkPackageDialog(project, "Parent", allowNone = true) { listOf(workPackage) }
        whileModal(dialog, "search results") { dialog.candidateTitles().size == 2 }
        assertEquals("#${workPackage.id} ${workPackage.subject}", dialog.candidateTitles()[1])
    }

    fun `test a failing work package search says why`() {
        val dialog = ChooseWorkPackageDialog(project, "Parent", allowNone = false) { throw ConnectException("Connection refused") }
        whileModal(dialog, "error text") { dialog.emptyText().contains("Connection refused") }
        assertEmpty(dialog.candidateTitles())
        assertEquals("Cannot reach the OpenProject server (Connection refused).", dialog.emptyText())
    }

    fun `test relation candidates appear while the dialog is open`() {
        val searched = mutableListOf<Pair<String, String>>()
        val dialog = AddRelationDialog(project) { query, type ->
            searched += query to type
            listOf(workPackage)
        }
        whileModal(dialog, "relation candidates") { dialog.candidateIds() == listOf(workPackage.id) }
        assertEquals("" to "relates", searched.last())
        // a single candidate is selected, so the dialog can be confirmed at once
        assertEquals(workPackage.id, dialog.result().target.id)
    }

    fun `test a failing relation search says why`() {
        val dialog = AddRelationDialog(project) { _, _ -> throw ConnectException("Connection refused") }
        whileModal(dialog, "error text") { dialog.emptyText().contains("Connection refused") }
        assertEmpty(dialog.candidateIds())
    }
}
