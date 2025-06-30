package com.github.jpmand.openproject.integration.models.wp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.jpmand.openproject.integration.models.OPFormattableText;
import com.github.jpmand.openproject.integration.models.abstracts.OPObject;

import java.time.OffsetDateTime;

public class OPWorkPackage extends OPObject {

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

    public static final String SELF_LINK = "self";
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

    @JsonProperty("subject")
    private String subject;

    @JsonProperty("description")
    private OPFormattableText description;

    @JsonProperty("scheduleManually")
    private Boolean scheduleManually;

    @JsonProperty("readOnly")
    private Boolean readOnly;

    @JsonProperty("startDate")
    private OffsetDateTime startDate;

    @JsonProperty("dueDate")
    private OffsetDateTime dueDate;

    @JsonProperty("derivedStartDate")
    private OffsetDateTime derivedStartDate;

    @JsonProperty("derivedDueDate")
    private OffsetDateTime derivedDueDate;

    @JsonProperty("duration")
    private String duration;

    @JsonProperty("estimatedTime")
    private String estimatedTime;

    @JsonProperty("derivedEstimatedTime")
    private String derivedEstimatedTime;

    @JsonProperty("ignoreNonWorkingDays")
    private Boolean ignoreNonWorkingDays;

    @JsonProperty("spentTime")
    private String spentTime;

    @JsonProperty("percentageDone")
    private Integer percentageDone;

    @JsonProperty("derivedPercentageDone")
    private Integer derivedPercentageDone;

    @JsonProperty("createdAt")
    private OffsetDateTime createdAt;

    @JsonProperty("updatedAt")
    private OffsetDateTime updatedAt;

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

    public OffsetDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(OffsetDateTime startDate) {
        this.startDate = startDate;
    }

    public OffsetDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(OffsetDateTime dueDate) {
        this.dueDate = dueDate;
    }

    public OffsetDateTime getDerivedStartDate() {
        return derivedStartDate;
    }

    public void setDerivedStartDate(OffsetDateTime derivedStartDate) {
        this.derivedStartDate = derivedStartDate;
    }

    public OffsetDateTime getDerivedDueDate() {
        return derivedDueDate;
    }

    public void setDerivedDueDate(OffsetDateTime derivedDueDate) {
        this.derivedDueDate = derivedDueDate;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getEstimatedTime() {
        return estimatedTime;
    }

    public void setEstimatedTime(String estimatedTime) {
        this.estimatedTime = estimatedTime;
    }

    public String getDerivedEstimatedTime() {
        return derivedEstimatedTime;
    }

    public void setDerivedEstimatedTime(String derivedEstimatedTime) {
        this.derivedEstimatedTime = derivedEstimatedTime;
    }

    public Boolean getIgnoreNonWorkingDays() {
        return ignoreNonWorkingDays;
    }

    public void setIgnoreNonWorkingDays(Boolean ignoreNonWorkingDays) {
        this.ignoreNonWorkingDays = ignoreNonWorkingDays;
    }

    public String getSpentTime() {
        return spentTime;
    }

    public void setSpentTime(String spentTime) {
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
}
