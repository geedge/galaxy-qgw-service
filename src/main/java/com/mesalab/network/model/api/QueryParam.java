package com.mesalab.network.model.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * @author gouge
 * @version 1.0
 * @date 2020/7/7 6:58 下午
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class QueryParam {

    @NotBlank
    private String queryType;
    @NotBlank
    private String dataSource;
    private String granularity;
    private String parameters;
    private Interval intervals;

    public void setIntervals(List<String> intervals) {
        if (intervals == null || intervals.size() != 1) {
            throw new IllegalArgumentException("intervals params is error");
        }
        String str = intervals.get(0);
        int separator = str.indexOf("/");
        if (separator < 0) {
            throw new IllegalArgumentException("Format requires a '/' separator: " + str);
        }
        String leftStr = str.substring(0, separator);
        if (leftStr.length() <= 0) {
            throw new IllegalArgumentException("Format invalid: " + str);
        }
        String rightStr = str.substring(separator + 1);
        if (rightStr.length() <= 0) {
            throw new IllegalArgumentException("Format invalid: " + str);
        }
        DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        if (dateTimeFormatter.parseMillis(rightStr) - dateTimeFormatter.parseMillis(leftStr) <= 0) {
            throw new IllegalArgumentException("Intervals should be [startTime/endTime], " + str);
        }
        this.intervals = new Interval(leftStr, rightStr);
    }
}