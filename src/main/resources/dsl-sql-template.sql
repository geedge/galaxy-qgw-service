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
SELECT app_name as app_name, SUM(bytes) as bytes, SUM(sessions) as sessions, MAX(bit_rate) as max_rate, SUM(bytes) * 8/$interval_seconds as avg_rate FROM (SELECT app_name as app_name, SUM(sessions) as sessions, SUM(c2s_bytes + s2c_bytes) as bytes, SUM(c2s_bytes + s2c_bytes) * 8 / $granularity_seconds as bit_rate FROM $table WHERE $intervals_and_filter AND notEmpty(app_name) GROUP BY app_name, TIME_FLOOR_WITH_FILL(UNIX_TIMESTAMP(__time), '$granularity') ORDER BY bytes DESC ) GROUP BY app_name ORDER BY bytes DESC $limit
#end
#sql("APPLICATION_AND_PROTOCOL_APP_RELATED_INTERNAL_IPS")
SELECT if(bitAnd(flags, 8) = 8, client_ip, server_ip) AS ip, SUM(sent_bytes + received_bytes) AS bytes FROM $table WHERE $intervals_and_filter GROUP BY ip ORDER BY bytes DESC $limit
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
SELECT COUNT(DISTINCT(IF(direction = 'Outbound', client_ip, NULL))) AS internal_uniq_client_ip, COUNT(DISTINCT(IF(direction = 'Outbound', server_ip, NULL))) AS external_uniq_server_ip, COUNT(DISTINCT(IF(direction = 'Inbound', server_ip, NULL))) AS internal_uniq_server_ip, COUNT(DISTINCT(IF(direction = 'Inbound', client_ip, NULL))) AS external_uniq_client_ip FROM $table WHERE $intervals_and_filter
#end
#sql("TRAFFIC_SPECTRUM_APP_DISTRIBUTION_SERVER_IP")
SELECT server_ip as value, SUM(bytes) as bytes, SUM(sessions) as sessions, SUM(pkts) as packets FROM $table WHERE $intervals_and_filter GROUP BY server_ip ORDER BY bytes DESC $limit
#end
#sql("TRAFFIC_SPECTRUM_APP_DISTRIBUTION_SERVER_DOMAIN")
SELECT server_domain as value, SUM(bytes) as bytes, SUM(sessions) as sessions, SUM(pkts) as packets FROM $table WHERE $intervals_and_filter AND notEmpty(server_domain) GROUP BY server_domain ORDER BY bytes DESC $limit
#end
#sql("TRAFFIC_SPECTRUM_CLIENT_IP_CONNECT_APPLICATION_USAGE")
SELECT direction, SUM(temp_sessions) AS sessions, SUM(temp_bytes) AS bytes, SUM(temp_packets) AS packets, MAX(temp_rate) AS max_rate, ROUND(SUM(temp_bytes) * 8 /$interval_seconds) AS avg_rate, client_ip AS client_ip, client_country AS client_country, app AS app, app_category AS app_category FROM( SELECT toUnixTimestamp(toDateTime(toStartOfInterval(toDateTime(recv_time), INTERVAL $granularity_seconds SECOND))) AS stat_time, IF(client_ip IN ( SELECT client_ip FROM $table WHERE $intervals_and_filter AND notEmpty(app) GROUP BY client_ip,app ORDER BY SUM(bytes) DESC $limit), client_ip, 'Other') AS client_ip, IF(client_ip IN ( SELECT client_ip FROM $table WHERE $intervals_and_filter AND notEmpty(app) GROUP BY client_ip,app ORDER BY SUM(bytes) DESC $limit), client_country, 'Other') AS client_country, IF(app IN ( SELECT app FROM $table WHERE $intervals_and_filter AND notEmpty(app) GROUP BY client_ip,app ORDER BY SUM(bytes) DESC $limit), app, 'Other') AS app, IF(app IN ( SELECT app FROM $table WHERE $intervals_and_filter AND notEmpty(app) GROUP BY client_ip,app ORDER BY SUM(bytes) DESC $limit), app_category, 'Other') AS app_category, direction, SUM(sessions) AS temp_sessions, SUM(bytes) AS temp_bytes, SUM(pkts) AS temp_packets, ROUND(SUM(bytes) * 8 / $granularity_seconds) AS temp_rate FROM $table WHERE $intervals_and_filter GROUP BY stat_time, client_ip, client_country, app, app_category, direction) GROUP BY client_ip, client_country, app, app_category, direction ORDER BY bytes DESC
#end
#sql("TRAFFIC_SPECTRUM_NETWORK_THROUGHPUT_TREND")
SELECT FROM_UNIXTIME(TIME_FLOOR_WITH_FILL(recv_time, '$granularity', 'zero')) AS stat_time, ROUND(RATE(bytes,$granularity_seconds,1)) * 8 AS avg_bits_per_sec, ROUND(RATE(bytes,$granularity_seconds,1)) AS avg_bytes_per_sec, ROUND(RATE(pkts,$granularity_seconds,1)) AS avg_pkts_per_sec, ROUND(RATE(sessions,$granularity_seconds,1)) AS avg_sessions_per_sec FROM $table WHERE $intervals_and_filter GROUP BY stat_time ORDER BY stat_time ASC $limit
#end