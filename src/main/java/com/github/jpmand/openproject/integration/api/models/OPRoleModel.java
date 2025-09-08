package com.github.jpmand.openproject.integration.api.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.jpmand.openproject.integration.api.models.base.OPBaseResource;

import java.util.Objects;

public class OPRoleModel extends OPBaseResource {

    @JsonProperty("id")
    private Integer id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("title")
    private String title;

    public OPRoleModel() {
    }

    public OPRoleModel(Integer id, String name) {
        this.id = id;
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof OPRoleModel that)) return false;
        if (!super.equals(o)) return false;

        return Objects.equals(getId(), that.getId()) && Objects.equals(getName(), that.getName()) &&  Objects.equals(getTitle(), that.getTitle());
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + getId().hashCode();
        result = 31 * result + Objects.hashCode(getName());
        result = 31 * result + Objects.hashCode(getTitle());
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("OPRoleModel{");
        sb.append("id=").append(getId());
        sb.append(", name='").append(getName()).append('\'');
        sb.append(", type='").append(getType()).append('\'');
        sb.append(", title='").append(getTitle()).append('\'');
        sb.append(", links=").append(getLinks());
        sb.append('}');
        return sb.toString();
    }
}
