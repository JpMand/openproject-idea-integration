package com.github.jpmand.openproject.integration.api.models.base;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OPBaseLink {
    private static final String UNDISCLOSED_LINK = "urn:openproject-org:api:v3:undisclosed";

    private String href;
    private String title;
    private Boolean templated;
    private String method;
    private String identifier;

    public OPBaseLink() {
    }

    public OPBaseLink(String href, String title, Boolean templated, String method, String identifier) {
        this.href = href;
        this.title = title;
        this.templated = templated;
        this.method = method;
        this.identifier = identifier;
    }

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Boolean getTemplated() {
        return templated;
    }

    public void setTemplated(Boolean templated) {
        this.templated = templated;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public boolean isUndisclosed() {
        return Objects.equals(getHref(), UNDISCLOSED_LINK);
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof OPBaseLink that)) return false;

        return Objects.equals(getHref(), that.getHref()) && Objects.equals(getTitle(), that.getTitle()) && Objects.equals(getTemplated(), that.getTemplated()) && Objects.equals(getMethod(), that.getMethod()) && Objects.equals(getIdentifier(), that.getIdentifier());
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(getHref());
        result = 31 * result + Objects.hashCode(getTitle());
        result = 31 * result + Objects.hashCode(getTemplated());
        result = 31 * result + Objects.hashCode(getMethod());
        result = 31 * result + Objects.hashCode(getIdentifier());
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("OPBaseLink{");
        sb.append("href='").append(href).append('\'');
        sb.append(", title='").append(title).append('\'');
        sb.append(", templated=").append(templated);
        sb.append(", method='").append(method).append('\'');
        sb.append(", identifier='").append(identifier).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
