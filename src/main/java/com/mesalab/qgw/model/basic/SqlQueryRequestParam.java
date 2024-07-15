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
 *
 * HTTP Rest API request parameters for SQL query.
 * @Date 2023/12/6 15:32
 * @Author wWei
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SqlQueryRequestParam extends CommonRequestParam implements Serializable {
    private String statement;
    private Map<String, Object> customRequestParam = Maps.newHashMap();

    @JsonAnySetter
    public void setCustomAttributes(String key, Object value) {
        customRequestParam.put(key, value);
    }
}
