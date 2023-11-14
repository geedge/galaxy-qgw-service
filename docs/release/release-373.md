Release 373 (TSG-23.11)
* 优化服务接口命名，Rest SQL、Async API、Admin API(TSG-17269)
* 修改Schema，原公共Schema更新为public_schema_info_2311，新增public_schema_info(TSG-17493)
* 修改Schema，重构会话、安全、事务日志，删除过渡会话日志(TSG-17493)
* 修改Live Traffic Chart，App Internal IPs基于flags判定(TSG-17493)
* 升级JDK版本至amazon JDK11(GAL-416)
* 修改知识库推荐subscribe ID pool接口，使用实时推荐数据 (TSG-17496)
* Security Event 更新decoded_as字典;定义缺失协议字段(TSG-17639)
* 完善通联日志decoded_as 协议类型定义(TSG-17672)
* 适配报告，统一report_result表名
* packet_capture_file字段不支持查询(TSG-17665)
* 会话日志删除index_table
* 适配IP、ASN定位库v4/v6合并后的加载逻辑(GAL-440)