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
SELECT count(1) AS count FROM %s %s LIMIT 1
#end
#sql("JOB_UPDATE_PROGRESS")
UPSERT INTO "tsg_galaxy"."job_result"( ROWKEY, "detail"."is_done", "detail"."done_progress", "detail"."duration_time") VALUES('%s', %s, %s, %s)
#end
#sql("JOB_TOPK_DEFAULT")
SELECT %s AS value, count(*) AS count FROM %s %s GROUP BY value ORDER BY count DESC limit 10000
#end
#sql("JOB_TOPK_ARRAY")
SELECT value, SUM(count) AS count FROM (SELECT arrayJoin(items) AS value, count FROM (SELECT %s AS items, count(*) AS count FROM %s WHERE notEmpty(%s) %s GROUP BY %s )) GROUP BY value ORDER BY count DESC LIMIT 10000
#end
#sql("JOB_TOPK_BIT")
SELECT value, SUM(count) AS count FROM (SELECT arrayJoin(items) AS value, count FROM (SELECT bitmaskToArray(%s) AS items, count(*) AS count FROM %s %s GROUP BY %s )) GROUP BY value ORDER BY count DESC LIMIT 10000
#end
#sql("JOB_UPDATE_FIELD_VALUE_SINGLE_QUOTATION")
UPSERT into "tsg_galaxy"."job_result" (ROWKEY, "%s"."%s") values('%s', '%s')
#end
#sql("JOB_UPDATE_FIELD_VALUE")
UPSERT into "tsg_galaxy"."job_result" (ROWKEY, "%s"."%s") values('%s', %s)
#end
#sql("JOB_RESULT")
SELECT is_done AS is_done, done_progress AS done_progress, duration_time AS duration_time, is_canceled AS is_canceled, is_failed AS is_failed, job_property AS job_property, count AS count, field_discovery.*, statistics.* FROM job_result WHERE ROWKEY = '%s' LIMIT 1
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
#sql("REPORT_INSERT")
insert into report_result ( status, result_rows, create_time, excute_row, is_valid, excute_detail, result_name, query_sql, excute_process, issued_time, op_time, start_time, excute_time, end_time, job_uuid, is_send_notice, job_id, chart_id, op_user, time_granularity, is_display_traffic_trend, time_zone) values ( 0, 0, '%s', 0, 1, 'COMMIT', 'OFFLINE JOB', '%s', 0, '%s', '%s', '%s', 0, '%s', '%s', 0, 0, 0, 0, 0, 0, '%s')
#end
#sql("REPORT_STATUS")
SELECT status, result_id, query_sql, excute_process, is_valid FROM report_result WHERE job_uuid = '%s' LIMIT 1
#end
#sql("REPORT_SAME")
SELECT job_uuid AS id FROM report_result WHERE is_valid = 1 AND status IN ( 0, 1) AND query_sql = '%s' LIMIT 1
#end
#sql("REPORT_UPDATE")
UPDATE report_result SET %s = %s  WHERE job_uuid = '%s'
#end