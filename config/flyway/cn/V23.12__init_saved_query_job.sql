SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for saved_query_job
-- ----------------------------
CREATE TABLE IF NOT EXISTS `saved_query_job` (
                                   `job_id` varchar(128) NOT NULL COMMENT 'Job unique identifier',
                                   `query_sql` text NOT NULL COMMENT 'Query SQL',
                                   `state` varchar(16) NOT NULL COMMENT 'Enums: PENDING or RUNNING or DONE',
                                   `done_progress` float(3, 2) NOT NULL DEFAULT 0 COMMENT 'Progress: 0 - 1.0',
                                   `is_failed` int(2) NOT NULL DEFAULT 0 COMMENT 'Failed or not',
                                   `result_message` varchar(2000) DEFAULT NULL COMMENT 'Result message',
                                   `elapsed` int(11) DEFAULT NULL COMMENT 'cost time(ms)',
                                   `rows_read` bigint(20) DEFAULT NULL COMMENT 'Processed rows',
                                   `bytes_read` bigint(20) DEFAULT NULL COMMENT 'Processed bytes',
                                   `result_bytes` bigint(20) DEFAULT NULL COMMENT 'Result bytes',
                                   `result_rows` bigint(20) DEFAULT NULL COMMENT 'Result rows',
                                   `is_valid` int(2) NOT NULL DEFAULT 1 COMMENT 'Valid or not',
                                   `start_time` bigint(20) DEFAULT NULL COMMENT 'start time: from PENDING to RUNNING or DONE',
                                   `end_time` bigint(20) DEFAULT NULL COMMENT 'end time: state updated DONE',
                                   `last_update_time` bigint(20) NOT NULL COMMENT 'Last Update Time',
                                   `generated_time` bigint(20) NOT NULL COMMENT 'Generated Time',
                                   PRIMARY KEY (`job_id`) USING BTREE,
                                   KEY `index_job_id` (`job_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;
