Release 381 (TSG-24.08)
* 新增Function，增加 groupUniqArray 聚合函数(TSG-21941)
* 新增Function，增加 DATETIME_FLOOR_WITH_FILL 自定义补全函数(TSG-22021)
* 扩展DSL接口，支持Customized Statistics查询(TSG-21708)
* 扩展Schema，增加client_ip_tags、server_ip_tags、server_fqdn_tags 字段(TSG-21877)
* 扩展Schema，traffic_shaping_rule_hits Metric 增加latency_distribution_us字段(TSG-21916)
* 修改Schema，fqdn_category_list、GeoIP 日志字段 标注过期(TSG-21837)
* 修改Schema，durations 支持定义多级数据储存粒度(TSG-21985)
* 修改Schema，traffic_sketch_metric 数据粒度更新为5min
* 修改Schema，Metrics ingestion_delay 修改为 15 sec 
* 修改Schema，显示Application Extra Info字段: Security、Monitor、Intercept、Session Event(TSG-22059)
* 修改Schema，Proxy Manipulation 隐藏IP Tags和FQDN Tags等字段(TSG-22071)
* 修正Customized Statistics DSL，修正时序图判断逻辑(TSG-22066)
* 完善时间粒度函数，时间粒度函数能够适配两级及以上的粒度指标(TSG-21985)
* 完善补全函数，对维度结果列值为 NULL 的处理(TSG-22096)
* 完善自动粒度解析，完善对 Group By、Order By 表达式的解析处理
* 完善Customized Statistics DSL，更新dimensions.metric_name 为 dimensions.dimension_name，新增expression高级参数(TSG-22021)
* 完善Customized Statistics DSL，完善Query Aggregate Function函数映射
* 完善Customized Statistics DSL，限定custom.statistics.subquery.topk.dimensions支持一个维度(TSG-22116)
* 优化代码，修复 SonarQube 检查异常代码(GAL-570)