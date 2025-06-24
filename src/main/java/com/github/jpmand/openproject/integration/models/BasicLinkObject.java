package com.github.jpmand.openproject.integration.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.jpmand.openproject.integration.models.abstracts.OPObject;

public class BasicLinkObject extends OPObject {

    @JsonProperty("href")
    private String href;

    @JsonProperty("title")
    private String title;

    @JsonProperty("templated")
    private Boolean templated;

    @JsonProperty("method")
    private String method;

    @JsonProperty("payload")
    private String payload;

    @JsonProperty("identifier")
    private String identifier;

}
