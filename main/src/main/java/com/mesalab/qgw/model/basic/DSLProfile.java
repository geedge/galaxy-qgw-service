package com.mesalab.qgw.model.basic;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.joda.time.Period;
import org.springframework.util.CollectionUtils;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DSLProfile implements Serializable {

    private String name;
    private String granularity;
    private String filter;
    @JsonProperty("order_by")
    private String orderBy;
    private List<String> intervals;
    private String limit;

    public String toSql(String template, String table, String timeField, String timeFieldType) {
        String sql = template;
        if (StrUtil.isNotEmpty(this.granularity) && template.contains("$granularity_seconds")) {
            int seconds = Period.parse(this.granularity).toStandardSeconds().getSeconds();
            sql = sql.replace("$granularity_seconds", Long.toString(seconds));
        }
        sql = StrUtil.isNotEmpty(this.granularity) ? sql.replace("$granularity", this.granularity) : sql;

        sql = StrUtil.isNotEmpty(table) ? sql.replace("$table", table) : sql;

        sql = sql.replace("$intervals_and_filter", StrUtil.isNotEmpty(this.filter) ?
                Objects.requireNonNull(parseIntervals(this.intervals, timeField, timeFieldType)).concat(" AND ").concat(this.filter)
                : Objects.requireNonNull(parseIntervals(this.intervals, timeField, timeFieldType)));

        sql = StrUtil.isNotEmpty(this.orderBy) ? sql.replace("$order_by", this.orderBy) : sql;

        sql = StrUtil.isNotEmpty(this.limit) ? sql.replace("$limit", "LIMIT ".concat(this.limit)) : sql;
        return sql;
    }

    private String parseIntervals(List<String> intervals, String timeField, String timeFieldType) {
        if (CollectionUtils.isEmpty(intervals) || !intervals.get(0).contains("/")) {
            return null;
        }
        String[] split = intervals.get(0).split("/");
        String start = split[0];
        String end = split[1];
        StringBuffer whereOfTime = new StringBuffer();
        if ("unix_timestamp".equalsIgnoreCase(timeFieldType)) {
            return whereOfTime.append(timeField).append(">= UNIX_TIMESTAMP('").append(start).append("') AND ").append(timeField).append("< UNIX_TIMESTAMP('").append(end).append("')").toString();
        } else {
            return whereOfTime.append(timeField).append(">= '").append(start).append("' AND ").append(timeField).append("< '").append(end).append("'").toString();
        }
    }
}
