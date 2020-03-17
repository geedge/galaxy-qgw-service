package com.mesalab.common.enums;

import lombok.Getter;


@Getter
public enum JobHandlerEnum {

    DELETE_TRAFFIC_DATA_JOB_HANDLER("deleteTrafficDataJobHandler"),
    DELETE_REPORT_AND_METRICS_DATA_JOB_HANDLER("deleteReportAndMetricsDataJobHandler"),
    DELETE_ALL_TRAFFIC_DATA_JOB_HANDLER("deleteAllTrafficDataJobHandler"),
    DELETE_ALL_REPORT_AND_METRICS_DATA_JOB_HANDLER("deleteAllReportAndMetricsDataJobHandler"),
    ;
    private String value;

    JobHandlerEnum(String value) {
        this.value = value;
    }
}
