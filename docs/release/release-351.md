 Release 351 (TSG-22.01)
 * 新增密码加密接口，敏感数据加密(TSG-8835)
 * 扩展statistics统计，clickhouse增加选项bytes_read，显示执行SQL时所加载的字节数量
 * 扩展Live Chart，支持Encapsulation Paths和Protocols and Applications 数量统计(TSG-9061)
 * 扩展Live Chart，统计相同所有Protocol/APP在全栈的流量(TSG-9053)
 * 扩展Schema，增加Stratum协议相关日志字段(TSG-9169)
 * 扩展Schema，增加公共字段common_packet_capture_file(TSG-9196)
 * 扩展Schema，增加公共字段common_app_behavior字段(TSG-9166)
 * 完善Entity推荐接口，活跃Client IP推荐设置缓存数据±30 min误差(TSG-9024)
 * 完善Schema，内置列internal_columns增加文件类型的字段，调整除了Security 和 Proxy Events 等Action有值的日志详情(TSG-9171)
 * 完善Schema，日志字段查询约束由"白名单"改为"黑名单"模式(TSG-9207)
 * 优化镜像，变更jdk版本，减少镜像大小
 * 修复App Active Client IP推荐获取预计算数据存在重复IP情况(TSG-9263)
 * 修复Field Discovery Job结果中，field字段自动新增无效问题(TSG-9373)