/*
 SQL Datasets
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
-- ----------------------------
-- Table structure for sql_dataset
-- ----------------------------
DROP TABLE IF EXISTS `sql_dataset`;
CREATE TABLE `sql_dataset` (
                               `id` int(64) NOT NULL AUTO_INCREMENT,
                               `identifier_name` varchar(128) NOT NULL,
                               `category` varchar(128) DEFAULT NULL,
                               `execute_engine` varchar(16) NOT NULL DEFAULT 'qgw' COMMENT 'qgw, clickhouse, druid, elasticsearch, hbase',
                               `sql_text` text NOT NULL,
                               `description` varchar(512) DEFAULT NULL,
                               `created_time` datetime NOT NULL COMMENT 'UTC+0',
                               `modified_time` datetime NOT NULL COMMENT 'UTC+0',
                               PRIMARY KEY (`id`) USING BTREE,
                               UNIQUE(identifier_name)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

-- ----------------------------
-- Records of sql_dataset
-- ----------------------------
BEGIN;
INSERT INTO `sql_dataset` (`id`, `identifier_name`, `category`, `execute_engine`, `sql_text`, `description`, `created_time`, `modified_time`) VALUES (1, 'top-security-rule-hits', 'Default dashboard', 'qgw', 'SELECT rule_id, action, SUM(hit_count) AS hits FROM security_rule_hits WHERE __time >= ''${start}'' AND __time < ''${end}'' AND vsys_id IN ( ${vsys_id} ) AND ${filter} GROUP BY rule_id, action ORDER BY hits DESC LIMIT ${limit}', NULL, '2023-08-19 00:00:00', '2023-08-19 00:00:00');

COMMIT;
SET FOREIGN_KEY_CHECKS = 1;
