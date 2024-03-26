package com.mesalab.common.enums;

import lombok.Getter;

/**
 * Create Query Job Execution Mode
 *
 * @Classname ExecutionMode
 * @Date 2023/12/6 17:06
 * @Author wWei
 */
@Getter
public enum OutputMode {

    /**
     * Desc: Output data in JSON format, But no meta.
     */
    JSON("json"),
    /**
     * Desc: Output data in CSV format.
     */
    CSV("csv"),
    /**
     * Desc: Output data in JSON format. It also contains some meta and statistics.
     */
    JSON_WITH_METADATA("jsonWithMetadata"),
    /**
     * Desc: Output data in JSON format, But no key. It also contains some meta and statistics.
     */
    JSON_COMPACT("jsonCompact");
    private final String value;

    OutputMode(String value) {
        this.value = value;
    }
}
