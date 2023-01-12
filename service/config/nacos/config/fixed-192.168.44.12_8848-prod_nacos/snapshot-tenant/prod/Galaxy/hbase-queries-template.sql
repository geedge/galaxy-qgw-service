--Q01. 范围查询
SELECT last_update_time FROM relation_account_framedip WHERE last_update_time>=CAST(TO_TIMESTAMP (@start,'yyyy-MM-dd HH:mm:ss','Asia/Shanghai') AS UNSIGNED_LONG) AND last_update_time<CAST(TO_TIMESTAMP (@end,'yyyy-MM-dd HH:mm:ss','Asia/Shanghai') AS UNSIGNED_LONG) LIMIT 30
--Q02. KV查询
select * from relation_account_framedip  where ROWKEY = '0a771a381088e7d72ded13e998c06cbe' limit 1