#sql("APPLICATION_AND_PROTOCOL_SUMMARY_ASYMMETRIC_FLOWS_STAT")
SELECT SUM(asymmetric_c2s_flows + asymmetric_s2c_flows) AS asymmetric_flows, SUM(closed_sessions) AS total_session_used_on_asymmetric_flows FROM $table  WHERE $intervals_and_filter LIMIT 1
#end
#sql("APPLICATION_AND_PROTOCOL_SUMMARY_TRAFFIC_STAT")
SELECT SUM(fragmentation_packets) AS fragmentation_packets, SUM(total_bytes) AS total_bytes, SUM(total_packets) AS total_packets, SUM(total_sessions) AS total_sessions, ROUND(AVG(total_bytes) * 8 / $granularity_seconds) AS data_rate FROM ( SELECT SUM(c2s_fragments + s2c_fragments) AS fragmentation_packets, SUM(c2s_bytes + s2c_bytes) AS total_bytes, SUM(c2s_pkts + s2c_pkts) AS total_packets, SUM(sessions) AS total_sessions FROM  $table WHERE $intervals_and_filter AND protocol_stack_id = 'ETHERNET' GROUP BY TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(__time), '$granularity')) LIMIT 1
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
SELECT FROM_UNIXTIME(TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(__time), '$granularity', 'zero')) as stat_time, protocol_stack_id as type, sum(c2s_bytes + s2c_bytes) as bytes, SUM(c2s_bytes + s2c_bytes) * 8 / $granularity_seconds AS bit_rate from $table where $intervals_and_filter group by FROM_UNIXTIME(TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(__time), '$granularity', 'zero')), protocol_stack_id order by stat_time asc
#end
#sql("APPLICATION_AND_PROTOCOL_TOP_APPS")
SELECT app_name as app_name, SUM(bytes) as bytes, SUM(sessions) as sessions, MAX(bit_rate) as max_rate, SUM(bytes) * 8/$interval_seconds as avg_rate FROM (SELECT app_name as app_name, SUM(sessions) as sessions, SUM(c2s_bytes + s2c_bytes) as bytes, SUM(c2s_bytes + s2c_bytes) * 8 / $granularity_seconds as bit_rate FROM $table WHERE $intervals_and_filter AND notEmpty(app_name) GROUP BY app_name, TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(__time), '$granularity') ORDER BY bytes DESC ) GROUP BY app_name ORDER BY bytes DESC $limit
#end
#sql("APPLICATION_AND_PROTOCOL_APP_RELATED_INTERNAL_IPS")
SELECT if(bitAnd(flags, 8) = 8, source_ip, destination_ip) AS ip, SUM(sent_bytes + received_bytes) AS bytes FROM $table WHERE $intervals_and_filter GROUP BY ip ORDER BY bytes DESC $limit
#end
#sql("APPLICATION_AND_PROTOCOL_APP_THROUGHPUT")
SELECT FROM_UNIXTIME(TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(__time),'$granularity','zero')) AS stat_time, app_name, SUM(c2s_bytes + s2c_bytes) AS bytes, SUM(c2s_bytes + s2c_bytes) * 8 / $granularity_seconds AS bit_rate FROM $table WHERE $intervals_and_filter GROUP BY FROM_UNIXTIME(TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(__time),'$granularity','zero')), app_name ORDER BY  stat_time ASC
#end
#sql("APPLICATION_AND_PROTOCOL_APP_SUMMARY")
SELECT app_name, SUM(sessions) AS sessions, SUM(c2s_bytes + s2c_bytes) AS bytes, SUM(s2c_bytes) AS received_bytes, SUM(c2s_bytes) AS sent_bytes, SUM(c2s_pkts + s2c_pkts) AS packets, SUM(c2s_pkts) AS sent_packets, SUM(s2c_pkts) AS received_packets FROM $table WHERE $intervals_and_filter GROUP BY app_name
#end
#sql("DATAPATH_TELEMETRY_RECORD")
SELECT * FROM $table WHERE $intervals_and_filter ORDER BY timestamp_us ASC
#end
#sql("TRAFFIC_SPECTRUM_SUMMARY")
SELECT direction, SUM(bytes) AS total_bytes, SUM(sessions) AS total_sessions, SUM(pkts) AS total_packets, SUM(IF(app = 'unknown', bytes, 0)) AS unknown_app_bytes, SUM(asymmetric_c2s_flows + asymmetric_s2c_flows) AS asymmetric_flows, SUM(c2s_fragments + s2c_fragments) AS fragmentation_packets FROM $table WHERE $intervals_and_filter GROUP BY direction
#end
#sql("TRAFFIC_SPECTRUM_UNIQ_IP")
SELECT COUNT(DISTINCT(IF(direction = 'Outbound', source_ip, NULL))) AS internal_uniq_source_ip, COUNT(DISTINCT(IF(direction = 'Outbound', destination_ip, NULL))) AS external_uniq_destination_ip, COUNT(DISTINCT(IF(direction = 'Inbound', destination_ip, NULL))) AS internal_uniq_destination_ip, COUNT(DISTINCT(IF(direction = 'Inbound', source_ip, NULL))) AS external_uniq_source_ip FROM $table WHERE $intervals_and_filter
#end
#sql("TRAFFIC_SPECTRUM_APP_DISTRIBUTION_SERVER_IP")
SELECT destination_ip as value, SUM(bytes) as bytes, SUM(sessions) as sessions, SUM(pkts) as packets FROM $table WHERE $intervals_and_filter GROUP BY destination_ip ORDER BY bytes DESC $limit
#end
#sql("TRAFFIC_SPECTRUM_APP_DISTRIBUTION_SERVER_DOMAIN")
SELECT destination_domain as value, SUM(bytes) as bytes, SUM(sessions) as sessions, SUM(pkts) as packets FROM $table WHERE $intervals_and_filter AND notEmpty(destination_domain) GROUP BY destination_domain ORDER BY bytes DESC $limit
#end
#sql("TRAFFIC_SPECTRUM_CLIENT_IP_CONNECT_APPLICATION_USAGE")
SELECT direction, SUM(temp_sessions) AS sessions, SUM(temp_bytes) AS bytes, SUM(temp_packets) AS packets, MAX(temp_rate) AS max_rate, ROUND(SUM(temp_bytes) * 8 /$interval_seconds) AS avg_rate, source_ip AS source_ip, source_country AS source_country, app AS app, app_category AS app_category FROM( SELECT toUnixTimestamp(toDateTime(toStartOfInterval(toDateTime(recv_time), INTERVAL $granularity_seconds SECOND))) AS stat_time, IF(source_ip IN ( SELECT source_ip FROM $table WHERE $intervals_and_filter AND notEmpty(app) GROUP BY source_ip,app ORDER BY SUM(bytes) DESC $limit), source_ip, 'Other') AS source_ip, IF(source_country IN ( SELECT anyLast(source_country) FROM $table WHERE $intervals_and_filter AND notEmpty(app) GROUP BY source_ip,app ORDER BY SUM(bytes) DESC $limit), source_country, 'Other') AS source_country, IF(app IN ( SELECT app FROM $table WHERE $intervals_and_filter AND notEmpty(app) GROUP BY source_ip,app ORDER BY SUM(bytes) DESC $limit), app, 'Other') AS app, IF(app_category IN ( SELECT anyLast(app_category) FROM $table WHERE $intervals_and_filter AND notEmpty(app) GROUP BY source_ip,app ORDER BY SUM(bytes) DESC $limit), app_category, 'Other') AS app_category, direction, SUM(sessions) AS temp_sessions, SUM(bytes) AS temp_bytes, SUM(pkts) AS temp_packets, ROUND(SUM(bytes) * 8 / $granularity_seconds) AS temp_rate FROM $table WHERE $intervals_and_filter GROUP BY stat_time, source_ip, source_country, app, app_category, direction) GROUP BY source_ip, source_country, app, app_category, direction ORDER BY bytes DESC
#end
#sql("TRAFFIC_SPECTRUM_NETWORK_THROUGHPUT_TREND")
SELECT FROM_UNIXTIME(TIME_FLOOR_WITH_FILL(recv_time, '$granularity', 'zero')) AS stat_time, ROUND(RATE(bytes,$granularity_seconds,1)) * 8 AS avg_bits_per_sec, ROUND(RATE(bytes,$granularity_seconds,1)) AS avg_bytes_per_sec, ROUND(RATE(pkts,$granularity_seconds,1)) AS avg_pkts_per_sec, ROUND(RATE(sessions,$granularity_seconds,1)) AS avg_sessions_per_sec FROM $table WHERE $intervals_and_filter GROUP BY stat_time ORDER BY stat_time ASC $limit
#end
#sql("CUSTOMIZED_STATISTICS")
SELECT %s FROM $table WHERE $intervals_and_filter %s %s %s $limit
#end
#sql("CUSTOMIZED_STATISTICS_SUBQUERY_TOPK")
(%s) IN (SELECT %s FROM $table WHERE $intervals_and_filter %s %s $limit)
#end
#sql("LOG_QUERY_TIMELINE")
SELECT FROM_UNIXTIME(TIME_FLOOR_WITH_FILL(%s, '$granularity', 'zero')) AS stat_time, COUNT(1) AS count %s FROM $table WHERE $intervals_and_filter GROUP BY stat_time %s ORDER BY stat_time DESC
#end
#sql("LOG_QUERY_LIST")
SELECT %s FROM $table WHERE $intervals_and_filter ORDER BY %s $limit
#end