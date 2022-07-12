--Q01.Top10 country
SELECT COUNT(*) AS count, ip_location_country AS name FROM entity_info WHERE entity_type = 'ip' AND update_time >= @start AND update_time < @end GROUP BY name ORDER BY count DESC LIMIT 10
--Q02.Top10 app_category
SELECT COUNT(*) AS count, app_category AS name FROM entity_info WHERE entity_type = 'app' AND update_time >= @start AND update_time < @end GROUP BY name ORDER BY count DESC LIMIT 10
--Q03.Top10 domain_category_group
SELECT COUNT(*) AS count, domain_category_group AS name FROM entity_info WHERE entity_type = 'domain' AND update_time >= @start AND update_time < @end GROUP BY name ORDER BY count DESC LIMIT 10
--Q04.Security Events Severity distribution map
SELECT HISTOGRAM(CAST(start_time * 1000 AS DATETIME), INTERVAL 864 SECOND) AS stat_time, event_severity AS event_severity , COUNT(*) AS count FROM security_event WHERE start_time >= @start AND start_time < @end GROUP BY stat_time, event_severity ORDER BY stat_time ASC
--Q05.Security Events Active Attacker
SELECT offender_ip AS offender_ip, COUNT(*) AS count FROM security_event WHERE start_time >= @start AND start_time < @end GROUP BY offender_ip ORDER BY count DESC
--Q06.Performance Events Severity distribution map
SELECT HISTOGRAM(CAST(start_time * 1000 AS DATETIME), INTERVAL 864 SECOND) AS stat_time, event_severity AS event_severity , COUNT(*) AS count FROM performance_event WHERE start_time >= @start AND start_time < @end GROUP BY stat_time, event_severity ORDER BY stat_time ASC
--Q07.Performance Events Active entity
SELECT IIF(entity_type = 'domain', domain, IIF(entity_type = 'ip', server_ip, app_name)) AS name , COUNT(*) AS count FROM performance_event WHERE start_time >= @start AND start_time < @end GROUP BY entity_type, app_name, domain, server_ip ORDER BY count DESC
--Q08.Full-Text Search
SELECT count(*) AS count FROM entity_info WHERE 1 = 1 AND update_time >= @start AND update_time < @end AND QUERY('entity_type:baidu ip_addr:baidu ip_location_country:baidu ip_location_province:baidu ip_location_city:baidu ip_asn:baidu dns_server_role:baidu dns_server_org:baidu dns_server_os:baidu dns_server_software:baidu domain_name:baidu domain_category:baidu domain_category_group:baidu domain_reputation_level:baidu domain_whois_email:baidu domain_whois_name_servers:baidu domain_whois_registrar:baidu domain_whois_org:baidu domain_whois_address:baidu domain_whois_city:baidu domain_whois_state:baidu domain_whois_country:baidu domain_icp_owner:baidu domain_icp_company_name:baidu domain_icp_company_type:baidu domain_icp_site_license:baidu domain_icp_site_name:baidu app_name:baidu app_id:baidu app_category:baidu app_subcategory:baidu app_risk:baidu app_description:baidu app_longname:baidu app_technology:baidu ')