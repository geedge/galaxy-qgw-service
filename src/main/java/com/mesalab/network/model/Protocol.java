package com.mesalab.network.model;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.network.common.Constants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Protocol implements Serializable {
    private String id;
    private String name;
    private String parentId;
    private List<Protocol> childrens = Lists.newArrayList();
    private Map<String, Object> metrics = Maps.newLinkedHashMap();
    private long sentBytes;
    private long receivedBytes;
    private long totalSentBytes;
    private long totalReceivedBytes;


    public Protocol(String id, String  name, String parentId) {
        this.id = id;
        this.name = name;
        this.parentId = parentId;
    }

    public Protocol(String id, String  name, String parentId, long sentBytes, long receivedBytes) {
        this.id = id;
        this.name = name;
        this.parentId = parentId;
        this.sentBytes = sentBytes;
        this.receivedBytes = receivedBytes;
    }

    public void addMetric(String key, Object value) {
        metrics.put(key, value);
    }


    public String getParentId() {
        return id.lastIndexOf(Constants.ENCAPSULATION_PATH_SEPARATOR) > 0  ?
                id.substring(0, id.lastIndexOf(Constants.ENCAPSULATION_PATH_SEPARATOR)) : null;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Protocol that = (Protocol) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }


}
