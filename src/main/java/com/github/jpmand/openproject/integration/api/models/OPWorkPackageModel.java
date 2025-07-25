package com.github.jpmand.openproject.integration.api.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.jpmand.openproject.integration.api.models.base.OPBaseResource;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

public class OPWorkPackageModel extends OPBaseResource {

    public static final String ADDATTACHMENT_ACTION = "addAttachment";
    public static final String ADDCOMMENT_ACTION = "addComment";
    public static final String ADDRELATION_ACTION = "addRelation";
    public static final String ADDWATCHER_ACTION = "addWatcher";
    public static final String CUSTOMACTIONS_ACTION = "customActions";
    public static final String LOGTIME_ACTION = "logTime";
    public static final String PREVIEWMARKUP_ACTION = "previewMarkup";
    public static final String REMOVEWATCHER_ACTION = "removeWatcher";
    public static final String UNWATCH_ACTION = "unwatch";
    public static final String UPDATE_ACTION = "update";
    public static final String UPDATEIMMEDIATELY_ACTION = "updateImmediately";
    public static final String WATCH_ACTION = "watch";
    public static final String DELETE_ACTION = "delete";

    public static final String SCHEMA_LINK = "schema";
    public static final String ANCESTORS_LINK = "ancestors";
    public static final String ATTACHMENTS_LINK = "attachments";
    public static final String AUTHOR_LINK = "author";
    public static final String ASSIGNEE_LINK = "assignee";
    public static final String AVAILABLEWATCHERS_LINK = "availableWatchers";
    public static final String BUDGET_LINK = "budget";
    public static final String CATEGORY_LINK = "category";
    public static final String CHILDREN_LINK = "children";
    public static final String PARENT_LINK = "parent";
    public static final String PRIORITY_LINK = "priority";
    public static final String PROJECT_LINK = "project";
    public static final String PROJECTPHASE_LINK = "projectPhase";
    public static final String PROJECTPHASEDEFINITION_LINK = "projectPhaseDefinition";
    public static final String RESPONSIBLE_LINK = "responsible";
    public static final String RELATIONS_LINK = "relations";
    public static final String REVISIONS_LINK = "revisions";
    public static final String STATUS_LINK = "status";
    public static final String TIMEENTRIES_LINK = "timeEntries";
    public static final String TYPE_LINK = "type";
    public static final String VERSION_LINK = "version";
    public static final String WATCHERS_LINK = "watchers";

    @JsonProperty("id")
    private Integer id;

    @JsonProperty("subject")
    private String subject;

    @JsonProperty("description")
    private OPFormattableText description;

    @JsonProperty("scheduleManually")
    private Boolean scheduleManually;

    @JsonProperty("readOnly")
    private Boolean readOnly;

    @JsonProperty("startDate")
    private LocalDate startDate;

    @JsonProperty("dueDate")
    private LocalDate dueDate;

    @JsonProperty("derivedStartDate")
    private LocalDate derivedStartDate;

    @JsonProperty("derivedDueDate")
    private LocalDate derivedDueDate;

    @JsonProperty("duration")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Duration duration;

    @JsonProperty("estimatedTime")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Duration estimatedTime;

    @JsonProperty("derivedEstimatedTime")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Duration derivedEstimatedTime;

    @JsonProperty("ignoreNonWorkingDays")
    private Boolean ignoreNonWorkingDays;

    @JsonProperty("spentTime")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Duration spentTime;

    @JsonProperty("percentageDone")
    private Integer percentageDone;

    @JsonProperty("derivedPercentageDone")
    private Integer derivedPercentageDone;

    @JsonProperty("createdAt")
    private OffsetDateTime createdAt;

    @JsonProperty("updatedAt")
    private OffsetDateTime updatedAt;

    public OPWorkPackageModel() {
    }

    public OPWorkPackageModel(Integer id, String subject, OPFormattableText description, Boolean scheduleManually, Boolean readOnly, LocalDate startDate, LocalDate dueDate, LocalDate derivedStartDate, LocalDate derivedDueDate, Duration duration, Duration estimatedTime, Duration derivedEstimatedTime, Boolean ignoreNonWorkingDays, Duration spentTime, Integer percentageDone, Integer derivedPercentageDone, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.subject = subject;
        this.description = description;
        this.scheduleManually = scheduleManually;
        this.readOnly = readOnly;
        this.startDate = startDate;
        this.dueDate = dueDate;
        this.derivedStartDate = derivedStartDate;
        this.derivedDueDate = derivedDueDate;
        this.duration = duration;
        this.estimatedTime = estimatedTime;
        this.derivedEstimatedTime = derivedEstimatedTime;
        this.ignoreNonWorkingDays = ignoreNonWorkingDays;
        this.spentTime = spentTime;
        this.percentageDone = percentageDone;
        this.derivedPercentageDone = derivedPercentageDone;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public OPFormattableText getDescription() {
        return description;
    }

    public void setDescription(OPFormattableText description) {
        this.description = description;
    }

    public Boolean getScheduleManually() {
        return scheduleManually;
    }

    public void setScheduleManually(Boolean scheduleManually) {
        this.scheduleManually = scheduleManually;
    }

    public Boolean getReadOnly() {
        return readOnly;
    }

    public void setReadOnly(Boolean readOnly) {
        this.readOnly = readOnly;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDate getDerivedStartDate() {
        return derivedStartDate;
    }

    public void setDerivedStartDate(LocalDate derivedStartDate) {
        this.derivedStartDate = derivedStartDate;
    }

    public LocalDate getDerivedDueDate() {
        return derivedDueDate;
    }

    public void setDerivedDueDate(LocalDate derivedDueDate) {
        this.derivedDueDate = derivedDueDate;
    }

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    public Duration getEstimatedTime() {
        return estimatedTime;
    }

    public void setEstimatedTime(Duration estimatedTime) {
        this.estimatedTime = estimatedTime;
    }

    public Duration getDerivedEstimatedTime() {
        return derivedEstimatedTime;
    }

    public void setDerivedEstimatedTime(Duration derivedEstimatedTime) {
        this.derivedEstimatedTime = derivedEstimatedTime;
    }

    public Boolean getIgnoreNonWorkingDays() {
        return ignoreNonWorkingDays;
    }

    public void setIgnoreNonWorkingDays(Boolean ignoreNonWorkingDays) {
        this.ignoreNonWorkingDays = ignoreNonWorkingDays;
    }

    public Duration getSpentTime() {
        return spentTime;
    }

    public void setSpentTime(Duration spentTime) {
        this.spentTime = spentTime;
    }

    public Integer getPercentageDone() {
        return percentageDone;
    }

    public void setPercentageDone(Integer percentageDone) {
        this.percentageDone = percentageDone;
    }

    public Integer getDerivedPercentageDone() {
        return derivedPercentageDone;
    }

    public void setDerivedPercentageDone(Integer derivedPercentageDone) {
        this.derivedPercentageDone = derivedPercentageDone;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof OPWorkPackageModel that)) return false;
        if (!super.equals(o)) return false;

        return Objects.equals(getSubject(), that.getSubject()) && Objects.equals(getDescription(), that.getDescription()) && Objects.equals(getScheduleManually(), that.getScheduleManually()) && Objects.equals(getReadOnly(), that.getReadOnly()) && Objects.equals(getStartDate(), that.getStartDate()) && Objects.equals(getDueDate(), that.getDueDate()) && Objects.equals(getDerivedStartDate(), that.getDerivedStartDate()) && Objects.equals(getDerivedDueDate(), that.getDerivedDueDate()) && Objects.equals(getDuration(), that.getDuration()) && Objects.equals(getEstimatedTime(), that.getEstimatedTime()) && Objects.equals(getDerivedEstimatedTime(), that.getDerivedEstimatedTime()) && Objects.equals(getIgnoreNonWorkingDays(), that.getIgnoreNonWorkingDays()) && Objects.equals(getSpentTime(), that.getSpentTime()) && Objects.equals(getPercentageDone(), that.getPercentageDone()) && Objects.equals(getDerivedPercentageDone(), that.getDerivedPercentageDone()) && Objects.equals(getCreatedAt(), that.getCreatedAt()) && Objects.equals(getUpdatedAt(), that.getUpdatedAt());
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Objects.hashCode(getSubject());
        result = 31 * result + Objects.hashCode(getDescription());
        result = 31 * result + Objects.hashCode(getScheduleManually());
        result = 31 * result + Objects.hashCode(getReadOnly());
        result = 31 * result + Objects.hashCode(getStartDate());
        result = 31 * result + Objects.hashCode(getDueDate());
        result = 31 * result + Objects.hashCode(getDerivedStartDate());
        result = 31 * result + Objects.hashCode(getDerivedDueDate());
        result = 31 * result + Objects.hashCode(getDuration());
        result = 31 * result + Objects.hashCode(getEstimatedTime());
        result = 31 * result + Objects.hashCode(getDerivedEstimatedTime());
        result = 31 * result + Objects.hashCode(getIgnoreNonWorkingDays());
        result = 31 * result + Objects.hashCode(getSpentTime());
        result = 31 * result + Objects.hashCode(getPercentageDone());
        result = 31 * result + Objects.hashCode(getDerivedPercentageDone());
        result = 31 * result + Objects.hashCode(getCreatedAt());
        result = 31 * result + Objects.hashCode(getUpdatedAt());
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("OPWorkPackageModel{");
        sb.append("id='").append(getId());
        sb.append(", subject='").append(getSubject()).append('\'');
        sb.append(", description=").append(getDescription());
        sb.append(", scheduleManually=").append(getScheduleManually());
        sb.append(", readOnly=").append(getReadOnly());
        sb.append(", startDate=").append(getStartDate());
        sb.append(", dueDate=").append(getDueDate());
        sb.append(", derivedStartDate=").append(getDerivedStartDate());
        sb.append(", derivedDueDate=").append(getDerivedDueDate());
        sb.append(", duration='").append(getDuration()).append('\'');
        sb.append(", estimatedTime='").append(getEstimatedTime()).append('\'');
        sb.append(", derivedEstimatedTime='").append(getDerivedEstimatedTime()).append('\'');
        sb.append(", ignoreNonWorkingDays=").append(getIgnoreNonWorkingDays());
        sb.append(", spentTime='").append(getSpentTime()).append('\'');
        sb.append(", percentageDone=").append(getPercentageDone());
        sb.append(", derivedPercentageDone=").append(getDerivedPercentageDone());
        sb.append(", createdAt=").append(getCreatedAt());
        sb.append(", updatedAt=").append(getUpdatedAt());
        sb.append(", type='").append(getType()).append('\'');
        sb.append(", links=").append(getLinks());
        sb.append('}');
        return sb.toString();
    }
}
