#sql("JOB_INIT")
UPSERT INTO "tsg_galaxy"."job_result"( ROWKEY, "detail"."is_done", "detail"."done_progress", "detail"."is_canceled", "detail"."is_failed", "detail"."last_query_time", "detail"."job_property", "detail"."count", "detail"."duration_time") VALUES('%s', %s, %s, %s, %s, %s, '%s', %s, %s)
#end
#sql("JOB_DETAIL")
SELECT detail.* FROM job_result WHERE ROWKEY = '%s' LIMIT 1
#end
#sql("JOB_ADD_COLUMN")
ALTER TABLE "tsg_galaxy"."job_result" ADD "%s"."%s" VARCHAR
#end
#sql("JOB_GET_FIELD_DISCOVERY_ALL_COLUMN")
SELECT field_discovery.* FROM job_result LIMIT 1
#end
#sql("JOB_LOG_COUNT")
SELECT count(*) AS logCount, %s AS totalMetric FROM %s %s LIMIT 1
#end
#sql("JOB_UPDATE_PROGRESS")
UPSERT INTO "tsg_galaxy"."job_result"( ROWKEY, "detail"."is_done", "detail"."done_progress", "detail"."duration_time") VALUES('%s', %s, %s, %s)
#end
#sql("JOB_TOPK_DEFAULT")
SELECT %s AS value, %s(%s) AS %s%s FROM %s %s GROUP BY value ORDER BY %s%s DESC limit %s
#end
#sql("JOB_TOPK_ARRAY")
SELECT value, %s(metric) AS %s%s FROM (SELECT arrayJoin(items) AS value, metric FROM (SELECT %s AS items, %s AS metric FROM %s WHERE notEmpty(%s) %s GROUP BY items )) GROUP BY value ORDER BY %s%s DESC LIMIT %s
#end
#sql("JOB_TOPK_BIT")
SELECT value, %s(metric) AS %s%s FROM (SELECT arrayJoin(items) AS value, metric FROM (SELECT bitmaskToArray(%s) AS items, %s AS metric FROM %s %s GROUP BY items )) GROUP BY value ORDER BY %s%s DESC LIMIT %s
#end
#sql("JOB_UPDATE_FIELD_VALUE_SINGLE_QUOTATION")
UPSERT into "tsg_galaxy"."job_result" (ROWKEY, "%s"."%s") values('%s', '%s')
#end
#sql("JOB_UPDATE_FIELD_VALUE")
UPSERT into "tsg_galaxy"."job_result" (ROWKEY, "%s"."%s") values('%s', %s)
#end
#sql("JOB_RESULT")
SELECT is_done AS is_done, done_progress AS done_progress, duration_time AS duration_time, is_canceled AS is_canceled, is_failed AS is_failed, job_property AS job_property, count AS count, field_discovery.*, long_term.* FROM job_result WHERE ROWKEY = '%s' LIMIT 1
#end
#sql("UPDATE_IS_CANCELED")
UPSERT INTO "tsg_galaxy"."job_result"( ROWKEY, "detail"."last_query_time", "detail"."is_canceled") VALUES( '%s', %s, %s)
#end
#sql("JOB_DELETE")
DELETE FROM "tsg_galaxy"."job_result" WHERE ROWKEY = '%s'
#end
#sql("FIELD_RESULT")
SELECT %s.%s AS value FROM job_result WHERE ROWKEY = '%s' LIMIT 1
#end
#sql("SYNTAX_VALIDATION")
SELECT %s FROM %s WHERE %s LIMIT 1
#end
#sql("SAVED_QUERY_JOB_INIT")
INSERT INTO saved_query_job (job_id, query_sql, state, done_progress, is_failed, result_message, elapsed, rows_read, bytes_read, result_rows, result_bytes, is_valid, start_time, end_time, last_update_time, generated_time ) VALUES ('%s', '%s', 'PENDING', 0, 0, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, NULL, '%s', '%s')
#end
#sql("SAVED_QUERY_JOB_STATUS")
SELECT state, done_progress, is_failed, is_valid, start_time, end_time, rows_read, query_sql, job_id FROM saved_query_job WHERE job_id IN ('%s') LIMIT %s
#end
#sql("SAVED_QUERY_JOB_SAME")
SELECT job_id AS id FROM saved_query_job WHERE is_valid = 1 AND state IN ( 'PENDING', 'RUNNING') AND is_failed = 0 AND query_sql = '%s' LIMIT 1
#end
#sql("SAVED_QUERY_JOB_UPDATE")
UPDATE saved_query_job SET %s = %s  WHERE job_id = '%s'
#end