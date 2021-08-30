#sql("NETWORK_OVERVIEW_SUMMARY")
SELECT APPROX_COUNT_DISTINCT_DS_HLL(ip_object) AS uniq_client_ip,  SUM(one_sided_connections) AS one_sided_connections,  SUM(uncategorized_bytes) AS total_uncategorized_bytes,  SUM(fragmentation_packets) AS fragmentation_packets,  SUM(sequence_gap_loss) AS sequence_gap_loss_bytes,  SUM(s2c_byte_num+c2s_byte_num) AS summaryTotalBytes,  SUM(s2c_pkt_num+c2s_pkt_num) AS summaryTotalPackets,  SUM(sessions) AS summarySessions  FROM %s  WHERE %s  %s LIMIT 1
#end

#sql("NETWORK_OVERVIEW_STAT")
(SELECT SUM(c2s_byte_num + s2c_byte_num) as total_bytes, SUM(sessions) as total_sessions, (SUM(c2s_byte_num + s2c_byte_num) * 8)/((TIMESTAMP_TO_MILLIS(TIMESTAMP '%s')-TIMESTAMP_TO_MILLIS(TIMESTAMP '%s'))/1000) AS data_rate  FROM traffic_protocol_stat_log  WHERE %s %s AND protocol_id = '%s' LIMIT 1) UNION ALL ( SELECT SUM(sessions), 0, 0 FROM traffic_protocol_stat_log WHERE %s %sAND protocol_id = '%s' GROUP BY __time ORDER BY __time DESC LIMIT 1 )
#end

#sql("NETWORK_TREE_SUMMARY")
SELECT protocol_id, SUM(sessions) as sessions,SUM(c2s_byte_num) as c2s_byte_num, SUM(c2s_pkt_num) as c2s_pkt_num, SUM(s2c_byte_num) as s2c_byte_num, SUM(s2c_pkt_num) as s2c_pkt_num FROM %s WHERE %s %s GROUP BY protocol_id
#end

#sql("NETWORK_DATA_SUMMARY")
(SELECT TIME_FORMAT(MILLIS_TO_TIMESTAMP( 1000 * TIME_FLOOR_WITH_FILL(TIMESTAMP_TO_MILLIS(__time)/1000, '%s', 'zero')), 'yyyy-MM-dd HH:mm:ss') as stat_time, protocol_id as type, sum(c2s_byte_num + s2c_byte_num) as bytes from %s where %s %s and protocol_id = '%s' group by TIME_FORMAT(MILLIS_TO_TIMESTAMP( 1000 * TIME_FLOOR_WITH_FILL(TIMESTAMP_TO_MILLIS(__time)/1000, '%s', 'zero')), 'yyyy-MM-dd HH:mm:ss'), protocol_id order by stat_time asc)
union all
(SELECT TIME_FORMAT(MILLIS_TO_TIMESTAMP( 1000 * TIME_FLOOR_WITH_FILL(TIMESTAMP_TO_MILLIS(__time)/1000, '%s', 'zero')), 'yyyy-MM-dd HH:mm:ss') as stat_time, protocol_id as type, sum(c2s_byte_num + s2c_byte_num) as bytes from %s where %s %s and protocol_id like CONCAT('%s','.%s') and LENGTH(protocol_id) = LENGTH(REPLACE(protocol_id,'.','')) + 1 + %s group by TIME_FORMAT(MILLIS_TO_TIMESTAMP( 1000 * TIME_FLOOR_WITH_FILL(TIMESTAMP_TO_MILLIS(__time)/1000, '%s', 'zero')), 'yyyy-MM-dd HH:mm:ss'), protocol_id order by stat_time asc)
#end

#sql("ENTITY_ACTIVE_CLIENT_IP")
select common_client_ip from %s where %s %s GROUP BY common_client_ip, common_app_label ORDER BY SUM (common_sessions) DESC LIMIT %s
#end

#sql("ENTITY_TOP_SERVER_IP")
SELECT DISTINCT(ip) FROM (
    (select common_server_ip as ip from %s where %s AND (common_l4_protocol IN ('IPv4_UDP' , 'IPv6_UDP')) and common_server_port in (53 , 443) group by ip order by sum(common_sessions) desc limit 100000)
    UNION ALL
    (select common_server_ip as ip from %s where %s AND (common_l4_protocol IN ('IPv4_UDP' , 'IPv6_UDP')) and common_server_port in (53 , 443) group by ip order by COUNT(DISTINCT(common_client_ip)) desc limit 40000)
    UNION ALL
    (select common_server_ip as ip from %s where %s AND (common_l4_protocol IN ('IPv4_TCP' , 'IPv6_TCP')) group by ip order by sum(common_sessions) desc limit 100000)
    UNION ALL
    (select common_server_ip as ip from %s where %s AND (common_l4_protocol IN ('IPv4_TCP' , 'IPv6_TCP')) group by ip order by COUNT(DISTINCT(common_client_ip)) desc limit 100000))
#end

#sql("ENTITY_TOP_SNI")
select ssl_sni from %s where %s and notEmpty(ssl_sni) group by ssl_sni order by SUM (common_sessions) desc limit 30000
#end

#sql("SYSTEM_STORAGE_QUOTA")
SELECT log_type, SUM(used_size) as used_size, SUM(max_size) * 7 / 10 as max_size, TIME_FORMAT(LATEST(last_storage) * 1000, 'YYYY-MM-dd') as first_storage FROM
    (SELECT log_type, LATEST(used_size) as used_size, LATEST(max_size) as max_size, LATEST(last_storage) as last_storage FROM sys_storage_log WHERE __time >= CURRENT_TIMESTAMP - INTERVAL '1' HOUR AND data_center != '' GROUP BY data_center, log_type)
GROUP BY log_type
#end

#sql("SYSTEM_DAILY_TREND_OF_STORAGE")
select TIME_FORMAT(__time, 'YYYY-MM-dd') as stat_time, log_type as type, sum(aggregate_size) as used_size from sys_storage_log where __time >= '%s' and __time < '%s' group by TIME_FORMAT(__time, 'YYYY-MM-dd'), log_type
#end