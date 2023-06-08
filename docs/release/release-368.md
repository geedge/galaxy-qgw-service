Release 368 (TSG-23.06)
* 优化知识库更新、发布接口
* 升级Galaxy Tools到1.1.3, 标识JsonMapper已过时，删除IPLookUp工具类
* json类库统一使用Fastjson2(GAL-342)
* 修改Schema，Proxy Events Sub Action字段不作为默认Field discovery的Selected Fields的列(TSG-15133)
* 完善TopN优化，解决Field Discovery与Statistics Top10结果差异较大问题(TSG-15360)
* 修改Schema，统一定义Metrics Schema Data Center/Device Group 支持检索方式为 =,in(TSG-15431)
* 修改log4j2配置，基于不同日志级别输出不同的日志文件(GAL-353)