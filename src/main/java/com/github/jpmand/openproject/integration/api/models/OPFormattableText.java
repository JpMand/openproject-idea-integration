package com.github.jpmand.openproject.integration.api.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OPFormattableText {

    @JsonProperty("format")
    private String format;

    @JsonProperty("raw")
    private String raw;

    @JsonProperty("html")
    private String html;

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getRaw() {
        return raw;
    }

    public void setRaw(String raw) {
        this.raw = raw;
    }

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }
}
