package com.mesalab.qgw.model.basic;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.geedgenetworks.utils.StringUtil;
import com.google.common.collect.Maps;
import com.mesalab.services.service.impl.JobExecuteService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * TODO
 *
 * @Classname DslQueryRequest
 * @Date 2023/12/6 15:32
 * @Author wWei
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DSLQueryRequestParam extends CommonRequestParam implements Serializable {
    @JsonProperty("name")
    private String name;
    @JsonProperty("data_source")
    private String dataSource;
    @JsonProperty("granularity")
    private String granularity;
    @JsonProperty("fields")
    private List<String> fields;
    @JsonProperty("filter")
    private String filter;
    @JsonProperty("order_by")
    private String orderBy;
    @JsonProperty("intervals")
    private List<String> intervals;
    @JsonProperty("limit")
    private String limit;
    private Map<String, Object> customRequestParam = Maps.newHashMap();

    @JsonAnySetter
    public void setCustomAttributes(String key, Object value) {
        customRequestParam.put(key, value);
    }

    public DateTime getStart() {
        if (this.intervals != null && this.intervals.size() == 1) {
            String[] split = this.intervals.get(0).split("/");
            return DateUtil.parse(split[0]);
        } else {
            return null;
        }
    }

    public DateTime getEnd() {
        if (this.intervals != null && this.intervals.size() == 1) {
            String[] split = this.intervals.get(0).split("/");
            return DateUtil.parse(split[1]);
        } else {
            return null;
        }
    }
}
