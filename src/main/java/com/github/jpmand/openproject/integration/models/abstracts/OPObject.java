package com.github.jpmand.openproject.integration.models.abstracts;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.otto.edison.hal.HalRepresentation;

public class OPObject extends HalRepresentation {
    @JsonProperty("_type")
    private String type;
}
