package com.mesalab.common.enums;

import com.mesalab.cn.enums.CDQueryType;
import lombok.Getter;

/**
 * Metric Type
 *
 * @Classname MetricType
 * @Date 2024/7/12 13:54
 * @Author wWei
 */
@Getter
public enum MetricType {

    /**
     * Desc: Represents the total number of occurrences or records of a specific event occurred within a specified time period. It's often used to accumulate the total count of events within a specified time period.
     */
    COUNT("count"),
    /**
     * Desc: Represents a specific value at a particular point in time. It provides an instantaneous measurement, such as current memory usage or CPU load.
     */
    GAUGE("gauge"),
    /**
     * Desc: Provides statistical information about data distribution, such as sum, average, median, maximum, minimum, etc. It's used to describe the distribution of data.
     */
    SUMMARY("summary"),
    /**
     * Desc: Describes the distribution of data within a range, often including percentiles or other measures that describe the shape of the data distribution.
     */
    DISTRIBUTION("distribution"),
    /**
     * Desc: Represents the number of distinct values within a specified time period. It's commonly used to count unique users or unique events.
     */
    UNIQUE_COUNT("unique_count");
    private final String value;

    MetricType(String value) {
        this.value = value;
    }

    public static MetricType getMetricTypeByValue(String value) {
        for (MetricType constants : values()) {
            if (constants.value.equalsIgnoreCase(value)) {
                return constants;
            }
        }
        return null;
    }
}
