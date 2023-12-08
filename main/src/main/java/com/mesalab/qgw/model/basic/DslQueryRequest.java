package com.mesalab.qgw.model.basic;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Maps;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * TODO
 *
 * @Classname SqlQueryJob
 * @Date 2023/12/6 15:32
 * @Author wWei
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DslQueryRequest extends QueryRequest implements Serializable {
    @JsonProperty("name")
    private String name;
    @JsonProperty("data_source")
    private String dataSource;
    @JsonProperty("granularity")
    private String granularity;
    @JsonProperty("filter")
    private String filter;
    @JsonProperty("order_by")
    private String orderBy;
    @JsonProperty("intervals")
    private String intervals;
    @JsonProperty("limit")
    private String limit;
    private Map<String, Object> customAttributes = Maps.newHashMap();

    @JsonAnySetter
    public void setCustomAttributes(String key, Object value) {
        customAttributes.put(key, value);
    }
}
