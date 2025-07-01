package com.github.jpmand.openproject.integration.models.filters;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OPFilterObject {

    @JsonAnySetter
    @JsonAnyGetter
    private Map<String, OPFilterValue> filterValues = new LinkedHashMap<>();


    public Map<String, OPFilterValue> getFilterValues() {
        return filterValues;
    }

    public void setFilterValues(Map<String, OPFilterValue> filterValues) {
        this.filterValues = filterValues;
    }

    public static OPFilterObject of(Map<String, OPFilterValue> filterValues) {
        OPFilterObject filterObject = new OPFilterObject();
        filterObject.setFilterValues(filterValues);
        return filterObject;
    }

    public static OPFilterObject of(String key, OPFilterValue filterValue) {
        Map<String, OPFilterValue> filterValues = new LinkedHashMap<>();
        filterValues.put(key, filterValue);
        return of(filterValues);
    }

    public static Map<String, OPFilterValue> from(OPFilterObject filterObject) {
        return filterObject.getFilterValues();
    }

    public static Map<String, OPFilterValue> from(String key, OPFilterValue filterValue) {
        return Map.of(key, filterValue);
    }

}
