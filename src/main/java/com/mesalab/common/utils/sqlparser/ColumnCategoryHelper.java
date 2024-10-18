package com.mesalab.common.utils.sqlparser;

import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.geedgenetworks.utils.StringUtil;
import com.jayway.jsonpath.JsonPath;
import com.mesalab.common.enums.DBEngineType;
import com.mesalab.qgw.constant.MetaConst;
import com.mesalab.qgw.dialect.ClickHouseDialect;
import com.mesalab.qgw.dialect.DruidDialect;
import com.mesalab.qgw.model.basic.SelectStatement;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.mesalab.qgw.constant.MetaConst.*;

/**
 * TODO
 *
 * @Classname ColumnCategoryHelper
 * @Date 2024/3/7 15:52
 * @Author wWei
 */
public class ColumnCategoryHelper {
    private static final Log log = LogFactory.get();

    private ColumnCategoryHelper() {
    }

    public static Object expandMetaCategory(Object columnMeta, SelectStatement selectStatement, String dbEngine) {
        if (StringUtil.isEmpty(selectStatement) || StringUtil.isEmpty(selectStatement.getExecSQL()) || StringUtil.isEmpty(columnMeta)) {
            return columnMeta;
        }
        List<Map<String, String>> result = JsonPath.read(columnMeta, "$");
        Map<String, String> groupDimension = selectStatement.getGroupDimension();
        Map<String, String> aliasFields = selectStatement.getAliasFields();
        meta:
        for (Map<String, String> map : result) {
            if (StrUtil.isEmptyIfStr(groupDimension)) {
                map.put(META_CATEGORY, META_CATEGORY_METRIC);
                continue;
            }
            String field = String.valueOf(map.get(META_NAME));
            for (String dimensionKey : groupDimension.keySet()) {
                if (Objects.equals(field, dimensionKey) || Objects.equals(field, groupDimension.get(dimensionKey))) {
                    map.put(META_CATEGORY, META_CATEGORY_DIMENSION);
                    continue meta;
                }
                try {
                    String selectItemExpr = aliasFields.get(field) == null ? SQLFunctionUtil.generateDateFunction(field, dbEngine) : aliasFields.get(field);
                    String dimensionExpr = groupDimension.get(dimensionKey) == null ? SQLFunctionUtil.generateDateFunction(dimensionKey, dbEngine) : groupDimension.get(dimensionKey);
                    if (dimensionExpr != null && selectItemExpr != null) {
                        ExpressionColumnCollectAdapter<Object> selectItemAdapter = new ExpressionColumnCollectAdapter<>();
                        Expression expr1 = CCJSqlParserUtil.parseExpression(selectItemExpr);
                        if (SQLFunctionUtil.pAggregateFunStandard.matcher(expr1.toString()).find()) {
                            map.put(META_CATEGORY, META_CATEGORY_METRIC);
                            continue meta;
                        }
                        if (DBEngineType.CLICKHOUSE.getValue().equalsIgnoreCase(dbEngine) && ClickHouseDialect.pAggregateFunSpecificOfCK.matcher(expr1.toString()).find()) {
                            map.put(META_CATEGORY, META_CATEGORY_METRIC);
                            continue meta;
                        }
                        if (DBEngineType.DRUID.getValue().equalsIgnoreCase(dbEngine) && DruidDialect.pAggregateFunSpecificOfDruid.matcher(expr1.toString()).find()) {
                            map.put(META_CATEGORY, META_CATEGORY_METRIC);
                            continue meta;
                        }
                        expr1.accept(selectItemAdapter, null);
                        Set<String> selectItemColumn = selectItemAdapter.getColumns();

                        ExpressionColumnCollectAdapter<Object> dimensionAdapter = new ExpressionColumnCollectAdapter<>();
                        Expression expr2 = CCJSqlParserUtil.parseExpression(dimensionExpr);
                        expr2.accept(dimensionAdapter, null);
                        Set<String> dimensionColumn = dimensionAdapter.getColumns();

                        boolean hasDuplicates = selectItemColumn.stream().anyMatch(dimensionColumn::contains);
                        if (hasDuplicates) {
                            map.put(META_CATEGORY, META_CATEGORY_DIMENSION);
                            continue meta;
                        }
                    }
                } catch (JSQLParserException e) {
                    log.warn("build meta error, message is: {}", e.getMessage());
                }
            }
            map.put(META_CATEGORY, META_CATEGORY_METRIC);
        }
        return result;
    }
}
