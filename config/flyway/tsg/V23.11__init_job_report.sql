/*
* 23.11 init job_report sql
*/
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for job_report
-- ----------------------------
DROP TABLE IF EXISTS `job_report`;
CREATE TABLE `job_report` (
                   `result_id` int(11) NOT NULL AUTO_INCREMENT,
                   `result_name` varchar(512) NOT NULL COMMENT '任务名称',
                   `job_id` int(11) NOT NULL COMMENT '任务id',
                   `chart_id` int(11) NOT NULL,
                   `job_uuid` varchar(128) NOT NULL,
                   `start_time` datetime NOT NULL COMMENT '统计开始时间',
                   `end_time` datetime NOT NULL COMMENT '统计结束时间',
                   `query_sql` text NOT NULL COMMENT '统计sql',
                   `status` int(11) NOT NULL COMMENT '状态',
                   `excute_time` int(11) DEFAULT NULL COMMENT '预计执行时间',
                   `excute_row` bigint(20) DEFAULT NULL COMMENT '预计统计记录数',
                   `excute_process` int(11) NOT NULL COMMENT '执行进度百分比',
                   `result_rows` bigint(20) NOT NULL,
                   `excute_detail` varchar(2000) NOT NULL COMMENT '执行详情',
                   `is_send_notice` int(11) NOT NULL COMMENT '是否已发送邮件通知',
                   `create_time` datetime NOT NULL COMMENT '生成记录的时间',
                   `op_time` datetime NOT NULL,
                   `op_user` int(11) NOT NULL,
                   `issued_time` datetime DEFAULT NULL COMMENT '下发时间',
                   `time_granularity` int(11) NOT NULL DEFAULT 0 COMMENT '统计时间粒度',
                   `is_valid` int(2) NOT NULL DEFAULT 1 COMMENT '是否启用',
                   `is_display_traffic_trend` int(2) NOT NULL DEFAULT 0 COMMENT '报表任务结果是否缺省显示流量趋势图',
                   `time_zone` varchar(64) NOT NULL COMMENT '时区',
                   PRIMARY KEY (`result_id`) USING BTREE,
                   KEY `index_op_time` (`op_time`) USING BTREE,
                   KEY `index_job_id` (`job_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=106337 DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;