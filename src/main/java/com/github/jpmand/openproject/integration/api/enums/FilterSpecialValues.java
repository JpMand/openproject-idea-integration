package com.github.jpmand.openproject.integration.api.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum FilterSpecialValues {
    @JsonProperty("t")
    TRUE("t"),

    @JsonProperty("f")
    FALSE("f");

    private final String value;

    private FilterSpecialValues(String value) {
        this.value = value;
    }
}
