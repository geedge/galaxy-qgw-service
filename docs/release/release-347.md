 Release 347 (TSG-21.09)
 * 扩展SQL数据集诊断接口，新增option参数，区分功能校验与POC测试。
 * 改善SQL与Code强耦合，涉及entity、network、system接口。
 * 扩展Schema，公共schema提取(GAL-56)。
 * 新增实体推荐接口(TSG-7512)。
 * 优化SELECT查询，不同option下的执行过程。
 * 扩展Schema，日志统一术语(TSG-7579)、更新etl表名。
 * 扩展Schema，会话、过渡、安全日志支持QUIC协议检索和报告定制(TSG-7695)
 * 扩展健康检查接口，展示组件timeZone信息(druid、ck、es、mariadb)
 * 扩展Schema，DoS事件日志schema增加DNS Flood(TSG-7739)
 * 完善SQL数据集诊断接口，测试集分类执行。
 * 扩展Http Rest SQL请求接口，支持Post的Request body为application/json形式传递参数。
 * 优化重复提交处理逻辑，仅针对幂等GET操作进行处理。
