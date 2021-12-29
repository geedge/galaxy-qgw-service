 Release 350 (TSG-21.12)
 * 优化LiveChart，Network Protocol Tree中的子项按照Throughput由大到小排序(TSG-8495)。
 * 新增标准时区函数CONVERT_TZ(TSG-8519)。
 * 修复Live Traffic Charts 获取协议树数据响应慢问题。栈构建协议树基于List进行遍历造成，更换为Hash结构进行存储(TSG-8707)。
 * 修复进度接口获取异常(TSG-8737)。
 * 修复entity推荐接口结果数量不正确，接口支持LIMIT限制(TSG-8605)。
 * 新增APP活跃Client IP推荐接口查询Hbase缓存策略。
 * 完善Subscriber ID获取IP推荐功能，过滤失效数据(TSG-8523)。
 * 扩展Schema，增加内置列internal_columns描述(TSG-8759)。
 * 扩展Schema，Radius协议增加字段radius_acct_authentic(TSG-8648)。
 * 扩展Schema，描述L2TP PPTP协议(TSG-8618)。
 * 新增字段发现功能(TSG-8542)。
 * 完善ToP优化，细化ToP优化策略，聚合函数支持无别名，增加min与max函数的支持
 * 修复远程代码执行漏洞，升级log4j2版本为2.17.0
 * 修复Schema，DoS Log搜索Attack Type移除DNS Amplification(TSG-9188)。
 