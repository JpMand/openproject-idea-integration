package com.github.jpmand.openproject.idea.ui.detail

import com.github.jpmand.openproject.idea.api.Durations
import com.github.jpmand.openproject.idea.api.model.AttributeGroup
import com.github.jpmand.openproject.idea.api.model.SchemaField
import com.github.jpmand.openproject.idea.api.model.WorkPackage
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** How an attribute of the work package page is edited, decided by its schema type. */
enum class EditorKind {
    /** A single-line text field ("String", "Link", ...). */
    TEXT,

    /** A multi-line text field for "Formattable" values (long text custom fields); sent as `{"raw": ...}`. */
    LONG_TEXT,
    INTEGER,
    FLOAT,

    /** Hours and minutes ("Duration": work, remaining work), entered like "1h 30m" or "1.5". */
    DURATION,

    /** The `duration` attribute: a "Duration" counted in whole days. */
    DAYS,
    DATE,
    DATE_TIME,
    BOOLEAN,

    /** A dropdown of the allowed values of a linked resource (user, version, category, custom option, ...). */
    LINK,

    /** Several allowed values of a linked resource ("[]User", "[]CustomOption", ...). */
    LINKS,

    /** A work package (the parent), searched by id or subject. */
    WORK_PACKAGE,
}

/** The attributes the properties section shows, their editors and the conversion of entered values for the API. */
object PropertyEditing {

    /** Shown in the page header or edited elsewhere, never in the properties section. */
    private val HEADER = setOf("id", "subject", "description", "lockVersion", "attachments", "type", "status", "author", "updatedAt")

    /** Without attribute groups from the server: the attributes shown, as before groups were supported. */
    private val FALLBACK = listOf(
        "priority", "assignee", "responsible", "project", "version", "category", "parent",
        "startDate", "dueDate", "date", "duration", "estimatedTime", "remainingTime", "spentTime", "percentageDone",
        "storyPoints", "createdAt",
    )

    /** OpenProject's form groups list "date" for the start and finish dates of work packages that are no milestones. */
    private val DATE_RANGE = listOf("startDate", "dueDate")

    /**
     * The groups of the properties section: the server's attribute groups with the attributes the schema knows
     * (empty ones included, so they can be filled in). Without groups, one unnamed group with the usual attributes.
     */
    fun groups(details: WorkPackageDetails): List<AttributeGroup> =
        groups(details.attributeGroups, details.schema?.fields?.keys.orEmpty() + details.formSchema?.fields?.keys.orEmpty())

    /** The groups of the properties section from the server's [groups] and the attributes the schemas [known]. */
    fun groups(groups: List<AttributeGroup>, known: Set<String>): List<AttributeGroup> {
        if (groups.isEmpty()) {
            val attributes = if (known.isEmpty()) {
                FALLBACK
            } else {
                (FALLBACK + known.filter { it.startsWith("customField") }).filter { it in known }
            }
            return listOf(AttributeGroup("", attributes.filter { it !in HEADER }.distinct()))
        }
        return groups.map { group ->
            val attributes = group.attributes
                .flatMap { attribute -> if (attribute == "date" && attribute !in known) DATE_RANGE else listOf(attribute) }
                .filter { it in known && it !in HEADER }
                .distinct()
            AttributeGroup(group.name, attributes)
        }.filter { it.attributes.isNotEmpty() }
    }

    /** The editor for [field], or null when values of its type cannot be entered here. */
    fun kindOf(field: SchemaField): EditorKind? {
        if (field.elementType == "WorkPackage") return if (field.isMultiValue) null else EditorKind.WORK_PACKAGE
        if (field.isLink) return if (field.isMultiValue) EditorKind.LINKS else EditorKind.LINK
        if (field.isMultiValue) return null
        return when (field.type) {
            "Boolean" -> EditorKind.BOOLEAN
            "Date" -> EditorKind.DATE
            "DateTime" -> EditorKind.DATE_TIME
            "Duration" -> if (field.attribute == "duration") EditorKind.DAYS else EditorKind.DURATION
            "Integer" -> EditorKind.INTEGER
            "Float" -> EditorKind.FLOAT
            "Formattable" -> EditorKind.LONG_TEXT
            "String", "Link", "Text" -> EditorKind.TEXT
            else -> null
        }
    }

    /** The text a text-like editor starts with: the current value as it is entered. */
    fun editText(workPackage: WorkPackage, attribute: String, kind: EditorKind): String {
        val value = workPackage.raw[attribute]
        val primitive = (value as? JsonPrimitive)?.takeUnless { it is JsonNull }
        return when (kind) {
            EditorKind.LONG_TEXT -> ((value as? JsonObject)?.get("raw") as? JsonPrimitive)?.contentOrNull.orEmpty()
            EditorKind.DURATION -> Durations.formatHuman(Durations.parseIso(primitive?.contentOrNull))
            EditorKind.DAYS -> Durations.parseIso(primitive?.contentOrNull)?.toDays()?.toString().orEmpty()
            else -> primitive?.contentOrNull.orEmpty()
        }
    }

    /** The current value of a boolean attribute. */
    fun booleanValue(workPackage: WorkPackage, attribute: String): Boolean =
        (workPackage.raw[attribute] as? JsonPrimitive)?.booleanOrNull ?: false

    /** The current date of a "Date" attribute, null when unset. */
    fun dateValue(workPackage: WorkPackage, attribute: String): LocalDate? =
        (workPackage.raw[attribute] as? JsonPrimitive)?.contentOrNull?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** The current moment of a "DateTime" attribute in [zone], null when unset. */
    fun dateTimeValue(workPackage: WorkPackage, attribute: String, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? =
        (workPackage.raw[attribute] as? JsonPrimitive)?.contentOrNull
            ?.let { runCatching { Instant.parse(it).atZone(zone).toLocalDateTime() }.getOrNull() }

    /**
     * The API value of entered [text]: `null` when it is invalid, [JsonNull] when it is empty (unsets the
     * attribute; invalid when [required]).
     */
    fun parse(kind: EditorKind, text: String, required: Boolean): JsonElement? {
        val trimmed = text.trim()
        if (kind == EditorKind.LONG_TEXT) {
            return if (required && trimmed.isEmpty()) null else buildJsonObject { put("raw", text) }
        }
        if (trimmed.isEmpty()) return if (required) null else JsonNull
        return when (kind) {
            EditorKind.TEXT -> JsonPrimitive(trimmed)
            EditorKind.INTEGER -> trimmed.toLongOrNull()?.let(::JsonPrimitive)
            EditorKind.FLOAT -> trimmed.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }?.let(::JsonPrimitive)
            EditorKind.DURATION -> Durations.parseHuman(trimmed)?.takeIf { !it.isNegative }?.let { JsonPrimitive(Durations.toIso(it)) }
            EditorKind.DAYS -> trimmed.toIntOrNull()?.takeIf { it >= 0 }?.let { JsonPrimitive("P${it}D") }
            EditorKind.DATE -> runCatching { LocalDate.parse(trimmed) }.getOrNull()?.let { JsonPrimitive(it.toString()) }
            else -> null
        }
    }

    /** API value of a date ("2026-11-16"), [JsonNull] to unset. */
    fun date(value: LocalDate?): JsonElement = value?.let { JsonPrimitive(it.toString()) } ?: JsonNull

    /** API value of a moment entered in [zone] (an ISO instant in UTC), [JsonNull] to unset. */
    fun dateTime(value: LocalDateTime?, zone: ZoneId = ZoneId.systemDefault()): JsonElement =
        value?.let { JsonPrimitive(it.atZone(zone).toInstant().toString()) } ?: JsonNull
}

/** One entry of the hierarchy above the subject: an ancestor or the work package itself. */
data class HierarchyItem(
    /** Null for ancestors the user may not see. */
    val workPackageId: Long?,
    val text: String,
)

object WorkPackageHierarchy {
    /** "#621 User Management", "#682 User Creation", ..., ending with the work package itself. */
    fun of(workPackage: WorkPackage): List<HierarchyItem> =
        workPackage.ancestors.map { link ->
            val id = link.id?.toLongOrNull()
            HierarchyItem(id, listOfNotNull(id?.let { "#$it" }, link.title).joinToString(" "))
        } + HierarchyItem(workPackage.id, "#${workPackage.id} ${workPackage.subject}")
}
