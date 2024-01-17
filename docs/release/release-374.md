Release 374 (TSG-23.12)
* 新增自定义聚合函数：MEDIAN_HDR、QUANTILE_HDR、PERCENTILES_HDR、COUNT_DISTINCT、APPROX_COUNT_DISTINCT_HLLD、RATE(GAL-44、TSG-18048)
* 新增自定义毫秒时间函数：FROM_UNIXTIME_MILLIS、UNIX_TIMESTAMP_MILLIS(GAL-449)
* 新增HOS API Get File List 接口(TSG-17843)
* 新增Monitor Schema，监测策略规则命中统计Schema(TSG-17851)
* 新增Trace ID，日志中新增TraceID(同响应request_id)便于日志追踪(TSG-17851)
* 删除过期Schema：radius_record、gtpc_record、radius_onff_log、sys_packet_capture_event、ctive_defence_event(TSG-17239、TSG-18119)
* 删除ES组件应用及相关代码(GAL-439)
* 重构Query API接口：管理SQL、DSL查询接口，支持批量获取(TSG-17269)
* 重构Datasets API接口，初始化内置数据集(TSG-17787)
* 重构Database API、Troubleshooting API(TSG-17843)
* 重构Util API (TSG-17843)
* 重构Knowledge Base API,对知识库文件管理接口进行重构(TSG-17843)
* 修改liveChart，移除liveChart summary uniq_client_ip指标
* 修改Dataset，field-discovery，filter增加 vsys_id过滤条件(TSG-18229)
* 修改dataset，数monitor-event-timeline 只提供会话指标趋势(TSG-17952)
* 修改配置，Hbase Saved Query结果表为tsg_galaxy:saved_query_result
* 修改配置，HOS存储桶重命名为knowledge_base_bucket(GAL-453)
* 修改样例数据接口，增加where时间范围(防止数据集过大)及默认Limit，其它不做处理(TSG-18645)
* 修改Schema，适配DosEvent、ProxyEvent与AssessmentEvent结构变化(TSG-17819)
* 修改Schema，基础字段app_path、protocol_path重命名(TSG-17828)
* 修改Schema，修改文件路径补全对应HOS桶名
* 修改Schema，更新fqdn_category_list字段dict_location数据字典引用位置
* 修改Schema，，删除radius_match、gtpc_match 函数(TSG-17813)
* 修改Schema，ssl_sni、dtls_sni、http_host、quic_sni字段新增sub_domain、get_value函数
* 修改Schema，日志Schema部分字段补全Combine函数定义(TSG-17239)
* 修改Schema，logicalType、constraints.type区分timestamp、unix_timestamp
* 修改Schema，完善Log/Metric Schema 函数、Unit约束(TSG-18048)
* 修改Schema，Proxy Event decoded_as 增加DoH协议字典
* 修改Schema，更新流量Top类统计schema metric字典
* 修改Schema，修改 metrics schema dimensions、metrics、filters自定义报告约束
* 修改Schema，Monitor Events中未展示Monitor Mirrored相关字段(TSG-18602)
* 修改Schema，Manipulation日志中应隐藏FQDN Category List、HTTP.Proxy Flags等字段(TSG-18577)
* 修改Schema，修正traffic_shaping_rule_hits.doc中字段名(TSG-18639)
* 扩展Schema，histogram支持MEDIAN_HDR函数
* 扩展Schema，schema doc 增加time分类(TSG-17875)
* 扩展Schema，新增公共字段ip_protocol，支持report统计(TSG-18203)
* 扩展Schema，新增公共字段statistics_rule_list(TSG-18328)
* 扩展Schema，VoIP Record decoded_as 增加VoIP字典值(TSG-18445)
* 扩展Schema，日志Schema支持表达式字段(TSG-18511)
* 扩展Schema，object_statistics 新增object_type字段及字典数据(TSG-17960)
* 扩展Schema，描述可作为维度的时间字段(TSG-18662)



