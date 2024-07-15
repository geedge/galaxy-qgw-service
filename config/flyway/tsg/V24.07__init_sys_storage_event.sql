/*
 Sys Storage Event
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
-- ----------------------------
-- Table structure for dataset
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_storage_event` (
                           `id` int(64) NOT NULL AUTO_INCREMENT,
                           `log_type` varchar(512) NOT NULL COMMENT 'Enums: Traffic Logs or Metrics or Files',
                           `data_center` varchar(512) NOT NULL,
                           `used_size` bigint NOT NULL,
                           `total_allocated_size` bigint NOT NULL,
                           `bytes` bigint NOT NULL,
                           `since_time` bigint NOT NULL,
                           `generated_time` bigint NOT NULL,
                           PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

SET FOREIGN_KEY_CHECKS = 1;