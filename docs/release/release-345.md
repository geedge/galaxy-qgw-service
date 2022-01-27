 Release 345 (TSG-21.07)
 * 升级工具类galaxy-tool版本，由1.0.4 升级到1.0.6，优化IP与ASN定位的返回数据格式。
 * 修改IPUtil，使用默认dat目录的定位库。
 * 支持查询Elasticsearch数据源，提供基于SQL数据服务能力，健康状态检测(CN-30)。
 * 支持对非所需组件，当IP配置为127.0.0.1时，不影响健康状态检测。
 * 修复Schema，纠正Http协议响应延迟的日志字段拼写(TSG-6957)。
 * 扩展Schema，日志支持链路ID(TSG-6945)。
 * 扩展Schema，公共字段增加common_userdefine_app_name(TSG-6878)。