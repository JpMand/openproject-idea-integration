package com.github.jpmand.openproject.integration.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.jpmand.openproject.integration.models.abstracts.OPObject;

public class OPError extends OPObject {

    @JsonProperty("errorIdentifier")
    private String errorIdentifier;

    @JsonProperty("message")
    private String message;
}
