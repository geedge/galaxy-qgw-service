/*
 SQL Datasets
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
-- ----------------------------
-- Table structure for dataset
-- ----------------------------
DROP TABLE IF EXISTS `dataset`;
CREATE TABLE `dataset` (
                           `id` int(64) NOT NULL AUTO_INCREMENT,
                           `identifier_name` varchar(128) NOT NULL,
                           `category` varchar(128) DEFAULT NULL,
                           `execute_engine` varchar(16) NOT NULL DEFAULT 'qgw' COMMENT 'qgw, clickhouse, druid, hbase',
                           `type` varchar(16) NOT NULL COMMENT 'dsl,sql,long_polling',
                           `template` text NOT NULL,
                           `description` varchar(512) DEFAULT NULL,
                           `generated_time` bigint DEFAULT NULL,
                           `last_update_time` bigint DEFAULT NULL,
                           PRIMARY KEY (`id`) USING BTREE,
                           UNIQUE KEY `identifier_name` (`identifier_name`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

-- ----------------------------
-- Trigger to set timestamps
-- ----------------------------
DELIMITER //

CREATE TRIGGER set_timestamps_trigger BEFORE INSERT ON `dataset`
    FOR EACH ROW
BEGIN
    SET NEW.generated_time = UNIX_TIMESTAMP();
    SET NEW.last_update_time = UNIX_TIMESTAMP();
END //

DELIMITER ;

-- ----------------------------
-- Records of dataset
-- ----------------------------
BEGIN;

COMMIT;
SET FOREIGN_KEY_CHECKS = 1;