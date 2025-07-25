package com.github.jpmand.openproject.integration.api.models;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.jpmand.openproject.integration.api.models.base.OPBaseLink;
import com.github.jpmand.openproject.integration.api.models.base.OPBaseResource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OPError extends OPBaseResource {

    @JsonProperty("errorIdentifier")
    private String errorIdentifier;

    @JsonProperty("message")
    private String message;

    private Map<String, Object> parameters = new HashMap<String, Object>();

    public OPError() {
    }

    public OPError(String errorIdentifier, String message, Map<String, Object> parameters) {
        this.errorIdentifier = errorIdentifier;
        this.message = message;
        this.parameters = parameters;
    }

    public OPError(String type, Map<String, List<OPBaseLink>> links, String errorIdentifier, String message, Map<String, Object> parameters) {
        super(type, links);
        this.errorIdentifier = errorIdentifier;
        this.message = message;
        this.parameters = parameters;
    }

    public String getErrorIdentifier() {
        return errorIdentifier;
    }

    public void setErrorIdentifier(String errorIdentifier) {
        this.errorIdentifier = errorIdentifier;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @JsonAnyGetter
    public Map<String, Object> getParameters() {
        return parameters;
    }

    @JsonAnySetter
    public void setParameters(String key, Object value) {
        this.parameters.put(key, value);
    }
}
