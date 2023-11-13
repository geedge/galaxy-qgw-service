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
COMMIT;
SET FOREIGN_KEY_CHECKS = 1;
