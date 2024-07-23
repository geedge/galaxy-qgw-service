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

    public static final String STATISTICS = "statistics";
    public static final String JOB = "job";
    public static final String META = "meta";
    public static final String DATA = "data";
    public static final String JOB_RESOURCE_PATH = "resource_path";
    public static final String JOB_ID = "job_id";
    public static final String COUNT = "count";
    public static final String IS_DONE = "is_done";
    public static final String START_TIME = "start_time";
    public static final String END_TIME = "end_time";
    public static final String DONE_PROGRESS = "done_progress";
    public static final String IS_CANCELED = "is_canceled";
    public static final String IS_FAILED = "is_failed";
    public static final String REASON = "reason";
    public static final String LINKS = "links";
    public static final String LINKS_STATUS = "status";
    public static final String LINKS_RESULT = "result";
    public static final String DURATION_TIME = "duration_time";
    public static final String LAST_QUERY_TIME = "last_query_time";
    public static final String LONG_TERM_RESULT = "result";
    public static final String JOB_PROPERTY = "job_property";
    public static final String JOB_PROPERTY_TYPE = "type";
    public static final String JOB_PROPERTY_K_RESULT_FIELD = "result_field";
    public static final String JOB_PROPERTY_K_OPTION = "option";
    public static final String JOB_PROPERTY_K_SUB_OPTION = "sub_option";
    public static final String JOB_PROPERTY_K_SUB_OPTION_DIMENSION = "dimension";
    public static final String JOB_PROPERTY_K_SUB_OPTION_METRIC = "metric";
    public static final String JOB_PROPERTY_V_OPTION_TIMECHART = "timechart";
    public static final String JOB_PROPERTY_V_OPTION_TOP = "top";
    public static final String JOB_PROPERTY_V_OPTION_RARE = "rate";
    public static final String JOB_PROPERTY_LIMIT = "limit";

    public static final String DETAIL = "detail";

    public static final String FILTER = "filter";
    public static final String QUERY_DATA_SOURCE = "query.data_source";
    public static final String SAVED_QUERY = "saved_query";
    public static final String FIELD_DISCOVERY = "field_discovery";
    public static final String DATAPATH_PACKET_COMBINE = "datapath_telemetry_packet_combine";
    public static final String TRAFFIC_SPECTRUM_SUMMARY = "traffic-spectrum-summary";
    public static final String TRAFFIC_SPECTRUM_UNIQUE_CLIENT_AND_SERVER_IPS = "traffic-spectrum-unique-client-and-server-ips";
    public static final String TRAFFIC_SPECTRUM_APP_DISTRIBUTION = "traffic-spectrum-app-distribution";
    public static final String TRAFFIC_SPECTRUM_CLIENT_IP_CONNECT_APPLICATION_USAGE = "traffic-spectrum-client-ip-connect-application-usage";
    public static final String TRAFFIC_SPECTRUM_NETWORK_THROUGHPUT_TREND = "traffic-spectrum-network-throughput-trend";
    public static final String CUSTOMIZED_STATISTICS = "customized-statistics";
    public static final String LONG_TERM_COLUMN_FAMILY = "long_term";

    public static final String DELIMIT = "-";

    public static final String KEY_CUSTOM_FIELD_DISCOVERY_FIELDS = "custom.field_discovery.fields";
    public static final String KEY_CUSTOM_FIELD_DISCOVERY_METRIC = "custom.field_discovery.metric";
    public static final String FIELD_DISCOVERY_TOPK_METRIC_PREFIX = "metric_";
    public static final String KEY_CUSTOM_FIELD_DISCOVERY_METRIC_FN = "custom.field_discovery.metric.fn";
    public static final String KEY_CUSTOM_FIELD_DISCOVERY_FILTER = "custom.field_discovery.filter";
    public static final String KEY_CUSTOM_STATISTICS_SQL = "custom.statistics.sql";
    public static final String KEY_CUSTOM_SAVED_QUERY_SQL = "custom.saved_query.sql";
    public static final String KEY_QUERY_TYPE = "query.type";
    public static final String KEY_QUERY_JOB_IDS = "query.job_ids";
    public static final String KEY_QUERY_DATA_SOURCE = "query.data_source";
    public static final String KEY_QUERY_SAMPLE_RATIO = "query.sample_ratio";


    @NacosValue(value = "${job.interactive.timeout}", autoRefreshed = true)
    private int interactiveTimeout;
    @NacosValue(value = "${job.response.timeout}", autoRefreshed = true)
    private int responseTimeout;
    @NacosValue(value = "${job.execution.timeout}", autoRefreshed = true)
    private int executionTimeout;
    @NacosValue(value = "${job.longPolling.enabled}", autoRefreshed = true)
    private boolean longPollingEnabled;
    @NacosValue(value = "${job.timeSlicing.enabled}", autoRefreshed = true)
    private boolean timeSlicingEnabled;
    @NacosValue(value = "${job.timeSlicing.interval}", autoRefreshed = true)
    private int timeSlicingInterval;

    @NacosValue(value = "${job.threadPool.lightWeight.corePoolSize}", autoRefreshed = true)
    private int lightWeightCorePoolSize;
    @NacosValue(value = "${job.threadPool.lightWeight.maxPoolSize}", autoRefreshed = true)
    private int lightWeightMaxPoolSize;
    @NacosValue(value = "${job.threadPool.lightWeight.queueCapacity}", autoRefreshed = true)
    private int lightWeightQueueCapacity;
    @NacosValue(value = "${job.threadPool.heavyResource.corePoolSize}", autoRefreshed = true)
    private int heavyResourceCorePoolSize;
    @NacosValue(value = "${job.threadPool.heavyResource.maxPoolSize}", autoRefreshed = true)
    private int heavyResourceMaxPoolSize;
    @NacosValue(value = "${job.threadPool.heavyResource.queueCapacity}", autoRefreshed = true)
    private int heavyResourceQueueCapacity;
}
