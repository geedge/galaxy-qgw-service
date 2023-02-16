package com.mesalab.services.configuration;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * TODO
 *
 * @Classname TaskCfg
 * @Date 2021/12/8 4:26 下午
 * @Author wWei
 */
@Data
@Component
public class JobConfig {
    public static final String COUNT = "count";
    public static final String IS_DONE = "is_done";
    public static final String DONE_PROGRESS = "done_progress";
    public static final String IS_CANCELED = "is_canceled";
    public static final String IS_FAILED = "is_failed";
    public static final String DURATION_TIME = "duration_time";
    public static final String LAST_QUERY_TIME = "last_query_time";
    public static final String STATISTICS_RESULT = "result";
    public static final String JOB_PROPERTY = "job_property";
    public static final String JOB_PROPERTY_TYPE = "type";
    public static final String JOB_PROPERTY_K_RESULT_FIELD = "result_field";
    public static final String JOB_PROPERTY_K_OPTION = "option";
    public static final String JOB_PROPERTY_K_SUB_OPTION = "sub_option";
    public static final String JOB_PROPERTY_V_OPTION_TIMECHART = "timechart";
    public static final String JOB_PROPERTY_V_OPTION_TOP = "top";
    public static final String JOB_PROPERTY_V_OPTION_RARE = "rate";
    public static final String JOB_PROPERTY_LIMIT = "limit";

    public static final String DETAIL = "detail";

    public static final String QUERY_DATA_SOURCE = "query.data_source";
    public static final String REPORT= "report";
    public static final String FIELD_DISCOVERY= "field_discovery";
    public static final String STATISTICS= "statistics";

    public static final String DELIMIT = "-";

    public static final String KEY_CUSTOM_FIELD_DISCOVERY_FIELDS = "custom.field_discovery.fields";
    public static final String KEY_CUSTOM_FIELD_DISCOVERY_FILTER = "custom.field_discovery.filter";
    public static final String KEY_CUSTOM_STATISTICS_SQL = "custom.statistics.sql";
    public static final String KEY_CUSTOM_REPORT_SQL = "custom.report.sql";
    public static final String KEY_QUERY_TYPE = "query.type";
    public static final String KEY_QUERY_DATA_SOURCE = "query.data_source";
    public static final String KEY_QUERY_SAMPLE_RATIO = "query.sample_ratio";


    @NacosValue(value = "${job.interactive.timeout}", autoRefreshed = true)
    private int interactiveTimeout;
    @NacosValue(value = "${job.response.timeout}", autoRefreshed = true)
    private int responseTimeout;
    @NacosValue(value = "${job.execution.timeout}", autoRefreshed = true)
    private int executionTimeout;
    @NacosValue(value = "${job.pool.corePoolSize}", autoRefreshed = true)
    private int poolCorePoolSize;
    @NacosValue(value = "${job.pool.maxPoolSize}", autoRefreshed = true)
    private int poolMaxPoolSize;
    @NacosValue(value = "${job.pool.queueCapacity}", autoRefreshed = true)
    private int poolQueueCapacity;
    @NacosValue(value="${job.longPolling.enabled}", autoRefreshed = true)
    private boolean longPollingEnabled;
    @NacosValue(value="${job.timeSlicing.enabled}", autoRefreshed = true)
    private boolean timeSlicingEnabled;
    @NacosValue(value="${job.timeSlicing.interval}", autoRefreshed = true)
    private int timeSlicingInterval;
}
