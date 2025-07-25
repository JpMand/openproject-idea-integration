package com.github.jpmand.openproject.integration.api.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum FilterOperator {

    /**
     * are equal to one of the given value(s)
     * @apiNote At least one typed value
     */
    @JsonProperty("=")
    EQUALS("="),
    /**
     * are containing all of the given value(s)
     * @apiNote At least one typed value
     */
    @JsonProperty("&=")
    CONTAINS("&="),
    /**
     * are not equal one of the given value(s)
     * @apiNote At least one typed value
     */
    @JsonProperty("!")
    NOT_EQUALS("!"),
    @JsonProperty(">=")
    GREATER_OR_EQUALS(">="),
    @JsonProperty("t-")
    PAST_N_DAYS("t-"),
    @JsonProperty("t+")
    FUTURE_N_DAYS("t+"),
    @JsonProperty("<t+")
    FUTURE_LESS_THAN_N_DAYS("<t+"),
    @JsonProperty(">t+")
    FUTURE_MORE_THAN_N_DAYS(">t+"),
    @JsonProperty("<t-")
    PAST_LESS_THAN_N_DAYS("<t-"),
    @JsonProperty(">t-")
    PAST_MORE_THAN_N_DAYS(">t-"),
    @JsonProperty("*")
    NOT_NULL("*"),
    @JsonProperty("!*")
    ARE_NULL("!*"),
    @JsonProperty("**")
    SEARCH_BY_STRING("**"),
    @JsonProperty("=d")
    ON_DATE("=d"),
    @JsonProperty("<>d")
    BETWEEN_DATES("<>d"),
    @JsonProperty("w")
    THIS_WEEK("w"),
    @JsonProperty("t")
    TODAY("t"),
    @JsonProperty("~")
    COTAINING_SQL_LIKE("~"),
    @JsonProperty("!~")
    NOT_CONTAINING_SQL_LIKE("!~"),
    @JsonProperty("o")
    WK_OPEN("o"),
    @JsonProperty("c")
    WK_CLOSED("c"),
    @JsonProperty("ow")
    WK_MANUAL_ORDER("ow");

    private final String value;

    private FilterOperator(String value){
        this.value = value;
    }
}
