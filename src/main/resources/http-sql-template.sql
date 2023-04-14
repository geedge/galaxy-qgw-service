#sql("NETWORK_STATISTICS_SUMMARY")
SELECT APPROX_COUNT_DISTINCT_DS_HLL(ip_object) AS uniq_client_ip,  SUM(one_sided_connections) AS one_sided_connections,  SUM(uncategorized_bytes) AS total_uncategorized_bytes,  SUM(fragmentation_packets) AS fragmentation_packets,  SUM(sequence_gap_loss) AS sequence_gap_loss_bytes,  SUM(s2c_byte_num+c2s_byte_num) AS total_bytes,  SUM(s2c_pkt_num+c2s_pkt_num) AS total_packets,  SUM(sessions) AS total_sessions  FROM %s  WHERE %s  %s LIMIT 1
#end

#sql("TRAFFIC_DYNAMIC_STATISTICS")
SELECT SUM(c2s_byte_num + s2c_byte_num) as dynamic_bytes, SUM(sessions) as dynamic_sessions, (SUM(c2s_byte_num + s2c_byte_num) * 8)/((TIMESTAMP_TO_MILLIS(%s)-TIMESTAMP_TO_MILLIS(%s))/1000) AS data_rate  FROM traffic_protocol_stat_log  WHERE %s %s AND protocol_id = '%s' LIMIT 1
#end

#sql("PROTOCOL_TREE_SUMMARY")
SELECT protocol_id, SUM(sessions) as sessions,SUM(c2s_byte_num) as c2s_byte_num, SUM(c2s_pkt_num) as c2s_pkt_num, SUM(s2c_byte_num) as s2c_byte_num, SUM(s2c_pkt_num) as s2c_pkt_num FROM %s WHERE %s %s GROUP BY protocol_id
#end

#sql("PROTOCOL_DATA_RATE_SUMMARY")
(SELECT TIME_FORMAT(MILLIS_TO_TIMESTAMP( 1000 * TIME_FLOOR_WITH_FILL(TIMESTAMP_TO_MILLIS(__time)/1000, '%s', 'zero')), 'yyyy-MM-dd HH:mm:ss') as stat_time, protocol_id as type, sum(c2s_byte_num + s2c_byte_num) as bytes from %s where %s %s and protocol_id = '%s' group by TIME_FORMAT(MILLIS_TO_TIMESTAMP( 1000 * TIME_FLOOR_WITH_FILL(TIMESTAMP_TO_MILLIS(__time)/1000, '%s', 'zero')), 'yyyy-MM-dd HH:mm:ss'), protocol_id order by stat_time asc)
union all
(SELECT TIME_FORMAT(MILLIS_TO_TIMESTAMP( 1000 * TIME_FLOOR_WITH_FILL(TIMESTAMP_TO_MILLIS(__time)/1000, '%s', 'zero')), 'yyyy-MM-dd HH:mm:ss') as stat_time, protocol_id as type, sum(c2s_byte_num + s2c_byte_num) as bytes from %s where %s %s and protocol_id like CONCAT('%s','.%s') and LENGTH(protocol_id) = LENGTH(REPLACE(protocol_id,'.','')) + 1 + %s group by TIME_FORMAT(MILLIS_TO_TIMESTAMP( 1000 * TIME_FLOOR_WITH_FILL(TIMESTAMP_TO_MILLIS(__time)/1000, '%s', 'zero')), 'yyyy-MM-dd HH:mm:ss'), protocol_id order by stat_time asc)
#end

#sql("ENTITY_ACTIVE_CLIENT_IP")
select common_client_ip as client_ip ,common_vsys_id as vsys_id from %s where %s %s AND notEmpty(common_client_ip) GROUP BY common_client_ip, common_app_label,common_vsys_id ORDER BY SUM (common_sessions) DESC LIMIT %s
#end

#sql("ENTITY_UDP_SESSION")
select common_server_ip as server_ip,common_vsys_id as vsys_id ,sum(common_sessions) as sessions from %s where %s AND (common_l4_protocol IN ('IPv4_UDP' , 'IPv6_UDP')) and common_server_port in (53 , 443) group by server_ip,common_vsys_id order by sessions desc limit %s
#end

#sql("ENTITY_UDP_UNIQ_CLIENT_IPS")
select common_server_ip as server_ip, common_vsys_id as vsys_id  from %s where %s AND (common_l4_protocol IN ('IPv4_UDP' , 'IPv6_UDP')) group by server_ip,common_vsys_id order by COUNT(DISTINCT(common_client_ip)) desc limit %s
#end

#sql("ENTITY_TCP_SESSION")
select common_server_ip as server_ip,common_vsys_id as vsys_id ,sum(common_sessions) as sessions from %s where %s AND (common_l4_protocol IN ('IPv4_TCP' , 'IPv6_TCP')) group by server_ip,common_vsys_id order by sessions desc limit %s
#end

#sql("ENTITY_TCP_UNIQ_CLIENT_IPS")
select common_server_ip as server_ip, common_vsys_id as vsys_id from %s where %s AND (common_l4_protocol IN ('IPv4_TCP' , 'IPv6_TCP')) group by server_ip, common_vsys_id order by COUNT(DISTINCT(common_client_ip)) desc limit %s
#end

#sql("TOP_ENTITY_TCP_UNIQ_CLIENT_IPS")
select COUNT(DISTINCT(common_client_ip)) as client_ips from %s where %s AND (common_l4_protocol IN ('IPv4_TCP' , 'IPv6_TCP')) AND common_server_ip in (select common_server_ip from %s as cc where %s AND (common_l4_protocol IN ('IPv4_TCP' , 'IPv6_TCP')) group by common_server_ip order by COUNT(DISTINCT(common_client_ip)) desc limit %s)
#end

#sql("TOP_ENTITY_UDP_UNIQ_CLIENT_IPS")
select COUNT(DISTINCT(common_client_ip)) as client_ips from %s where %s AND (common_l4_protocol IN ('IPv4_UDP' , 'IPv6_UDP')) AND common_server_ip in (select common_server_ip from %s as cc where %s AND (common_l4_protocol IN ('IPv4_UDP' , 'IPv6_UDP')) group by common_server_ip order by COUNT(DISTINCT(common_client_ip)) desc limit %s)
#end

#sql("TOTAL_ENTITY_UDP_SESSION")
select sum(common_sessions) as sessions from %s where %s AND (common_l4_protocol IN ('IPv4_UDP' , 'IPv6_UDP')) and common_server_port in (53 , 443) limit 1
#end

#sql("TOTAL_ENTITY_UDP_UNIQ_CLIENT_IPS")
select COUNT(DISTINCT(common_client_ip)) as uniq_client_ips from %s where %s AND (common_l4_protocol IN ('IPv4_UDP' , 'IPv6_UDP')) limit 1
#end

#sql("TOTAL_ENTITY_TCP_SESSION")
select sum(common_sessions) as sessions from %s where %s AND (common_l4_protocol IN ('IPv4_TCP' , 'IPv6_TCP')) limit 1
#end

#sql("TOTAL_ENTITY_TCP_UNIQ_CLIENT_IPS")
select COUNT(DISTINCT(common_client_ip)) as uniq_client_ips  from %s where %s AND (common_l4_protocol IN ('IPv4_TCP' , 'IPv6_TCP')) limit 1
#end

#sql("ENTITY_TOP_SNI")
select ssl_sni, SUM(common_c2s_byte_num + common_s2c_byte_num) AS bytes, SUM(common_sessions) AS sessions,common_vsys_id as vsys_id from %s where %s and notEmpty(ssl_sni) and common_schema_type = 'SSL' group by ssl_sni,common_vsys_id order by SUM (common_sessions) desc limit %s
#end

#sql("ENTITY_SNI_TOTAL")
select SUM(common_c2s_byte_num + common_s2c_byte_num) AS bytes, SUM(common_sessions) AS sessions from %s where %s and common_schema_type = 'SSL' limit 1
#end

#sql("SYSTEM_STORAGE_QUOTA")
SELECT log_type, SUM(used_size) as used_size, SUM(max_size) * 7 / 10 as max_size, TIME_FORMAT(LATEST(last_storage) * 1000, 'YYYY-MM-dd') as first_storage FROM
    (SELECT log_type, LATEST(used_size) as used_size, LATEST(max_size) as max_size, LATEST(last_storage) as last_storage FROM sys_storage_log WHERE __time >= CURRENT_TIMESTAMP - INTERVAL '1' HOUR AND data_center != '' GROUP BY data_center, log_type)
GROUP BY log_type
#end

#sql("SYSTEM_DAILY_TREND_OF_STORAGE")
select TIME_FORMAT(__time, 'YYYY-MM-dd') as stat_time, log_type as type, sum(aggregate_size) as used_size from sys_storage_log where __time >= '%s' and __time < '%s' group by TIME_FORMAT(__time, 'YYYY-MM-dd'), log_type
#end

#sql("RELATION_SUBSCRIBER_ID")
SELECT account AS SUBSCRIBER_ID,framed_ip AS IP,first_found_time AS FIRST_FOUND_TIME,last_update_time AS LAST_FOUND_TIME,vsys_id as VSYS_ID FROM tsg_galaxy.relation_account_framedip WHERE 1=1 %s AND acct_status_type != 2 LIMIT %s
#end

#sql("SCHEMA_STORAGE_SIZE")
SELECT name AS field, IFNULL(SUM(data_compressed_bytes), 0) AS bytes FROM columns_cluster WHERE "table" = '%s_local' AND  "database" = '%s' GROUP BY name ORDER BY bytes DESC
#end

#sql("TABLE_TTL")
SELECT name, engine_full AS table_ttl FROM tables WHERE name ='%s_local' AND  "database" = '%s' AND "engine" ='MergeTree'
#end

#sql("GTPC_KNOWLEDGE_BASE")
SELECT uplink_teid AS teid, apn, phone_number, imsi, imei, last_update_time,vsys_id FROM tsg_galaxy.gtpc_knowledge_base WHERE msg_type = 1 and uplink_teid !=0 %s order by last_update_time desc limit %s
#end
#sql("UNSTRUCTURED_DATA_PATH")
SELECT common_log_id AS log_id,%s AS file_path FROM %s where %s AND notEmpty(%s) ORDER BY common_recv_time DESC LIMIT %s
#end