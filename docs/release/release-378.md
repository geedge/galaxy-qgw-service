Release 378 (TSG-24.05)
* 新增Schema, 新增路径数据遥测日志datapath_telemetry_record(TSG-20473)
* 新增Dataset, 新增数据路径遥测相关业务数据集: count、list、packet combine(TSG-20473)
* 新增Pcapng合并功能, 扩展DSL接口支持Packet Combine合并功能(TSG-20473)
* 扩展Schema, 新增app_extra_info公共字段(TSG-20783)
* 扩展Schema, Statistics Rule 新增 c2s/s2c_ttl、c2s/s2c_link_id 字段，支持Statistics模版维度(TSG-20725)
* 扩展Schema, 日志新增公共字段c2s_ttl、s2c_ttl(TSG-20811)
* 扩展Schema, Link ID支持 Report Filter
* 扩展Schema, 新增公共字段direction(TSG-20925)
* 扩展操作符函数, 支持IS NULL、IS NOT NULL检索(TSG-20925) 
* 修改Schema, VoIP Record删除与SIP/RTP协议无关字段(TSG-20765)
* 修改Schema, 设置表达式_rule_id 字段不支持日志检索
* 修改HOS API, 参数命名遵循下划线规范(TSG-20730)
* 修改schema, 安全事件日志隐藏monitor_mirrored_pkts，monitor_mirrored_bytes字段
