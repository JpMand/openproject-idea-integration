package com.github.jpmand.openproject.integration.models.filters;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.jpmand.openproject.integration.models.enums.FilterOperator;

import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OPFilterValue {

    @JsonProperty("operator")
    private FilterOperator operator;

    @JsonProperty("values")
    private List<Object> values;

    public FilterOperator getOperator() {
        return operator;
    }

    public void setOperator(FilterOperator operator) {
        this.operator = operator;
    }

    public List<Object> getValues() {
        return values;
    }

    public void setValues(List<Object> values) {
        this.values = values;
    }

    public static OPFilterValue of(FilterOperator operator, List<Object> values) {
        OPFilterValue filterValue = new OPFilterValue();
        filterValue.setOperator(operator);
        filterValue.setValues(values);
        return filterValue;
    }

    public static OPFilterValue of(FilterOperator operator, Object value) {
        return of(operator, List.of(value));
    }

    public static OPFilterValue of(FilterOperator operator) {
        return of(operator, Collections.emptyList());
    }
}
