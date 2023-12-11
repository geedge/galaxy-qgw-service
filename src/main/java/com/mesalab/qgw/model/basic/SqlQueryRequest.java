package com.mesalab.qgw.model.basic;

import com.fasterxml.jackson.annotation.JsonAnySetter;
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
 * @Classname SqlQueryRequest
 * @Date 2023/12/6 15:32
 * @Author wWei
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SqlQueryRequest extends QueryRequest implements Serializable {
    private String statement;
    private Map<String, Object> customAttributes = Maps.newHashMap();

    @JsonAnySetter
    public void setCustomAttributes(String key, Object value) {
        customAttributes.put(key, value);
    }
}
