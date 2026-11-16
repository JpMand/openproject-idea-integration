package com.github.jpmand.openproject.idea.ui

import com.github.jpmand.openproject.idea.api.Fixtures
import com.github.jpmand.openproject.idea.api.OpenProjectApi
import com.github.jpmand.openproject.idea.api.model.Priority
import com.github.jpmand.openproject.idea.api.model.Status
import com.github.jpmand.openproject.idea.api.model.WorkPackageType
import com.github.jpmand.openproject.idea.ui.common.StatusColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.awt.Color

class StatusColorsTest {

    @Test
    fun `colors are parsed from the api`() {
        assertEquals(Color(0xC3FAE8), StatusColors.parse("#C3FAE8"))
        assertEquals(Color(0xAABBCC), StatusColors.parse("#abc"))
        assertNull(StatusColors.parse(null))
        assertNull(StatusColors.parse(""))
        assertNull(StatusColors.parse("#12345"))
        assertNull(StatusColors.parse("#GGGGGG"))
    }

    @Test
    fun `text stays readable on the status color`() {
        assertEquals(Color.WHITE, StatusColors.textOn(Color(0xBE4BDB)))
        assertEquals(Color.WHITE, StatusColors.textOn(Color(0x1098AD)))
        assertEquals(Color(0x1E1F22), StatusColors.textOn(Color(0xC3FAE8)))
        assertEquals(Color(0x1E1F22), StatusColors.textOn(Color(0xFFEC99)))
    }

    @Test
    fun `work packages are matched to their status color`() {
        val workPackage = OpenProjectApi.decodeWorkPackage(Fixtures.json("work_package.json"))
        val statusId = workPackage.links["status"]!!.id!!.toLong()
        val colors = StatusColors.of(listOf(Status(statusId, "In progress", color = "#FFEC99"), Status(statusId + 1, "Done")))
        assertEquals(mapOf(statusId to Color(0xFFEC99)), colors)
        assertEquals(Color(0xFFEC99), StatusColors.of(workPackage, colors))
        assertNull(StatusColors.of(workPackage, emptyMap()))
    }

    @Test
    fun `types and priorities are matched to their colors`() {
        val workPackage = OpenProjectApi.decodeWorkPackage(Fixtures.json("work_package.json"))
        val typeId = workPackage.links["type"]!!.id!!.toLong()
        val priorityId = workPackage.links["priority"]!!.id!!.toLong()
        val types = StatusColors.ofTypes(listOf(WorkPackageType(typeId, "Task", color = "#1A67A3"), WorkPackageType(typeId + 1, "Bug")))
        val priorities = StatusColors.ofPriorities(listOf(Priority(priorityId, "High", color = "#F59F00")))
        assertEquals(Color(0x1A67A3), StatusColors.of(workPackage, "type", types))
        assertEquals(Color(0xF59F00), StatusColors.of(workPackage, "priority", priorities))
        assertNull(StatusColors.of(workPackage, "priority", types))
    }
}
