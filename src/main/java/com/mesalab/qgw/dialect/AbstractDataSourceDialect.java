package com.mesalab.qgw.dialect;


import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.qgw.model.api.ApiParam;
import com.zdjizhi.utils.Encodes;
import com.zdjizhi.utils.StringUtil;
import lombok.Data;
import com.zdjizhi.calcite.rel.core.TableScan;

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
    abstract  String getOriginQuery();

    /**
     * 获取用于语法检查的SQL，目标生成无Filter语句。
     * @return
     */
    abstract  String getSyntaxCheckQuery();

    /**
     * 简化规范异常信息展示。
     * @return
     */
    abstract String simplyErrorMessage(Object object);

    /**
     * 解析并生成最终结果
     * @param sql
     * @param message
     * @return
     */
    abstract  BaseResult generateBaseResult(String sql, Optional<String> message);

    @Override
    final public BaseResult executeQuery() {

        init();
        String sql = convertQuery(getBestQuery());
        return generateBaseResult(sql, Optional.of("OK"));
    }

    @Override
    final public BaseResult executeSyntaxCheck() {
        init();
        String originSQL = getOriginQuery();
        String sql = convertQuery(getSyntaxCheckQuery());
        return generateBaseResult(sql, Optional.of(originSQL));
    }

    @Override
    final public BaseResult executeAdministrativeQuery() {
        init();
        String sql = getOriginQuery();
        return generateBaseResult(sql, Optional.of("OK"));
    }

}
