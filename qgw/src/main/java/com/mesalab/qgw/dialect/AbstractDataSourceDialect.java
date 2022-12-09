package com.mesalab.qgw.dialect;

import com.google.common.collect.Maps;
import com.mesalab.common.entity.BaseResult;
import com.zdjizhi.utils.Encodes;
import com.zdjizhi.utils.JsonMapper;
import lombok.Data;

import java.util.Map;
import java.util.Optional;

@Data
public abstract class AbstractDataSourceDialect implements Dialect {
    abstract  void init();

    /**
     * SQL查询语句转换为方言所认知的查询语句。
     * @param sql
     * @return
     */
    abstract  String convertQuery(String sql);

    /**
     * 基于RBO(rule-base optimizer)获取最优化的SQL
     * @return
     */
    abstract  String getBestQuery();

    /**
     * 获取原始SQL语句，不做规则转换，直接下推
     * @return
     */
    abstract  String getOriginalQuery();

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
        String transformedSQL = convertQuery(getBestQuery());
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
        sqlMap.put("OriginalSQL", Encodes.encodeBase64(getOriginalQuery().getBytes()));
        sqlMap.put("transformedSQL", Encodes.encodeBase64(transformedSQL.getBytes()));
        return generateBaseResult(transformedSQL, Optional.of(JsonMapper.toJsonString(sqlMap)));
    }


    @Override
    final public BaseResult executeSyntaxCheck() {
        init();
        String transformedSQL = getSampleQuery();
        return generateBaseResult(transformedSQL, Optional.of(getOriginalQuery()));
    }

    @Override
    final public BaseResult executeAdministrativeQuery() {
        init();
        String sql = getOriginalQuery();
        return generateBaseResult(sql, Optional.of("OK"));
    }

}
