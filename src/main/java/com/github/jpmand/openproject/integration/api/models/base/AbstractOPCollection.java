package com.github.jpmand.openproject.integration.api.models.base;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

public class AbstractOPCollection<T extends OPBaseResource> extends OPBaseResource {

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

    @JsonProperty("_embedded")
    private Map<String, List<T>> embedded;

    public AbstractOPCollection() {
    }

    public AbstractOPCollection(String type, Map<String, List<OPBaseLink>> links, Integer total, Integer pageSize, Integer count, Integer offset, Map<String, List<T>> embedded) {
        super(type, links);
        this.total = total;
        this.pageSize = pageSize;
        this.count = count;
        this.offset = offset;
        this.embedded = embedded;
    }

    public AbstractOPCollection(List<T> elements) {
        setElements(elements);
    }

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

    public Map<String, List<T>> getEmbedded() {
        return embedded;
    }

    public List<T> getElements() {
        if (embedded == null) {
            return Collections.emptyList();
        }
        return embedded.getOrDefault("elements", Collections.emptyList());
    }

    public void setElements(List<T> elements) {
        if (embedded == null) {
            embedded = new HashMap<>();
        }
        embedded.put("elements", elements);
        this.count = elements.size();
        this.total = elements.size();
        this.pageSize = elements.size();
        this.offset = 0;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof AbstractOPCollection<?> that)) return false;
        if (!super.equals(o)) return false;

        return Objects.equals(getTotal(), that.getTotal()) && Objects.equals(getPageSize(), that.getPageSize()) && Objects.equals(getCount(), that.getCount()) && Objects.equals(getOffset(), that.getOffset()) && Objects.equals(getEmbedded(), that.getEmbedded());
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Objects.hashCode(getTotal());
        result = 31 * result + Objects.hashCode(getPageSize());
        result = 31 * result + Objects.hashCode(getCount());
        result = 31 * result + Objects.hashCode(getOffset());
        result = 31 * result + Objects.hashCode(getEmbedded());
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("AbstractOPCollection{");
        sb.append("total=").append(getTotal());
        sb.append(", pageSize=").append(getPageSize());
        sb.append(", count=").append(getCount());
        sb.append(", offset=").append(getOffset());
        sb.append(", embedded=").append(getEmbedded());
        sb.append(", type='").append(getType()).append('\'');
        sb.append(", links=").append(getLinks());
        sb.append('}');
        return sb.toString();
    }
}
