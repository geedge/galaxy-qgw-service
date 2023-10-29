#sql("NETWORK_OVERVIEW_ASYMMETRIC_FLOWS_STAT")
SELECT SUM(asymmetric_c2s_flows + asymmetric_s2c_flows) AS asymmetric_flows, SUM(closed_sessions) AS total_session_used_on_asymmetric_flows FROM traffic_general_stat  WHERE %s  %s LIMIT 1
#end

#sql("NETWORK_OVERVIEW_STAT")
SELECT	APPROX_COUNT_DISTINCT_DS_HLL(client_ip_sketch) AS uniq_client_ip, SUM(c2s_fragments + s2c_fragments) AS fragmentation_packets, SUM(c2s_bytes + s2c_bytes) AS total_bytes, SUM(c2s_pkts + s2c_pkts) AS total_packets, SUM(sessions) AS total_sessions, (SUM(c2s_bytes + s2c_bytes) * 8)/(%s -%s) AS data_rate  FROM %s  WHERE %s %s AND protocol_stack_id = '%s' LIMIT 1
#end

#sql("NETWORK_OVERVIEW_TCP_STAT")
SELECT SUM(c2s_tcp_retransmitted_pkts + s2c_tcp_retransmitted_pkts) AS tcp_retransmissions_packets, SUM(c2s_pkts + s2c_pkts) AS tcp_total_packets FROM %s  WHERE %s %s AND RIGHT(protocol_stack_id, 4) = '.TCP' LIMIT 1
#end

#sql("NETWORK_OVERVIEW_APP_STAT")
SELECT SUM(c2s_bytes + s2c_bytes) as unknown_app_bytes FROM %s WHERE %s %s AND app_name = 'unknown' LIMIT 1
#end

#sql("PROTOCOL_TREE_SUMMARY")
SELECT protocol_stack_id, SUM(sessions) as sessions,SUM(c2s_bytes) as c2s_bytes, SUM(c2s_pkts) as c2s_pkts, SUM(s2c_bytes) as s2c_bytes, SUM(s2c_pkts) as s2c_pkts FROM %s WHERE %s %s GROUP BY protocol_stack_id
#end

#sql("PROTOCOL_DATA_RATE_SUMMARY")
(SELECT TIME_FORMAT(MILLIS_TO_TIMESTAMP( 1000 * TIME_FLOOR_WITH_FILL(TIMESTAMP_TO_MILLIS(__time)/1000, '%s', 'zero')), 'yyyy-MM-dd HH:mm:ss') as stat_time, protocol_stack_id as type, sum(c2s_bytes + s2c_bytes) as bytes from %s where %s %s and protocol_stack_id = '%s' group by TIME_FORMAT(MILLIS_TO_TIMESTAMP( 1000 * TIME_FLOOR_WITH_FILL(TIMESTAMP_TO_MILLIS(__time)/1000, '%s', 'zero')), 'yyyy-MM-dd HH:mm:ss'), protocol_stack_id order by stat_time asc)
union all
(SELECT TIME_FORMAT(MILLIS_TO_TIMESTAMP( 1000 * TIME_FLOOR_WITH_FILL(TIMESTAMP_TO_MILLIS(__time)/1000, '%s', 'zero')), 'yyyy-MM-dd HH:mm:ss') as stat_time, protocol_stack_id as type, sum(c2s_bytes + s2c_bytes) as bytes from %s where %s %s and protocol_stack_id like CONCAT('%s','.%s') and LENGTH(protocol_stack_id) = LENGTH(REPLACE(protocol_stack_id,'.','')) + 1 + %s group by TIME_FORMAT(MILLIS_TO_TIMESTAMP( 1000 * TIME_FLOOR_WITH_FILL(TIMESTAMP_TO_MILLIS(__time)/1000, '%s', 'zero')), 'yyyy-MM-dd HH:mm:ss'), protocol_stack_id order by stat_time asc)
#end

#sql("APP_DATA_SUMMARY")
SELECT app_name as app_name, SUM(bytes) as bytes, SUM(sessions) as sessions, MAX(bytes_rate) as max_rate, AVG(bytes_rate) as avg_rate FROM (SELECT app_name as app_name, SUM(sessions) as sessions, SUM(c2s_bytes + s2c_bytes) as bytes, SUM(c2s_bytes + s2c_bytes) * 8 / %s as bytes_rate FROM %s WHERE %s %s AND notEmpty(app_name) GROUP BY app_name, time_floor(TIMESTAMP_TO_MILLIS(__time), '%s') ORDER BY bytes DESC ) GROUP BY app_name ORDER BY bytes DESC %s
#end

#sql("APP_INTERNAL_IP_SUMMARY")
SELECT IF(bitAnd(flags, 8) = 8 AND bitAnd(flags, 16) != 16, client_ip, IF(bitAnd(flags, 8) != 8 AND bitAnd(flags, 16) = 16, server_ip, arrayJoin(splitByChar('_',concat(client_ip,'_',server_ip))))) AS ip, SUM(sent_bytes + received_bytes) AS bytes FROM %s WHERE %s %s AND (bitAnd(flags, 8) = 8 OR bitAnd(flags, 16) = 16) GROUP BY ip ORDER BY bytes DESC %s
#end

#sql("APP_DATA_RATE_SUMMARY")
SELECT TIME_FORMAT(MILLIS_TO_TIMESTAMP( 1000 * TIME_FLOOR_WITH_FILL(TIMESTAMP_TO_MILLIS(__time)/ 1000, '%s', 'zero')), 'yyyy-MM-dd HH:mm:ss') as stat_time, app_name , sum(c2s_bytes + s2c_bytes) as bytes from %s where %s %s group by TIME_FORMAT(MILLIS_TO_TIMESTAMP( 1000 * TIME_FLOOR_WITH_FILL(TIMESTAMP_TO_MILLIS(__time)/ 1000, '%s', 'zero')), 'yyyy-MM-dd HH:mm:ss'), app_name order by stat_time asc
#end

#sql("APP_TRAFFIC_SUMMARY")
SELECT app_name, SUM(sessions) as sessions, sum(c2s_bytes + s2c_bytes) as bytes, sum(s2c_bytes) as received_bytes, sum(c2s_bytes) as sent_bytes, sum(c2s_pkts + s2c_pkts) as packets, sum(c2s_pkts) as sent_packets, sum(s2c_pkts) as received_packets from %s where %s %s group by app_name
#end

#sql("ENTITY_ACTIVE_CLIENT_IP")
select client_ip as client_ip, vsys_id as vsys_id from %s where %s %s AND notEmpty(client_ip) GROUP BY client_ip, app, vsys_id ORDER BY COUNT(session_id) DESC LIMIT %s
#end

#sql("ENTITY_UDP_SESSION")
select server_ip as server_ip,vsys_id as vsys_id ,COUNT(session_id) as sessions from %s where %s AND (common_l4_protocol IN ('IPv4_UDP' , 'IPv6_UDP')) and server_port in (53 , 443) group by server_ip,vsys_id order by sessions desc limit %s
#end

#sql("ENTITY_UDP_UNIQ_CLIENT_IPS")
select server_ip as server_ip, vsys_id as vsys_id  from %s where %s AND (common_l4_protocol IN ('IPv4_UDP' , 'IPv6_UDP')) group by server_ip,vsys_id order by COUNT(DISTINCT(client_ip)) desc limit %s
#end

#sql("ENTITY_TCP_SESSION")
select server_ip as server_ip,vsys_id as vsys_id ,COUNT(session_id) as sessions from %s where %s AND (common_l4_protocol IN ('IPv4_TCP' , 'IPv6_TCP')) group by server_ip,vsys_id order by sessions desc limit %s
#end

#sql("ENTITY_TCP_UNIQ_CLIENT_IPS")
select server_ip as server_ip, vsys_id as vsys_id from %s where %s AND (common_l4_protocol IN ('IPv4_TCP' , 'IPv6_TCP')) group by server_ip, vsys_id order by COUNT(DISTINCT(client_ip)) desc limit %s
#end

#sql("TOP_ENTITY_TCP_UNIQ_CLIENT_IPS")
select COUNT(DISTINCT(client_ip)) as client_ips from %s where %s AND (common_l4_protocol IN ('IPv4_TCP' , 'IPv6_TCP')) AND server_ip in (select server_ip from %s as cc where %s AND (common_l4_protocol IN ('IPv4_TCP' , 'IPv6_TCP')) group by server_ip order by COUNT(DISTINCT(client_ip)) desc limit %s)
#end

#sql("TOP_ENTITY_UDP_UNIQ_CLIENT_IPS")
select COUNT(DISTINCT(client_ip)) as client_ips from %s where %s AND (common_l4_protocol IN ('IPv4_UDP' , 'IPv6_UDP')) AND server_ip in (select server_ip from %s as cc where %s AND (common_l4_protocol IN ('IPv4_UDP' , 'IPv6_UDP')) group by server_ip order by COUNT(DISTINCT(client_ip)) desc limit %s)
#end

#sql("TOTAL_ENTITY_UDP_SESSION")
select COUNT(session_id) as sessions from %s where %s AND (common_l4_protocol IN ('IPv4_UDP' , 'IPv6_UDP')) and server_port in (53 , 443) limit 1
#end

#sql("TOTAL_ENTITY_UDP_UNIQ_CLIENT_IPS")
select COUNT(DISTINCT(client_ip)) as uniq_client_ips from %s where %s AND (common_l4_protocol IN ('IPv4_UDP' , 'IPv6_UDP')) limit 1
#end

#sql("TOTAL_ENTITY_TCP_SESSION")
select COUNT(session_id) as sessions from %s where %s AND (common_l4_protocol IN ('IPv4_TCP' , 'IPv6_TCP')) limit 1
#end

#sql("TOTAL_ENTITY_TCP_UNIQ_CLIENT_IPS")
select COUNT(DISTINCT(client_ip)) as uniq_client_ips  from %s where %s AND (common_l4_protocol IN ('IPv4_TCP' , 'IPv6_TCP')) limit 1
#end

#sql("ENTITY_TOP_SNI")
select ssl_sni, SUM(sent_bytes + received_bytes) AS bytes, COUNT(session_id) AS sessions,vsys_id as vsys_id from %s where %s and notEmpty(ssl_sni) and decoded_as = 'SSL' group by ssl_sni,vsys_id order by  COUNT(session_id) desc limit %s
#end

#sql("ENTITY_SNI_TOTAL")
select SUM(sent_bytes + received_bytes) AS bytes, COUNT(session_id) AS sessions from %s where %s and decoded_as = 'SSL' limit 1
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

#sql("TABLE_INDEX_KEY")
SELECT any(splitByString(', ',sorting_key)) AS index_key FROM tables_cluster where name = '%s_local' AND "database" = '%s'
#end

#sql("UNSTRUCTURED_DATA_PATH")
SELECT log_id AS log_id,%s AS file_path FROM %s where %s AND notEmpty(%s) ORDER BY recv_time DESC LIMIT %s
#end

#sql("UNSTRUCTURED_DATA_PATH_OLD")
SELECT common_log_id AS log_id,%s AS file_path FROM %s where %s AND notEmpty(%s) ORDER BY common_recv_time DESC LIMIT %s
#end

#sql("SQL_DATASETS")
SELECT id, identifier_name, category, execute_engine, sql_text, description, created_time, modified_time FROM sql_dataset %s ORDER BY modified_time DESC
#end

#sql("SQL_DATASETS_CATEGORY")
SELECT category FROM sql_dataset GROUP BY category
