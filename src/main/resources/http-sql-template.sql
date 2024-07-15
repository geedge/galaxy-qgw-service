#sql("SYSTEM_STORAGE_QUOTA")
SELECT log_type AS type, SUM(used_size) AS used_size, SUM(total_allocated_size) AS total_allocated_size, MIN(since_time) AS since_time FROM (SELECT log_type, used_size, total_allocated_size, DATE(FROM_UNIXTIME(since_time)) AS since_time, ROW_NUMBER() OVER (PARTITION BY log_type ORDER BY generated_time DESC) AS row_num FROM sys_storage_event WHERE generated_time >= UNIX_TIMESTAMP(DATE_SUB(NOW(), INTERVAL 1 WEEK)) ) AS subquery WHERE row_num = 1 GROUP BY log_type
#end

#sql("SYSTEM_DAILY_TREND_OF_STORAGE")
SELECT DATE(FROM_UNIXTIME(generated_time)) AS stat_time, log_type AS type, sum( bytes ) AS used_size FROM sys_storage_event WHERE generated_time >= UNIX_TIMESTAMP('%s') AND generated_time < UNIX_TIMESTAMP('%s') GROUP BY stat_time, type ORDER BY stat_time ASC
#end

#sql("SCHEMA_STORAGE_SIZE")
SELECT name AS field, IFNULL(SUM(data_compressed_bytes), 0) AS bytes FROM columns_cluster WHERE "table" = '%s_local' AND  "database" = '%s' GROUP BY name ORDER BY bytes DESC
#end

#sql("TABLE_TTL")
SELECT name, engine_full AS table_ttl FROM tables WHERE name ='%s_local' AND  "database" = '%s' AND "engine" ='MergeTree'
#end

#sql("TABLE_INDEX_KEY")
SELECT any(splitByString(', ',sorting_key)) AS index_key FROM tables_cluster where name = '%s_local' AND "database" = '%s'
#end

#sql("SQL_DATASETS")
SELECT id, identifier_name, category, backend_engine, type, template, description, generated_time, last_update_time FROM dataset %s ORDER BY last_update_time DESC
#end
