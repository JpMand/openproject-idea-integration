package com.github.jpmand.openproject.integration.models.abstracts;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.jpmand.openproject.integration.models.BasicLinkObject;

public class OPAbstractCollection<T extends BasicLinkObject> extends OPObject {

    public static final String SELF_LINK = "self";
    public static final String CHANGESIZE_LINK = "changeSize";
    public static final String JUMPTO_LINK = "jumpTo";
    public static final String NEXTBYOFFSET_LINK = "nextByOffset";
    public static final String PREVIOUSBYOFFSET_LINK = "previousByOffset";
    public static final String NEXTBYCURSOR_LINK = "nextByCursor";
    public static final String PREVIOUSBYCURSOR_LINK = "previousByCursor";


    @JsonProperty("total")
    private Integer total;

    @JsonProperty("pageSize")
    private Integer pageSize;

    @JsonProperty("count")
    private Integer count;

    @JsonProperty("offset")
    private Integer offset;

    @JsonProperty("groups")
    private OPObject groups;

    @JsonProperty("totalSums")
    private OPObject totalSums;

}
