package com.mesalab.qgw.dialect;

import com.mesalab.common.base.BaseResult;
import com.mesalab.qgw.model.api.ApiParam;
import com.zdjizhi.utils.StringUtil;
import lombok.Data;

import java.util.Optional;

@Data
public abstract class AbstractEngineDialect implements Dialect {

    abstract  void init();

    public boolean isExecute() {
        return false;
    }

    /**
     * Desc: 执行自定义函数操作
     *
     * @param
     * @return
     * @created by wWei
     * @date 2022/4/27 5:25 下午
     */
    abstract void executeUDF();

    /**
     * Desc: 获取联合查询SQL
     *
     * @param
     * @return {@link String}
     * @created by wWei
     * @date 2022/4/27 5:26 下午
     */
    abstract String getFederateQuery();

    /**
     * Desc: 获取样数据SQL
     *
     * @param
     * @return {@link String}
     * @created by wWei
     * @date 2022/4/27 5:27 下午
     */
    abstract String getSampleQuery();

    /**
     * Desc: 获取语法检查SQL
     *
     * @param
     * @return {@link String}
     * @created by wWei
     * @date 2022/4/27 5:28 下午
     */
    abstract String getSyntaxCheckQuery();

    /**
     * 解析并生成最终结果
     * @return
     */
    abstract  BaseResult generateBaseResult(String sql);


    @Override
    final public BaseResult executeQuery() {
        init();
        executeUDF();
        return generateBaseResult(getFederateQuery());
    }

    @Override
    final public BaseResult executeSampleQuery() {
        init();
        executeUDF();
        return generateBaseResult(getSampleQuery());
    }

    @Override
    final public BaseResult executeSyntaxCheck() {
        init();
        executeUDF();
        return generateBaseResult(getSyntaxCheckQuery());
    }

    @Override
    final public BaseResult executeAdministrativeQuery() {
        return  null;
    }




}
