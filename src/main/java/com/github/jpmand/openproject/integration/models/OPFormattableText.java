package com.github.jpmand.openproject.integration.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OPFormattableText {

    @JsonProperty("format")
    private String format;

    @JsonProperty("raw")
    private String raw;

    @JsonProperty("html")
    private String html;
}
