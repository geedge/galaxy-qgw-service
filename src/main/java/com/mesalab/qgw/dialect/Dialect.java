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
    BaseResult executeSyntaxCheck();

    /**
     * 执行查询操作
     * @return
     */
    BaseResult executeQuery();


    /**
     * 执行数据库系统管理操作，包含 Show/Describe等
     * @return
     */
    BaseResult executeAdministrativeQuery();


    /**
     * Desc: 取消查询
     *
     * @param queryId
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2021/2/24 11:18 上午
     */
    BaseResult executeKillQuery(String queryId);

    /**
     * Desc: 获取查询进度
     *
     * @param queryId
     * @return {@link BaseResult}
     * @created by wWei
     * @date 2021/2/24 5:01 下午
     */
    BaseResult getProcesses(String queryId);

}
