#sql("JOB_STATUS")
SELECT is_canceled AS isCanceled FROM job_result WHERE ROWKEY = '%s' LIMIT 1
#end
#sql("JOB_RESULT_ALL_COLUMN")
SELECT result.* FROM job_result LIMIT 1
#end
#sql("JOB_ADD_COLUMN")
ALTER TABLE "tsg_galaxy"."job_result" ADD "result"."%s" VARCHAR
#end
#sql("JOB_INIT")
UPSERT INTO "tsg_galaxy"."job_result"( ROWKEY, "detail"."is_done", "detail"."done_progress", "detail"."is_canceled", "detail"."last_query_time", "result"."field") VALUES('%s', %s, %s, %s, %s, '%s')
#end
#sql("JOB_LOG_COUNT")
SELECT count(1) AS count FROM %s %s LIMIT 1
#end
#sql("JOB_TTL")
SELECT detail.last_query_time as stat_time FROM job_result WHERE ROWKEY = '%s' LIMIT 1
#end
#sql("JOB_SET_CANCELED")
UPSERT INTO "job_result"( ROWKEY, "detail"."is_canceled") VALUES( '%s', %s)
#end
#sql("JOB_UPSET_PROGRESS")
UPSERT INTO "tsg_galaxy"."job_result"( ROWKEY, "detail"."is_done", "detail"."done_progress") VALUES('%s', %s, %s)
#end
#sql("JOB_TOPK")
SELECT %s AS value, count(1) AS count FROM %s %s GROUP BY value ORDER BY count DESC limit 100
#end
#sql("JOB_UPSERT_RESULT")
UPSERT into "tsg_galaxy"."job_result" (ROWKEY, "result"."%s") values('%s', '%s')
#end
#sql("JOB_RESULT")
SELECT is_done AS is_done, done_progress AS done_progress, is_canceled AS is_canceled, result.* FROM job_result WHERE ROWKEY = '%s' LIMIT 1
#end
#sql("JOB_RESULT_QUERY")
UPSERT INTO "tsg_galaxy"."job_result"( ROWKEY, "detail"."last_query_time") VALUES( '%s', %s)
#end
#sql("UPDATE_IS_CANCELED")
UPSERT INTO "tsg_galaxy"."job_result"( ROWKEY, "detail"."last_query_time", "detail"."is_canceled") VALUES( '%s', %s, %s)
#end
#sql("SYNTAX_VALIDATION")
SELECT %s FROM %s WHERE %s LIMIT 1
#end