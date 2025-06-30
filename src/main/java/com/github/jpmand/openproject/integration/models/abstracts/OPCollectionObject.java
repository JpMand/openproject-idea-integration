package com.github.jpmand.openproject.integration.models.abstracts;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OPCollectionObject extends OPObject {

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

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }
}
