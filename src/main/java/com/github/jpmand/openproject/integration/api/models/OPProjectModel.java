package com.github.jpmand.openproject.integration.api.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.jpmand.openproject.integration.api.models.base.OPBaseResource;

import java.time.OffsetDateTime;
import java.util.Objects;

public class OPProjectModel extends OPBaseResource {

    public static final String UPDATE_ACTION = "update";
    public static final String UPDATEIMMEDIATELY_ACTION = "updateImmediately";
    public static final String DELETE_ACTION = "delete";
    public static final String CREATEWORKPACKAGE_ACTION = "createWorkPackage";
    public static final String CREATEWORKPACKAGEIMMEDIATELY_ACTION = "createWorkPackageImmediately";

    private static final String ANCESTORS_LINK = "ancestors";
    private static final String CATEGORIES_LINK = "categories";
    private static final String TYPES_LINK = "types";
    private static final String VERSIONS_LINK = "versions";
    private static final String MEMBERSHIPS_LINK = "memberships";
    private static final String WORKPACKAGES_LINK = "workPackages";
    private static final String PARENT_LINK = "parent";
    private static final String STATUS_LINK = "status";

    @JsonProperty("id")
    private Integer id;

    @JsonProperty("identifier")
    private String identifier;

    @JsonProperty("name")
    private String name;

    @JsonProperty("active")
    private Boolean active;

    @JsonProperty("statusExplanation")
    private OPFormattableText statusExplanation;

    @JsonProperty("public")
    private Boolean _public;

    @JsonProperty("description")
    private OPFormattableText description;

    @JsonProperty("createdAt")
    private OffsetDateTime createdAt;

    @JsonProperty("updatedAt")
    private OffsetDateTime updatedAt;

    public OPProjectModel() {
    }

    public OPProjectModel(Integer id, String identifier, String name, Boolean active, OPFormattableText statusExplanation, Boolean _public, OPFormattableText description, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        this.id = id;
        this.identifier = identifier;
        this.name = name;
        this.active = active;
        this.statusExplanation = statusExplanation;
        this._public = _public;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public OPFormattableText getStatusExplanation() {
        return statusExplanation;
    }

    public void setStatusExplanation(OPFormattableText statusExplanation) {
        this.statusExplanation = statusExplanation;
    }

    public Boolean getPublic() {
        return _public;
    }

    public void setPublic(Boolean _public) {
        this._public = _public;
    }

    public OPFormattableText getDescription() {
        return description;
    }

    public void setDescription(OPFormattableText description) {
        this.description = description;
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
        if (!(o instanceof OPProjectModel that)) return false;
        if (!super.equals(o)) return false;

        return Objects.equals(getId(), that.getId()) && Objects.equals(getIdentifier(), that.getIdentifier());
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Objects.hashCode(getId());
        result = 31 * result + Objects.hashCode(getIdentifier());
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("OPProjectModel{");
        sb.append("id=").append(getId());
        sb.append(", identifier='").append(getIdentifier()).append('\'');
        sb.append(", name='").append(getName()).append('\'');
        sb.append(", active=").append(getActive());
        sb.append(", statusExplanation=").append(getStatusExplanation());
        sb.append(", public=").append(getPublic());
        sb.append(", description=").append(getDescription());
        sb.append(", createdAt=").append(getCreatedAt());
        sb.append(", updatedAt=").append(getUpdatedAt());
        sb.append(", public=").append(getPublic());
        sb.append(", type='").append(getType()).append('\'');
        sb.append(", links=").append(getLinks());
        sb.append('}');
        return sb.toString();
    }
}
