package com.mesalab.qgw.constant;

import java.util.*;

/**
 * TODO
 *
 * @Classname DslIdentifierNameConst
 * @Date 2023/11/28 13:55
 * @Author wWei
 */
public class DslIdentifierNameConst {
    public static final String APPLICATION_AND_PROTOCOL_SUMMARY = "application-and-protocol-summary";
    public static final String APPLICATION_AND_PROTOCOL_TREE_COMPOSITION = "application-and-protocol-tree-composition";
    public static final String APPLICATION_AND_PROTOCOL_TREE_THROUGHPUT = "application-and-protocol-tree-throughput";
    public static final String APPLICATION_AND_PROTOCOL_TOP_APPS = "application-and-protocol-top-apps";
    public static final String APPLICATION_AND_PROTOCOL_APP_RELATED_INTERNAL_IPS = "application-and-protocol-app-related-internal-ips";
    public static final String APPLICATION_AND_PROTOCOL_APP_THROUGHPUT = "application-and-protocol-app-throughput";
    public static final String APPLICATION_AND_PROTOCOL_APP_SUMMARY = "application-and-protocol-app-summary";
    public static final String IP_LEARNING_FQDN_RELATE_IP = "ip-learning-fqdn-relate-ip";
    public static final String IP_LEARNING_ACTIVE_IP = "ip-learning-active-ip";
    public static final String FIELD_DISCOVERY = "field_discovery";
    public static final String DATAPATH_PACKET_COMBINE = "datapath_telemetry_packet_combine";
    public static final String TRAFFIC_SPECTRUM_SUMMARY = "traffic-spectrum-summary";
    public static final String TRAFFIC_SPECTRUM_UNIQUE_CLIENT_AND_SERVER_IPS = "traffic-spectrum-unique-client-and-server-ips";
    public static final String TRAFFIC_SPECTRUM_APP_DISTRIBUTION = "traffic-spectrum-app-distribution";
    public static final String TRAFFIC_SPECTRUM_CLIENT_IP_CONNECT_APPLICATION_USAGE = "traffic-spectrum-client-ip-connect-application-usage";
    public static final String TRAFFIC_SPECTRUM_NETWORK_THROUGHPUT_TREND = "traffic-spectrum-network-throughput-trend";
    public static final Map<String, Set<String>> IDENTIFIER_NAME_SOURCE_MAPPING;

    static {
        IDENTIFIER_NAME_SOURCE_MAPPING = new HashMap<>();
        IDENTIFIER_NAME_SOURCE_MAPPING.put(APPLICATION_AND_PROTOCOL_SUMMARY, new HashSet<>(List.of("application_protocol_stat", "traffic_general_stat")));
        IDENTIFIER_NAME_SOURCE_MAPPING.put(APPLICATION_AND_PROTOCOL_TREE_COMPOSITION, new HashSet<>(List.of("application_protocol_stat")));
        IDENTIFIER_NAME_SOURCE_MAPPING.put(APPLICATION_AND_PROTOCOL_TREE_THROUGHPUT, new HashSet<>(List.of("application_protocol_stat")));
        IDENTIFIER_NAME_SOURCE_MAPPING.put(APPLICATION_AND_PROTOCOL_TOP_APPS, new HashSet<>(List.of("application_protocol_stat")));
        IDENTIFIER_NAME_SOURCE_MAPPING.put(APPLICATION_AND_PROTOCOL_APP_RELATED_INTERNAL_IPS, new HashSet<>(List.of("session_record")));
        IDENTIFIER_NAME_SOURCE_MAPPING.put(APPLICATION_AND_PROTOCOL_APP_THROUGHPUT, new HashSet<>(List.of("application_protocol_stat")));
        IDENTIFIER_NAME_SOURCE_MAPPING.put(APPLICATION_AND_PROTOCOL_APP_SUMMARY, new HashSet<>(List.of("application_protocol_stat")));
        IDENTIFIER_NAME_SOURCE_MAPPING.put(IP_LEARNING_FQDN_RELATE_IP, new HashSet<>());
        IDENTIFIER_NAME_SOURCE_MAPPING.put(IP_LEARNING_ACTIVE_IP, new HashSet<>());
        IDENTIFIER_NAME_SOURCE_MAPPING.put(FIELD_DISCOVERY, new HashSet<>());
        IDENTIFIER_NAME_SOURCE_MAPPING.put(DATAPATH_PACKET_COMBINE, new HashSet<>(List.of("datapath_telemetry_record")));
        IDENTIFIER_NAME_SOURCE_MAPPING.put(TRAFFIC_SPECTRUM_SUMMARY, new HashSet<>(List.of("traffic_sketch_metric")));
        IDENTIFIER_NAME_SOURCE_MAPPING.put(TRAFFIC_SPECTRUM_UNIQUE_CLIENT_AND_SERVER_IPS, new HashSet<>(List.of("traffic_sketch_metric")));
        IDENTIFIER_NAME_SOURCE_MAPPING.put(TRAFFIC_SPECTRUM_APP_DISTRIBUTION, new HashSet<>(List.of("traffic_sketch_metric")));
        IDENTIFIER_NAME_SOURCE_MAPPING.put(TRAFFIC_SPECTRUM_CLIENT_IP_CONNECT_APPLICATION_USAGE, new HashSet<>(List.of("traffic_sketch_metric")));
        IDENTIFIER_NAME_SOURCE_MAPPING.put(TRAFFIC_SPECTRUM_NETWORK_THROUGHPUT_TREND, new HashSet<>(List.of("traffic_sketch_metric")));
    }
}
