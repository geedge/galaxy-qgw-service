#sql("APPLICATION_AND_PROTOCOL_SUMMARY_ASYMMETRIC_FLOWS_STAT")
SELECT SUM(asymmetric_c2s_flows + asymmetric_s2c_flows) AS asymmetric_flows, SUM(closed_sessions) AS total_session_used_on_asymmetric_flows FROM $table  WHERE $intervals_and_filter LIMIT 1
#end
#sql("APPLICATION_AND_PROTOCOL_SUMMARY_TRAFFIC_STAT")
SELECT SUM(fragmentation_packets) AS fragmentation_packets, SUM(total_bytes) AS total_bytes, SUM(total_packets) AS total_packets, SUM(total_sessions) AS total_sessions, AVG(total_bytes) * 8 / $granularity_seconds AS data_rate FROM ( SELECT SUM(c2s_fragments + s2c_fragments) AS fragmentation_packets, SUM(c2s_bytes + s2c_bytes) AS total_bytes, SUM(c2s_pkts + s2c_pkts) AS total_packets, SUM(sessions) AS total_sessions FROM  $table WHERE $intervals_and_filter AND protocol_stack_id = 'ETHERNET' GROUP BY TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(__time), '$granularity')) LIMIT 1
#end
#sql("APPLICATION_AND_PROTOCOL_SUMMARY_TCP_STAT")
SELECT SUM(c2s_tcp_retransmitted_pkts + s2c_tcp_retransmitted_pkts) AS tcp_retransmissions_packets, SUM(c2s_pkts + s2c_pkts) AS tcp_total_packets FROM $table WHERE $intervals_and_filter AND RIGHT(protocol_stack_id, 4) = '.TCP' LIMIT 1
#end
#sql("APPLICATION_AND_PROTOCOL_SUMMARY_APP_STAT")
SELECT SUM(c2s_bytes + s2c_bytes) as unknown_app_bytes FROM $table WHERE $intervals_and_filter AND app_name = 'unknown' LIMIT 1
#end
#sql("APPLICATION_AND_PROTOCOL_TREE_COMPOSITION")
SELECT protocol_stack_id, SUM(sessions) as sessions,SUM(c2s_bytes) as c2s_bytes, SUM(c2s_pkts) as c2s_pkts, SUM(s2c_bytes) as s2c_bytes, SUM(s2c_pkts) as s2c_pkts FROM $table WHERE $intervals_and_filter GROUP BY protocol_stack_id
#end
#sql("APPLICATION_AND_PROTOCOL_TREE_THROUGHPUT")
SELECT FROM_UNIXTIME(TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(__time), '$granularity', 'zero')) as stat_time, protocol_stack_id as type, sum(c2s_bytes + s2c_bytes) as bytes from $table where $intervals_and_filter group by FROM_UNIXTIME(TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(__time), '$granularity', 'zero')), protocol_stack_id order by stat_time asc
#end
#sql("APPLICATION_AND_PROTOCOL_TOP_APPS")
SELECT app_name as app_name, SUM(bytes) as bytes, SUM(sessions) as sessions, MAX(bit_rate) as max_rate, SUM(bytes)/$interval_seconds as avg_rate FROM (SELECT app_name as app_name, SUM(sessions) as sessions, SUM(c2s_bytes + s2c_bytes) as bytes, SUM(c2s_bytes + s2c_bytes) * 8 / $granularity_seconds as bit_rate FROM $table WHERE $intervals_and_filter AND notEmpty(app_name) GROUP BY app_name, TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(__time), '$granularity') ORDER BY bytes DESC ) GROUP BY app_name ORDER BY bytes DESC $limit
#end
#sql("APPLICATION_AND_PROTOCOL_APP_RELATED_INTERNAL_IPS")
SELECT if(bitAnd(flags, 8) = 8, client_ip, server_ip) AS ip, SUM(sent_bytes + received_bytes) AS bytes FROM $table WHERE $intervals_and_filter GROUP BY ip ORDER BY bytes DESC $limit
#end
#sql("APPLICATION_AND_PROTOCOL_APP_THROUGHPUT")
SELECT FROM_UNIXTIME(TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(__time),'$granularity','zero')) AS stat_time, app_name, SUM(c2s_bytes + s2c_bytes) AS bytes FROM $table WHERE $intervals_and_filter GROUP BY FROM_UNIXTIME(TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(__time),'$granularity','zero')), app_name ORDER BY  stat_time ASC
#end
#sql("APPLICATION_AND_PROTOCOL_APP_SUMMARY")
SELECT app_name, SUM(sessions) AS sessions, SUM(c2s_bytes + s2c_bytes) AS bytes, SUM(s2c_bytes) AS received_bytes, SUM(c2s_bytes) AS sent_bytes, SUM(c2s_pkts + s2c_pkts) AS packets, SUM(c2s_pkts) AS sent_packets, SUM(s2c_pkts) AS received_packets FROM $table WHERE $intervals_and_filter GROUP BY app_name
#end
#sql("REAL_TIME_DATA_ANALYTICS_SUBSCRIBER_ID_RELATE_IP")
SELECT account AS SUBSCRIBER_ID, framed_ip AS IP, first_found_time AS FIRST_FOUND_TIME, last_update_time AS LAST_FOUND_TIME, vsys_id as VSYS_ID FROM $table WHERE $intervals_and_filter AND acct_status_type != 2 $limit
#end
#sql("REAL_TIME_DATA_ANALYTICS_UNSTRUCTURED_DATA")
SELECT $primary_key AS log_id, $field AS file_path FROM $table where $intervals_and_filter AND notEmpty($field) ORDER BY $partition_key DESC $limit
#end