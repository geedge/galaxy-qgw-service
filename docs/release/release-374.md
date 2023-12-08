Release 374 (TSG-23.12)
* 移除ES组件应用及相关代码(GAL-439)
* 新增自定义聚合函数：MEDIAN_HDR、QUANTILE_HDR、PERCENTILES_HDR、COUNT_DISTINCT、APPROX_COUNT_DISTINCT_HLLD(GAL-448)
* 重构Datasets,初始化内置数据(TSG-17787)
* 完善save query接口，适配save_query_job表结构
* 扩展DSL接口，liveChart支持DSL接口查询(TSG-17269)
* 修改Schema，logicalType、constraints.type区分timestamp、unix_timestamp
* 扩展离线任务接口，新增状态、结果批量接口(TSG-17790)
* 扩展Function Utils，新增毫秒时间函数(GAL-449)
* 扩展Schema，适配DosEvent、ProxyEvent与AssessmentEvent结构变化(TSG-17819)
* 完善Schema，日志Schema部分字段补全Combine函数定义(TSG-17239)
* 修改配置，Hbase查询结果存储表名变更:report_result ——> saved_query_result
* 修改Schema，基础字段app_path、protocol_path重命名(TSG-17828)
* 新增SQL Query Job API接口
* 修改Schema，移除Radius/ GTP-C/ radius_on_off schema，删除radius_match、gtpc_match 函数(TSG-17813)
* 修改配置，HOS存储桶重命名：knowledge_base_hos_bucket->knowledge_base_bucket(GAL-453)
* 修改Schema，ssl_sni、dtls_sni、http_host、quic_sni字段新增sub_domain、get_value函数