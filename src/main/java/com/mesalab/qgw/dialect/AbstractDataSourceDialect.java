package com.mesalab.qgw.dialect;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Maps;
import com.mesalab.common.entity.BaseResult;
import com.geedgenetworks.utils.Encodes;
import com.mesalab.qgw.model.basic.SQLQueryContext;
import lombok.Data;

import java.util.*;

@Data
public abstract class AbstractDataSourceDialect implements Dialect {
    protected SQLQueryContext sqlQueryContext;
    private static final Log log = LogFactory.get();
    public AbstractDataSourceDialect(SQLQueryContext sqlQueryContext) {
        this.sqlQueryContext = sqlQueryContext;
    }

    abstract void init();

    /**
     * Query SQL statement to be converted the SQL that can be executed by the data source.
     *
     * @param sql
     * @return
     */
    abstract String convertQuery(String sql);

    /**
     * Generate the best query statement by RBO(Rule Based Optimization)
     *
     * @return
     */
    abstract String generateRuleBaseOptimizedQuery();

    /**
     * 获取样例SQL，目标生成空执行语句。
     *
     * @return
     */
    abstract String getSampleQuery();

    /**
     * get Explain Statement
     *
     * @return
     */
    abstract String getExplainQuery();

    /**
     * Desc: 简化规范异常信息展示。
     *
     * @return {@link String}
     */
    abstract String simplyErrorMessage(Object object);

    /**
     * Desc: 构建样例数据 meta 信息
     *
     * @created by wWei
     * @date 2024/3/7 09:54
     */
    abstract List<Map<String, String>> buildExampleMeta();

    /**
     * Desc: 样例数据
     *
     * @return {@link null}
     * @created by wWei
     * @date 2024/3/7 10:15
     */
    abstract List<Object> buildExampleData();


    @Override
    final public BaseResult executeQuery() {
        init();
        String transformedSQL = convertQuery(generateRuleBaseOptimizedQuery());
        return generateBaseResult(transformedSQL, Optional.of("OK"));
    }

    @Override
    final public BaseResult executeSampleQuery() {
        init();
        String transformedSQL = getSampleQuery();
        return generateBaseResult(transformedSQL, Optional.of("OK"));
    }

    /**
     * 解析并生成最终结果
     *
     * @param sql
     * @param message
     * @return
     */
    abstract BaseResult generateBaseResult(String sql, Optional<String> message);


    /**
     * shows the execution plan of a select statement.
     *
     * @return
     */
    final public BaseResult executeExplainPlan() {
        init();
        String transformedSQL = getExplainQuery();
        Map sqlMap = Maps.newHashMap();
        sqlMap.put("OriginalSQL", Encodes.encodeBase64(sqlQueryContext.getOriginalSQL().getBytes()));
        sqlMap.put("TransformedSQL", Encodes.encodeBase64(transformedSQL.getBytes()));
        return generateBaseResult(transformedSQL, Optional.of(JSON.toJSONString(sqlMap)));
    }

    @Override
    final public BaseResult executeSyntaxValidation() {
        init();
        String transformedSQL = getSampleQuery();
        BaseResult baseResult = generateBaseResult(transformedSQL, Optional.of(sqlQueryContext.getOriginalSQL()));
        try {
            baseResult.setMeta(buildExampleMeta());
            baseResult.setData(buildExampleData());
        } catch (Exception e) {
            log.error("Build Example Data Error: {} ", e.toString());
        }
        return baseResult;
    }
    @Override
    final public BaseResult executeAdministrativeQuery() {
        init();
        return generateBaseResult(sqlQueryContext.getOriginalSQL(), Optional.of("OK"));
    }


}
