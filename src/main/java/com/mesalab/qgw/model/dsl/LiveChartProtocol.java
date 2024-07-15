package com.mesalab.qgw.model.dsl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.qgw.constant.dsl.LiveChartConstants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author wangwei
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"id", "name", "parentId", "childrens", "metrics", "sentBytes", "receivedBytes", "totalSentBytes", "totalReceivedBytes"})
public class LiveChartProtocol implements Serializable {
    private String id;
    private String name;
    @JsonProperty("parent_id")
    private String parentId;
    private List<LiveChartProtocol> childrens = Lists.newArrayList();
    private Map<String, Object> metrics = Maps.newLinkedHashMap();
    @JsonProperty("sent_bytes")
    private long sentBytes;
    @JsonProperty("received_bytes")
    private long receivedBytes;
    @JsonProperty("total_sent_bytes")
    private long totalSentBytes;
    @JsonProperty("total_received_bytes")
    private long totalReceivedBytes;


    public LiveChartProtocol(String id, String  name, String parentId) {
        this.id = id;
        this.name = name;
        this.parentId = parentId;
    }

    public LiveChartProtocol(String id, String  name, String parentId, long sentBytes, long receivedBytes) {
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
        return id.lastIndexOf(LiveChartConstants.ENCAPSULATION_PATH_SEPARATOR) > 0  ?
                id.substring(0, id.lastIndexOf(LiveChartConstants.ENCAPSULATION_PATH_SEPARATOR)) : null;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LiveChartProtocol that = (LiveChartProtocol) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }


}
