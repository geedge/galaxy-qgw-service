package com.mesalab.qgw.dialect;

import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Maps;
import com.mesalab.common.entity.BaseResult;
import com.geedgenetworks.utils.Encodes;
import com.mesalab.qgw.model.basic.SQLQueryContext;
import lombok.Data;

import java.util.Map;
import java.util.Optional;

@Data
public abstract class AbstractDataSourceDialect implements Dialect {
    protected SQLQueryContext sqlQueryContext;
    public AbstractDataSourceDialect(SQLQueryContext sqlQueryContext) {
        this.sqlQueryContext = sqlQueryContext;
    }

    abstract void init();

    /**
     * Query SQL statement to be converted the SQL that can be executed by the data source.
     * @param sql
     * @return
     */
    abstract  String convertQuery(String sql);

    /**
     *  Generate the best query statement by RBO(Rule Based Optimization)
     * @return
     */
    abstract  String generateRuleBaseOptimizedQuery();

    /**
     * 获取样例SQL，目标生成无Filter语句。
     * @return
     */
    abstract  String getSampleQuery();

    /**
     * get Explain Statement
     * @return
     */
    abstract String getExplainQuery();

    /**
     * 简化规范异常信息展示。
     * @return
     */
    abstract String simplyErrorMessage(Object object);



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
     * @param sql
     * @param message
     * @return
     */
    abstract  BaseResult generateBaseResult(String sql, Optional<String> message);


    /**
     * shows the execution plan of a select statement.
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
    final public BaseResult executeSyntaxCheck() {
        init();
        String transformedSQL = getSampleQuery();
        return generateBaseResult(transformedSQL, Optional.of(sqlQueryContext.getOriginalSQL()));
    }

    @Override
    final public BaseResult executeAdministrativeQuery() {
        init();
        return generateBaseResult(sqlQueryContext.getOriginalSQL(), Optional.of("OK"));
    }

}
