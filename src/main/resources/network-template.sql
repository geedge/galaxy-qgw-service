--!NETWORK_OVERVIEW_SUMMARY
SELECT APPROX_COUNT_DISTINCT_DS_HLL(ip_object) AS uniq_client_ip,  SUM(one_sided_connections) AS one_sided_connections,  SUM(uncategorized_bytes) AS total_uncategorized_bytes,  SUM(fragmentation_packets) AS fragmentation_packets,  SUM(sequence_gap_loss) AS sequence_gap_loss_bytes,  SUM(s2c_byte_num+c2s_byte_num) AS summaryTotalBytes,  SUM(s2c_pkt_num+c2s_pkt_num) AS summaryTotalPackets,  SUM(sessions) AS summarySessions  FROM %s  WHERE %s  %s LIMIT 1
--!NETWORK_OVERVIEW_STAT
(SELECT SUM(c2s_byte_num + s2c_byte_num) as total_bytes, SUM(sessions) as total_sessions, (SUM(c2s_byte_num + s2c_byte_num) * 8)/((TIMESTAMP_TO_MILLIS(TIMESTAMP '%s')-TIMESTAMP_TO_MILLIS(TIMESTAMP '%s'))/1000) AS data_rate  FROM traffic_protocol_stat_log  WHERE %s %s AND protocol_id = '%s' LIMIT 1) UNION ALL ( SELECT SUM(sessions), 0, 0 FROM traffic_protocol_stat_log WHERE %s %sAND protocol_id = '%s' GROUP BY __time ORDER BY __time DESC LIMIT 1 )
--!NETWORK_TREE_SUMMARY
SELECT protocol_id, SUM(sessions) as sessions,SUM(c2s_byte_num) as c2s_byte_num, SUM(c2s_pkt_num) as c2s_pkt_num, SUM(s2c_byte_num) as s2c_byte_num, SUM(s2c_pkt_num) as s2c_pkt_num FROM %s WHERE %s %s GROUP BY protocol_id
--!NETWORK_DATA_SUMMARY
(SELECT TIME_FORMAT(MILLIS_TO_TIMESTAMP( 1000 * TIME_FLOOR_WITH_FILL(TIMESTAMP_TO_MILLIS(__time)/1000, '%s', 'zero')), 'yyyy-MM-dd HH:mm:ss') as stat_time, protocol_id as type, sum(c2s_byte_num + s2c_byte_num) as bytes
 from %s
 where %s %s and protocol_id = '%s'
 group by TIME_FORMAT(MILLIS_TO_TIMESTAMP( 1000 * TIME_FLOOR_WITH_FILL(TIMESTAMP_TO_MILLIS(__time)/1000, '%s', 'zero')), 'yyyy-MM-dd HH:mm:ss'), protocol_id
 order by stat_time asc)
union all
(SELECT TIME_FORMAT(MILLIS_TO_TIMESTAMP( 1000 * TIME_FLOOR_WITH_FILL(TIMESTAMP_TO_MILLIS(__time)/1000, '%s', 'zero')), 'yyyy-MM-dd HH:mm:ss') as stat_time, protocol_id as type, sum(c2s_byte_num + s2c_byte_num) as bytes
 from %s
 where %s %s and protocol_id like CONCAT('%s','.%s') and LENGTH(protocol_id) = LENGTH(REPLACE(protocol_id,'.','')) + 1 + %s
 group by TIME_FORMAT(MILLIS_TO_TIMESTAMP( 1000 * TIME_FLOOR_WITH_FILL(TIMESTAMP_TO_MILLIS(__time)/1000, '%s', 'zero')), 'yyyy-MM-dd HH:mm:ss'), protocol_id
 order by stat_time asc)