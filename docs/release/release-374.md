Release 374 (TSG-23.12)
* 新增自定义聚合函数：MEDIAN_HDR、QUANTILE_HDR、PERCENTILES_HDR、COUNT_DISTINCT、APPROX_COUNT_DISTINCT_HLLD(GAL-448)
* 新增自定义毫秒时间函数：FROM_UNIXTIME_MILLIS、UNIX_TIMESTAMP_MILLIS(GAL-449)
* 新增HOS API Get File List 接口(TSG-17843)
* 新增Monitor Schema，监测策略规则命中统计Schema(TSG-17851)
* 重构Query API接口：管理SQL、DSL查询接口，支持批量获取(TSG-17269)
* 重构Datasets API接口，初始化内置数据集(TSG-17787)
* 重构Database API、Troubleshooting API(TSG-17843)
* 修改配置，Hbase Saved Query结果表为tsg_galaxy:saved_query_result
* 修改配置，HOS存储桶重命名为knowledge_base_bucket(GAL-453)
* 修改Schema，适配DosEvent、ProxyEvent与AssessmentEvent结构变化(TSG-17819)
* 修改Schema，基础字段app_path、protocol_path重命名(TSG-17828)
* 修改Schema，修改文件路径补全对应HOS桶名
* 修改Schema，更新fqdn_category_list字段dict_location数据字典引用位置
* 修改Schema，，删除radius_match、gtpc_match 函数(TSG-17813)
* 修改Schema，ssl_sni、dtls_sni、http_host、quic_sni字段新增sub_domain、get_value函数
* 修改Schema，日志Schema部分字段补全Combine函数定义(TSG-17239)
* 修改Schema，logicalType、constraints.type区分timestamp、unix_timestamp
* 删除过期Schema：radius_record、gtpc_record、radius_onff_log、sys_packet_capture_event(TSG-17239)
* 删除ES组件应用及相关代码(GAL-439)

