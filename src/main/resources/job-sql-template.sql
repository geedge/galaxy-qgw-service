#sql("JOB_STATUS")
SELECT is_canceled AS is_canceled, is_error AS is_error, last_query_time AS last_query_time FROM job_result WHERE ROWKEY = '%s' LIMIT 1
#end
#sql("JOB_RESULT_ALL_COLUMN")
SELECT result.* FROM job_result LIMIT 1
#end
#sql("JOB_ADD_COLUMN")
ALTER TABLE "tsg_galaxy"."job_result" ADD "result"."%s" VARCHAR
#end
#sql("JOB_INIT")
UPSERT INTO "tsg_galaxy"."job_result"( ROWKEY, "detail"."is_done", "detail"."done_progress", "detail"."is_canceled", "detail"."is_error", "detail"."last_query_time", "detail"."discovery_field", "detail"."count", "detail"."duration_time") VALUES('%s', %s, %s, %s, %s, %s, '%s', %s, %s)
#end
#sql("JOB_LOG_COUNT")
SELECT count(1) AS count FROM %s %s LIMIT 1
#end
#sql("JOB_TTL")
SELECT detail.last_query_time as stat_time FROM job_result WHERE ROWKEY = '%s' LIMIT 1
#end
#sql("JOB_UPSET_PROGRESS")
UPSERT INTO "tsg_galaxy"."job_result"( ROWKEY, "detail"."is_done", "detail"."done_progress", "detail"."duration_time") VALUES('%s', %s, %s, %s)
#end
#sql("JOB_TOPK")
SELECT %s AS value, count(1) AS count FROM %s %s GROUP BY value ORDER BY count DESC limit 100
#end
#sql("JOB_UPSERT_RESULT")
UPSERT into "tsg_galaxy"."job_result" (ROWKEY, "result"."%s") values('%s', '%s')
#end
#sql("JOB_UPSERT_DETAIL")
UPSERT into "tsg_galaxy"."job_result" (ROWKEY, "detail"."%s") values('%s', %s)
#end
#sql("JOB_RESULT")
SELECT is_done AS is_done, done_progress AS done_progress, duration_time AS duration_time, is_canceled AS is_canceled, is_error AS is_error, discovery_field AS discovery_field, count AS count, result.* FROM job_result WHERE ROWKEY = '%s' LIMIT 1
#end
#sql("JOB_RESULT_QUERY")
UPSERT INTO "tsg_galaxy"."job_result"( ROWKEY, "detail"."last_query_time") VALUES( '%s', %s)
#end
#sql("UPDATE_IS_CANCELED")
UPSERT INTO "tsg_galaxy"."job_result"( ROWKEY, "detail"."last_query_time", "detail"."is_canceled") VALUES( '%s', %s, %s)
#end
#sql("JOB_DELETE")
DELETE FROM "tsg_galaxy"."job_result" WHERE ROWKEY = '%s'
#end
#sql("SYNTAX_VALIDATION")
SELECT %s FROM %s WHERE %s LIMIT 1
#end