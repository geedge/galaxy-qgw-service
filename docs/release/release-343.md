 Release 343 (TSG-21.05)
 * 新增Schema，新增事物日志、活跃会话、GTP-C日志(TSG-6048)。
 * 扩展Schema，日志公共属性支持common_service_category(TSG-5918)。
 * 扩展Schema，支持带宽增量指标bytes与packets(TSG-5926)。
 * 扩展Schema，支持隧道协议(TSG-5886)。
 * 扩展Schema，schema配置文件json化(TSG-5994)。
 * 扩展Schema，修改ETHERNET定义，增加MULTIPATH_ETHERNET定义(TSG-5886)。
 * 扩展Schema，采集日志Action字段的隐藏;包含VoIP的协议SIP/RTP中属性中label修改(TSG-6212)。
 * 扩展Schema，Surrogate ID、Application ID字段类型变更为string，新增app_match函数(TSG-6215)。
 * 扩展Schema，取消FQDN Category报告指定与检索约束(TSG-5918)。
 * 新增Schema，liveChart_history、liveChart_live。
 * 新增Nacos健康检测接口。
 * 修复正则替换对特殊字符\与$的处理(TSG-6244)。
 * 改善SQL语句校验功能，细化异常分类与异常描述(TSG-5966)。
 * 应用弱依赖增强，支持本地模式(TSG-6002)。
 * 配置文件增加扩展名，改善本地显示和开发效率。

    