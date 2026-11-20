package com.github.jpmand.openproject.idea.ui

import com.github.jpmand.openproject.idea.api.Fixtures
import com.github.jpmand.openproject.idea.api.OpenProjectApi
import com.github.jpmand.openproject.idea.api.WorkPackagePatch
import com.github.jpmand.openproject.idea.api.model.AttributeGroup
import com.github.jpmand.openproject.idea.api.model.Schema
import com.github.jpmand.openproject.idea.ui.common.ResponsiveGridLayout
import com.github.jpmand.openproject.idea.ui.detail.EditorKind
import com.github.jpmand.openproject.idea.ui.detail.HierarchyItem
import com.github.jpmand.openproject.idea.ui.detail.PropertyEditing
import com.github.jpmand.openproject.idea.ui.detail.WorkPackageHierarchy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Dimension
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.swing.JPanel

class PropertyEditingTest {
    private val schema = Schema(Fixtures.json("work_package_schema.json"))

    @Test
    fun `attribute groups are read from the schema`() {
        assertEquals(listOf("People", "Estimates and progress", "Details", "Other"), schema.attributeGroups.map { it.name })
        assertEquals(listOf("assignee", "responsible"), schema.attributeGroups.first().attributes)
    }

    @Test
    fun `groups show the known attributes and expand the date range`() {
        val groups = PropertyEditing.groups(schema.attributeGroups, schema.fields.keys)
        assertEquals(
            listOf("startDate", "dueDate", "duration", "category", "version", "priority", "customField3", "customField4"),
            groups.single { it.name == "Details" }.attributes,
        )
        // milestones have a "date" attribute of their own
        val milestone = PropertyEditing.groups(listOf(AttributeGroup("Details", listOf("date"))), setOf("date", "startDate"))
        assertEquals(listOf("date"), milestone.single().attributes)
        // groups without known attributes are left out
        assertTrue(PropertyEditing.groups(listOf(AttributeGroup("Empty", listOf("nothing"))), schema.fields.keys).isEmpty())
    }

    @Test
    fun `without groups the usual attributes are shown`() {
        val fallback = PropertyEditing.groups(emptyList(), schema.fields.keys).single()
        assertEquals("", fallback.name)
        assertTrue(fallback.attributes.containsAll(listOf("priority", "assignee", "customField3", "customField8")))
        assertFalse(fallback.attributes.any { it in setOf("subject", "status", "type", "description") })
        assertTrue(PropertyEditing.groups(emptyList(), emptySet()).single().attributes.contains("assignee"))
    }

    @Test
    fun `editors follow the schema type`() {
        fun kind(attribute: String) = PropertyEditing.kindOf(schema[attribute]!!)
        assertEquals(EditorKind.LINK, kind("assignee"))
        assertEquals(EditorKind.LINK, kind("priority"))
        assertEquals(EditorKind.LINKS, kind("customField6"))
        assertEquals(EditorKind.WORK_PACKAGE, kind("parent"))
        assertEquals(EditorKind.DATE, kind("startDate"))
        assertEquals(EditorKind.DATE_TIME, kind("createdAt"))
        assertEquals(EditorKind.DURATION, kind("estimatedTime"))
        assertEquals(EditorKind.DAYS, kind("duration"))
        assertEquals(EditorKind.INTEGER, kind("percentageDone"))
        assertEquals(EditorKind.FLOAT, kind("customField4"))
        assertEquals(EditorKind.BOOLEAN, kind("customField3"))
        assertEquals(EditorKind.LONG_TEXT, kind("customField5"))
        assertEquals(EditorKind.TEXT, kind("customField7"))
        assertTrue(schema["customField8"]!!.required)
        assertFalse(schema["spentTime"]!!.writable)
    }

    @Test
    fun `allowed values are listed or linked as a collection`() {
        assertEquals("/api/v3/work_packages/24971/available_assignees", schema["assignee"]!!.allowedValuesHref)
        assertTrue(schema["assignee"]!!.allowedOptions.isEmpty())
        assertNull(schema["priority"]!!.allowedValuesHref)
        assertEquals(listOf("Normal", "High"), schema["priority"]!!.allowedOptions.map { it.title })
    }

    @Test
    fun `entered values are converted for the api`() {
        assertEquals(JsonPrimitive("abc"), PropertyEditing.parse(EditorKind.TEXT, " abc ", required = false))
        assertEquals(JsonNull, PropertyEditing.parse(EditorKind.TEXT, " ", required = false))
        assertNull(PropertyEditing.parse(EditorKind.TEXT, "", required = true))
        assertEquals(JsonPrimitive(42L), PropertyEditing.parse(EditorKind.INTEGER, "42", required = false))
        assertNull(PropertyEditing.parse(EditorKind.INTEGER, "4.2", required = false))
        assertEquals(JsonPrimitive(1.5), PropertyEditing.parse(EditorKind.FLOAT, "1,5", required = false))
        assertNull(PropertyEditing.parse(EditorKind.FLOAT, "x", required = false))
        assertEquals(JsonPrimitive("PT1H30M"), PropertyEditing.parse(EditorKind.DURATION, "1h 30m", required = false))
        assertEquals(JsonPrimitive("PT2H"), PropertyEditing.parse(EditorKind.DURATION, "2", required = false))
        assertNull(PropertyEditing.parse(EditorKind.DURATION, "soon", required = false))
        assertEquals(JsonPrimitive("P3D"), PropertyEditing.parse(EditorKind.DAYS, "3", required = false))
        assertNull(PropertyEditing.parse(EditorKind.DAYS, "-1", required = false))
        assertEquals(buildJsonObject { put("raw", "line 1\nline 2") }, PropertyEditing.parse(EditorKind.LONG_TEXT, "line 1\nline 2", required = false))
        assertEquals(JsonPrimitive("2026-11-16"), PropertyEditing.date(LocalDate.of(2026, 11, 16)))
        assertEquals(JsonNull, PropertyEditing.date(null))
        assertEquals(JsonPrimitive("2026-11-16T08:30:00Z"), PropertyEditing.dateTime(LocalDateTime.of(2026, 11, 16, 9, 30), ZoneOffset.ofHours(1)))
    }

    @Test
    fun `editors start with the current value`() {
        val workPackage = OpenProjectApi.decodeWorkPackage(JsonObject(Fixtures.json("work_package.json") + mapOf(
            "estimatedTime" to JsonPrimitive("PT2H30M"),
            "duration" to JsonPrimitive("P4D"),
            "customField4" to JsonPrimitive(0.5),
            "customField3" to JsonPrimitive(true),
            "customField5" to buildJsonObject { put("format", "markdown"); put("raw", "Some *notes*"); put("html", "<p>Some notes</p>") },
            "customField8" to JsonPrimitive("2026-01-31"),
            "createdAt" to JsonPrimitive("2026-01-31T10:15:00Z"),
        )))
        assertEquals("2h 30m", PropertyEditing.editText(workPackage, "estimatedTime", EditorKind.DURATION))
        assertEquals("4", PropertyEditing.editText(workPackage, "duration", EditorKind.DAYS))
        assertEquals("0.5", PropertyEditing.editText(workPackage, "customField4", EditorKind.FLOAT))
        assertEquals("Some *notes*", PropertyEditing.editText(workPackage, "customField5", EditorKind.LONG_TEXT))
        assertEquals("", PropertyEditing.editText(workPackage, "customField7", EditorKind.TEXT))
        assertTrue(PropertyEditing.booleanValue(workPackage, "customField3"))
        assertEquals(LocalDate.of(2026, 1, 31), PropertyEditing.dateValue(workPackage, "customField8"))
        assertNull(PropertyEditing.dateValue(workPackage, "startDate"))
        assertEquals(LocalDateTime.of(2026, 1, 31, 10, 15), PropertyEditing.dateTimeValue(workPackage, "createdAt", ZoneOffset.UTC))
    }

    @Test
    fun `multi value links are patched as arrays`() {
        val patch = WorkPackagePatch(3).links("customField6", listOf("/api/v3/custom_options/1", "/api/v3/custom_options/2")).build()
        val links = patch["_links"] as JsonObject
        assertEquals(2, (links["customField6"] as JsonArray).size)
        assertEquals(JsonArray(emptyList()), (WorkPackagePatch(3).links("customField6", emptyList()).build()["_links"] as JsonObject)["customField6"])
    }

    @Test
    fun `the hierarchy lists the ancestors and ends with the work package`() {
        val json = Fixtures.json("work_package.json")
        val links = json["_links"] as JsonObject
        val withAncestors = OpenProjectApi.decodeWorkPackage(JsonObject(json + ("_links" to JsonObject(links + ("ancestors" to buildJsonArray {
            add(buildJsonObject { put("href", "/api/v3/work_packages/621"); put("title", "User Management") })
            add(buildJsonObject { put("href", "urn:openproject-org:api:v3:undisclosed"); put("title", "Undisclosed") })
            add(buildJsonObject { put("href", "/api/v3/work_packages/682"); put("title", "User Creation") })
        })))))
        assertEquals(
            listOf(
                HierarchyItem(621, "#621 User Management"),
                HierarchyItem(null, "Undisclosed"),
                HierarchyItem(682, "#682 User Creation"),
                HierarchyItem(withAncestors.id, "#${withAncestors.id} ${withAncestors.subject}"),
            ),
            WorkPackageHierarchy.of(withAncestors),
        )
        val root = OpenProjectApi.decodeWorkPackage(json)
        assertEquals(listOf(HierarchyItem(root.id, "#${root.id} ${root.subject}")), WorkPackageHierarchy.of(root))
        assertEquals("Victor Calvo", root.authorTitle)
    }

    @Test
    fun `the grid uses up to three columns as the width allows`() {
        val layout = ResponsiveGridLayout(maxColumns = 3, minColumnWidth = 200, hgap = 16, vgap = 8)
        assertEquals(1, layout.columnsFor(100))
        assertEquals(1, layout.columnsFor(415))
        assertEquals(2, layout.columnsFor(416))
        assertEquals(3, layout.columnsFor(632))
        assertEquals(3, layout.columnsFor(5000))

        val panel = JPanel(layout)
        repeat(5) { panel.add(JPanel().apply { preferredSize = Dimension(50, 20) }) }
        panel.setSize(632, 1000)
        panel.doLayout()
        // 3 + 2 cells in two rows of equal column widths
        assertEquals(listOf(0, 216, 432, 0, 216), panel.components.map { it.x })
        assertEquals(listOf(0, 0, 0, 28, 28), panel.components.map { it.y })
        assertEquals(200, panel.getComponent(0).width)
        assertEquals(48, layout.preferredLayoutSize(panel).height)
        panel.setSize(300, 1000)
        assertEquals(20 * 5 + 8 * 4, layout.preferredLayoutSize(panel).height)
    }
}
