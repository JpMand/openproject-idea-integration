package com.github.jpmand.openproject.integration.models.abstracts;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.otto.edison.hal.HalRepresentation;

public class OPObject extends HalRepresentation {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("_type")
    private String type;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
