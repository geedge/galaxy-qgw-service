package com.mesalab.qgw.dialect;

import com.mesalab.common.base.BaseResult;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.SQLQuerySource;

public interface Dialect {

    /**
     * 数据库本身是否支持分页当前的分页查询方式
     * 如果数据库不支持的话，则不进行数据库分页
     *
     * @return true：支持当前的分页查询方式
     */
    boolean supportsLimit();

    /**
     * 将sql转换为分页SQL，分别调用分页sql
     *
     * @param sql    SQL语句
     * @param offset 开始条数
     * @param limit  每页显示多少纪录条数
     * @return 分页查询的sql
     */
    String getLimitString(String sql, int offset, int limit);

    /**
     * SQL查询语句转换为方言所认知的查询语句。
     * @param sql
     * @return
     */
    String convertQuery(String sql);

    /**
     * 基于RBO(rule-base optimizer)获取最优化的SQL
     * @param sql
     * @return
     */
    String findBestQuery(String sql);

    Dialect queryDefaultPreProcess();

    /**
     * 执行SQL语法检查
     * @return
     */
    Dialect executeSyntaxCheck();

    /**
     * 执行查询操作
     * @return
     */
    Dialect executeQuery();

    /**
     * 返回结果数据进行封装
     * @return
     */
    BaseResult build();
















}
