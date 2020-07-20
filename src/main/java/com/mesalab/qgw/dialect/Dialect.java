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
     * 执行数据库系统管理操作，包含 Show/Describe/Kill等
     * @return
     */
    Dialect executeAdministrativeQuery();

    /**
     * 返回结果数据进行封装
     * @return
     */
    BaseResult build();
















}
