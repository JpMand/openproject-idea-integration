package com.github.jpmand.openproject.integration.api.models.base;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OPBaseResource {

    public static final String SELF_LINK = "self";

    @JsonProperty("_type")
    private String type;

    @JsonProperty("_links")
    private Map<String, List<OPBaseLink>> links;

    @JsonProperty("_embedded")
    private Map<String, List<OPBaseResource>> embedded;

    @JsonAnySetter
    @JsonAnyGetter
    private Map<String, Object> properties;

    public OPBaseResource() {
    }

    public OPBaseResource(String type, Map<String, List<OPBaseLink>> links) {
        this.type = type;
        this.links = links;
    }

    public String getType() {
        return type;
    }

    public void setType(String _type) {
        this.type = _type;
    }

    public Map<String, List<OPBaseLink>> getLinks() {
        return links;
    }

    public void setLinks(Map<String, List<OPBaseLink>> _links) {
        this.links = _links;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public List<OPBaseLink> getLink(String rel) {
        return this.links.getOrDefault(rel, null);
    }

    public OPBaseLink getSingleLink(String rel) {
        return this.getLink(rel) == null ? null : this.getLink(rel).getFirst();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof OPBaseResource that)) return false;

        return Objects.equals(getType(), that.getType()) && Objects.equals(getLinks(), that.getLinks());
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(getType());
        result = 31 * result + Objects.hashCode(getLinks());
        return result;
    }


    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("OPBaseResource{");
        sb.append("type='").append(getType()).append('\'');
        sb.append(", links=").append(getLinks());
        sb.append('}');
        return sb.toString();
    }
}
