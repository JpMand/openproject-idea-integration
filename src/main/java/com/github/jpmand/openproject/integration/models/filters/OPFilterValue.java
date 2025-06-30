package com.github.jpmand.openproject.integration.models.filters;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.jpmand.openproject.integration.models.enums.FilterOperator;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OPFilterValue {

    @JsonProperty("operator")
    private FilterOperator operator;

    @JsonProperty("values")
    private String[] values;

    public FilterOperator getOperator() {
        return operator;
    }

    public void setOperator(FilterOperator operator) {
        this.operator = operator;
    }
}
