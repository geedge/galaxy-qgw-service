Release 380 (TSG-24.07)
* 扩展Schema，IP类型字段支持 isIPAddressInRange(TSG-21544)
* 扩展Schema，Statistics Rule Metric 增加IP Fragments字段(TSG-21637)
* 扩展时间粒度函数，DSL、SQL Query Job支持默认时间粒度(TSG-21420)
* 新增储存配额 sys_storage_event表(TSG-21555)
* 优化储存配额接口，新增max_usage、基于mariadb获取数据、删除冗余代码(TSG-21554)
* 完善审计日志，查询网关具备记录高延迟查询 SQL 的功能(GAL-603)
* 优化Schema，统一命名:Client-to-Server、Server-to-Client(TSG-21651)
* 修正Traffic Spectrum Throughput 流量曲线粒度(TSG-21648)
* 扩展Function，转义isIPAddressInRange，解决空值导致函数执行异常问题
* 完善样例数据，避免schema dict值数量小于随机结果数量时，生成数据异常问题