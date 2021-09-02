--Q01.CK DateTime
select toDateTime(common_recv_time) as common_recv_time from session_record where common_recv_time >= toDateTime(@start) and common_recv_time< toDateTime(@end) limit 20
--Q02.Standard DateTime
select FROM_UNIXTIME(common_recv_time) as common_recv_time from session_record where common_recv_time >= UNIX_TIMESTAMP(@start) and common_recv_time< UNIX_TIMESTAMP(@end) limit 20
--Q03.count(1)
select count(1) from session_record where common_recv_time >= toDateTime(@start) and common_recv_time< toDateTime(@end)
--Q04.count(*)
select count(*) from session_record where common_recv_time >= toDateTime(@start) and common_recv_time< toDateTime(@end)
--Q05.UDF APPROX_COUNT_DISTINCT_DS_HLL
SELECT policy_id, APPROX_COUNT_DISTINCT_DS_HLL(isp) as num  FROM proxy_event_hits_log where  __time >= @start and __time < @end and policy_id=0 group by policy_id
--Q06.UDF TIME_FLOOR_WITH_FILL
select TIME_FLOOR_WITH_FILL(common_recv_time,'PT5M','previous') as stat_time from session_record where  common_recv_time > @start and common_recv_time < @end  group by stat_time
--Q07.UDF GEO IP
select IP_TO_GEO(common_client_ip) as geo,IP_TO_CITY(common_server_ip) as city,IP_TO_COUNTRY(common_server_ip) as country from session_record limit 10
--Q08.Special characters
select * from session_record where (common_protocol_label ='/$' or common_client_ip like'%') limit 10
--Q09.Federation Query
select * from (select FROM_UNIXTIME(TIME_FLOOR_WITH_FILL(common_recv_time,'PT5M','zero')) as stat_time from session_record where common_recv_time >= toDateTime(@start) and common_recv_time< toDateTime(@end) group by stat_time order by stat_time asc)
--Q10.Catalog Database
select * from tsg_galaxy_v3.session_record where common_recv_time >= toDateTime(@start) and common_recv_time< toDateTime(@end) limit 20
--Q11.Session Record Logs
select * from session_record where common_recv_time >= toDateTime(@start) and common_recv_time< toDateTime(@end)  AND @common_filter order by common_recv_time desc limit 20
--Q12.Live Session Record Logs
select * from interim_session_record where common_recv_time >= toDateTime(@start) and common_recv_time< toDateTime(@end)  AND @common_filter order by common_recv_time desc limit 20
--Q13.Transaction  Record Logs
select * from transaction_record where common_recv_time >= toDateTime(@start) and common_recv_time< toDateTime(@end)  order by common_recv_time desc limit 20
--Q14.Security Event Logs
select * from security_event where common_recv_time >= UNIX_TIMESTAMP(@start) and common_recv_time< UNIX_TIMESTAMP(@end) AND @common_filter order by common_recv_time desc limit 0,20
--Q15.Proxy Event Logs
select * from proxy_event where common_recv_time >= UNIX_TIMESTAMP(@start) and common_recv_time< UNIX_TIMESTAMP(@end) order by common_recv_time desc limit 0,20
--Q16.Radius Record Logs
select * from radius_record where common_recv_time >= UNIX_TIMESTAMP(@start) and common_recv_time< UNIX_TIMESTAMP(@end) order by common_recv_time desc limit 0,20
--Q17.GTPC Record Logs
select * from gtpc_record where common_recv_time >= UNIX_TIMESTAMP(@start) and common_recv_time< UNIX_TIMESTAMP(@end) order by common_recv_time desc limit 0,20
--Q18.Security Event Logs with fields
select FROM_UNIXTIME(common_recv_time) as common_recv_time,common_log_id,common_policy_id,common_subscriber_id,common_client_ip,common_client_port,common_l4_protocol,common_address_type,common_server_ip,common_server_port,common_action,common_direction,common_sled_ip,common_client_location,common_client_asn,common_server_location,common_server_asn,common_c2s_pkt_num,common_s2c_pkt_num,common_c2s_byte_num,common_s2c_byte_num,common_schema_type,common_sub_action,common_device_id, FROM_UNIXTIME(common_start_time) as common_start_time, FROM_UNIXTIME(common_end_time) as common_end_time,common_establish_latency_ms,common_con_duration_ms,common_stream_dir,common_stream_trace_id,http_url,http_host,http_domain,http_request_body,http_response_body,http_cookie,http_referer,http_user_agent,http_content_length,http_content_type,http_set_cookie,http_version,http_response_latency_ms,http_action_file_size,http_session_duration_ms,mail_protocol_type,mail_account,mail_from_cmd,mail_to_cmd,mail_from,mail_to,mail_cc,mail_bcc,mail_subject,mail_attachment_name,mail_eml_file,dns_message_id,dns_qr,dns_opcode,dns_aa,dns_tc,dns_rd,dns_ra,dns_rcode,dns_qdcount,dns_ancount,dns_nscount,dns_arcount,dns_qname,dns_qtype,dns_qclass,dns_cname,dns_sub,dns_rr,ssl_sni,ssl_san,ssl_cn,ssl_pinningst,ssl_intercept_state,ssl_server_side_latency,ssl_client_side_latency,ssl_server_side_version,ssl_client_side_version,ssl_cert_verify,ssl_error,quic_version,quic_sni,quic_user_agent,ftp_account,ftp_url,ftp_content  from security_event where  common_recv_time  >= @start and common_recv_time < @end  order by  common_recv_time desc  limit 10000
--Q19.Radius ON/OFF Logs For Frame IP
select framed_ip, arraySlice(groupUniqArray(concat(toString(event_timestamp),':', if(acct_status_type=1,'start','stop'))),1,100000) as timeseries from radius_onff_log where event_timestamp >=toDateTime(@start) and event_timestamp <toDateTime(@end) group by framed_ip limit 20
--Q20.Radius ON/OFF Logs For Account
select account, arraySlice(groupUniqArray(concat(toString(event_timestamp),':', if(acct_status_type=1,'start','stop'))),1,100000) as timeseries from radius_onff_log where event_timestamp >= @start and event_timestamp < @end  group by account
--Q21.Radius ON/OFF Logs total Account number
select count(distinct(framed_ip)) as active_ip_num , sum(acct_session_time) as online_duration from (select any(framed_ip) as framed_ip ,max(acct_session_time) as acct_session_time from radius_onff_log where account='000jS' and event_timestamp >= @start and event_timestamp < @end  group by acct_session_id)
--Q22.Radius ON/OFF Logs Account Access Detail
select max(if(acct_status_type=1,event_timestamp,0)) as start_time,max(if(acct_status_type=2,event_timestamp,0)) as end_time, any(framed_ip) as ip,max(acct_session_time) as online_duration  from radius_onff_log where event_timestamp >= @start and event_timestamp < @end  group by acct_session_id  order by start_time desc limit 200
--Q23.Report for Client IP
select common_client_ip, count(*) as sessions from session_record where common_recv_time>= toStartOfDay(toDateTime(@start))-604800 and common_recv_time< toStartOfDay(toDateTime(@end))  group by common_client_ip order by sessions desc limit 0,100
--Q24.Report for Server IP
select common_server_ip, count(*) as sessions from session_record where common_recv_time>= toStartOfDay(toDateTime(@start))-604800 and common_recv_time< toStartOfDay(toDateTime(@start))  group by common_server_ip order by sessions desc limit 0,100
--Q25.Report for SSL SNI
select ssl_sni, count(*) as sessions from session_record where common_recv_time>= toStartOfDay(toDateTime(@start))-604800 and common_recv_time< toStartOfDay(toDateTime(@start))  group by ssl_sni order by sessions desc limit 0,100
--Q26.Report for SSL APP
select common_app_label as applicaiton, count(*) as sessions from session_record where common_recv_time>= toStartOfDay(toDateTime(@start))-604800 and common_recv_time< toStartOfDay(toDateTime(@start))  group by applicaiton order by sessions desc limit 0,100
--Q27.Report for Domains
select http_domain AS domain,SUM(coalesce(common_c2s_byte_num, 0)) AS sent_bytes,SUM(coalesce(common_s2c_byte_num, 0)) AS received_bytes,SUM(coalesce(common_c2s_byte_num, 0)+coalesce(common_s2c_byte_num, 0)) AS bytes FROM session_record WHERE common_recv_time >= toStartOfDay(toDateTime(@start))-86400 AND common_recv_time < toStartOfDay(toDateTime(@start)) and notEmpty(domain)  GROUP BY domain ORDER BY bytes DESC LIMIT 100
--Q28.Report for Domains with unique Client IP
select toDateTime(intDiv(toUInt32(toDateTime(toDateTime(common_recv_time))), 300)*300) as stat_time, http_domain, uniq (common_client_ip) as nums  from session_record where common_recv_time >= toStartOfDay(toDateTime(@start))-86400 AND common_recv_time < toStartOfDay(toDateTime(@start))  and http_domain in (select http_domain from session_record where common_recv_time >= toStartOfDay(toDateTime(@start))-86400 AND common_recv_time < toStartOfDay(toDateTime(@start))  and notEmpty(http_domain) group by http_domain order by SUM(coalesce(common_c2s_byte_num, 0)+coalesce(common_s2c_byte_num, 0)) desc limit 10 ) group by toDateTime(intDiv(toUInt32(toDateTime(toDateTime(common_recv_time))), 300)*300), http_domain order by stat_time asc limit 500
--Q29. Report for HTTP Host
SELECT http_host as host, SUM(coalesce(common_c2s_byte_num, 0)) AS sent_bytes,SUM(coalesce(common_s2c_byte_num, 0)) AS received_bytes,SUM(coalesce(common_c2s_byte_num, 0)+coalesce(common_s2c_byte_num, 0)) AS bytes FROM session_record WHERE common_recv_time>= toStartOfDay(toDateTime(@start))-604800 and common_recv_time< toStartOfDay(toDateTime(@start))  and notEmpty(http_host) GROUP BY host ORDER BY bytes DESC limit 100 union all SELECT  'totals'  as host, SUM(coalesce(common_c2s_byte_num, 0)) AS sent_bytes, SUM(coalesce(common_s2c_byte_num, 0)) AS received_bytes, SUM(coalesce(common_c2s_byte_num, 0)+coalesce(common_s2c_byte_num, 0)) AS bytes from session_record where common_recv_time>= toStartOfDay(toDateTime(@start))-604800 and common_recv_time< toStartOfDay(toDateTime(@start))  and notEmpty(http_host)
--Q30.Report for HTTP/HTTPS URLS with Sessions
SELECT http_url AS url,count(*) AS sessions FROM proxy_event WHERE common_recv_time >= toStartOfDay(toDateTime(@start))-86400 AND common_recv_time < toStartOfDay(toDateTime(@start)) and notEmpty(http_url) GROUP BY url ORDER BY sessions DESC LIMIT 100
--Q31.Report for HTTP/HTTPS URLS with UNIQUE Client IP
select toDateTime(intDiv(toUInt32(toDateTime(toDateTime(common_recv_time))), 300)*300) as stat_time, http_url, count(distinct(common_client_ip)) as nums  from proxy_event where common_recv_time >= toStartOfDay(toDateTime(@start))-86400 AND common_recv_time < toStartOfDay(toDateTime(@start)) and http_url IN (select http_url from proxy_event where common_recv_time >= toStartOfDay(toDateTime(@start))-86400 AND common_recv_time < toStartOfDay(toDateTime(@start)) and notEmpty(http_url)  group by http_url order by count(*) desc limit 10 ) group by toDateTime(intDiv(toUInt32(toDateTime(toDateTime(common_recv_time))), 300)*300), http_url order by stat_time asc limit 500
--Q32.Report for Subscriber ID with Sessions
select common_subscriber_id as user, count(*) as sessions from session_record where common_recv_time>= toStartOfDay(toDateTime(@start))-604800 and common_recv_time< toStartOfDay(toDateTime(@start))  and notEmpty(user)   group by common_subscriber_id order by sessions desc limit 0,100
--Q33.Report for Subscriber ID with Bandwidth
SELECT common_subscriber_id as user,SUM(coalesce(common_c2s_byte_num, 0)) AS sent_bytes,SUM(coalesce(common_s2c_byte_num, 0)) AS received_bytes,SUM(coalesce(common_c2s_byte_num, 0)+coalesce(common_s2c_byte_num, 0)) AS bytes FROM session_record WHERE common_recv_time>= toStartOfDay(toDateTime(@start))-604800 and common_recv_time< toStartOfDay(toDateTime(@start)) and notEmpty(user)   GROUP BY user ORDER BY bytes  DESC LIMIT 100
--Q34.Report Unique Endpoints
select uniq(common_client_ip) as "Client IP",uniq(common_server_ip) as "Server IP",uniq(common_internal_ip) as "Internal IP",uniq(common_external_ip) as "External IP",uniq(http_domain) as "Domain",uniq(ssl_sni) as "SNI"  from session_record where common_recv_time>= toStartOfDay(toDateTime(@start))-604800 and common_recv_time< toStartOfDay(toDateTime(@start))

