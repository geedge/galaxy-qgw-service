package com.mesalab.common.utils.sqlparser;

import cn.hutool.core.util.StrUtil;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.qgw.model.api.HBaseAPISource;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.arithmetic.*;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.values.ValuesStatement;

import java.util.List;

/**
 * 为避免phoenix将列名、别名、表名默认转大写，所以对SQL列名、别名添加双引号。
 *
 * @Classname ColumnVisitor
 * @Date 2021/10/22 2:26 下午
 * @Author wWei
 */
public class TableColumnVisitor implements SelectVisitor, SelectItemVisitor, FromItemVisitor, GroupByVisitor, OrderByVisitor, ExpressionVisitor {
    private HBaseAPISource hBaseAPISource = (HBaseAPISource) SpringContextUtil.getBean("hBaseAPISource");

    public void surroundWithDoubleQuote(Statement statement) {
        if (statement instanceof Select) {
            Select select = (Select) statement;
            SelectBody selectBody = select.getSelectBody();
            selectBody.accept(this);
        } else {
            throw new RuntimeException("Only support statement operation as select.");
        }
    }

    private String addDoubleQuote(String str) {
        return "\"" + str + "\"";
    }

    private boolean isQuote(String columnName) {
        return columnName.startsWith("\"") && columnName.endsWith("\"");
    }

    @Override
    public void visit(AllColumns allColumns) {
    }

    @Override
    public void visit(AllTableColumns allTableColumns) {
        Table table = allTableColumns.getTable();
        if (!StrUtil.isBlankIfStr(table)) {
            table.setName(addDoubleQuote(table.getName()));
        }
    }

    @Override
    public void visit(SelectExpressionItem selectExpressionItem) {
        Alias alias = selectExpressionItem.getAlias();
        if (!StrUtil.isBlankIfStr(alias) && !StrUtil.isBlank(alias.getName())) {
            if (!isQuote(alias.getName())) {
                alias.setName(addDoubleQuote(alias.getName()));
            }
        }
        selectExpressionItem.getExpression().accept(this);
    }

    @Override
    public void visit(BitwiseRightShift bitwiseRightShift) {

    }

    @Override
    public void visit(BitwiseLeftShift bitwiseLeftShift) {

    }

    @Override
    public void visit(NullValue nullValue) {

    }

    @Override
    public void visit(Function function) {
        ExpressionList parameters = function.getParameters();
        if (StrUtil.isBlankIfStr(parameters)) {
            return;
        }
        parameters.getExpressions().forEach(o -> o.accept(this));
    }

    @Override
    public void visit(SignedExpression signedExpression) {
        signedExpression.getExpression().accept(this);
    }

    @Override
    public void visit(JdbcParameter jdbcParameter) {

    }

    @Override
    public void visit(JdbcNamedParameter jdbcNamedParameter) {

    }

    @Override
    public void visit(DoubleValue doubleValue) {

    }

    @Override
    public void visit(LongValue longValue) {

    }

    @Override
    public void visit(HexValue hexValue) {

    }

    @Override
    public void visit(DateValue dateValue) {

    }

    @Override
    public void visit(TimeValue timeValue) {

    }

    @Override
    public void visit(TimestampValue timestampValue) {
    }

    @Override
    public void visit(Parenthesis parenthesis) {
        parenthesis.getExpression().accept(this);
    }

    @Override
    public void visit(StringValue stringValue) {

    }

    @Override
    public void visit(Addition addition) {
        addition.getLeftExpression().accept(this);
        addition.getRightExpression().accept(this);
    }

    @Override
    public void visit(Division division) {
        division.getLeftExpression().accept(this);
        division.getRightExpression().accept(this);
    }

    @Override
    public void visit(IntegerDivision integerDivision) {
    }

    @Override
    public void visit(Multiplication multiplication) {
        multiplication.getLeftExpression().accept(this);
        multiplication.getRightExpression().accept(this);
    }

    @Override
    public void visit(Subtraction subtraction) {
        subtraction.getLeftExpression().accept(this);
        subtraction.getRightExpression().accept(this);

    }

    @Override
    public void visit(AndExpression andExpression) {
        andExpression.getLeftExpression().accept(this);
        andExpression.getRightExpression().accept(this);
    }

    @Override
    public void visit(OrExpression orExpression) {
        orExpression.getLeftExpression().accept(this);
        orExpression.getRightExpression().accept(this);
    }

    @Override
    public void visit(Between between) {
        between.getLeftExpression().accept(this);
        between.getBetweenExpressionStart().accept(this);
        between.getBetweenExpressionEnd().accept(this);
    }

    @Override
    public void visit(EqualsTo equalsTo) {
        equalsTo.getLeftExpression().accept(this);
        equalsTo.getRightExpression().accept(this);
    }

    @Override
    public void visit(GreaterThan greaterThan) {
        greaterThan.getLeftExpression().accept(this);
        greaterThan.getRightExpression().accept(this);

    }

    @Override
    public void visit(GreaterThanEquals greaterThanEquals) {
        greaterThanEquals.getLeftExpression().accept(this);
        greaterThanEquals.getRightExpression().accept(this);
    }

    @Override
    public void visit(InExpression inExpression) {
        inExpression.getLeftExpression().accept(this);
    }

    @Override
    public void visit(FullTextSearch fullTextSearch) {
        fullTextSearch.getMatchColumns().forEach(o -> o.accept(this));
    }

    @Override
    public void visit(IsNullExpression isNullExpression) {
        isNullExpression.getLeftExpression().accept(this);
    }

    @Override
    public void visit(IsBooleanExpression isBooleanExpression) {
        isBooleanExpression.getLeftExpression().accept(this);
    }

    @Override
    public void visit(LikeExpression likeExpression) {
        likeExpression.getLeftExpression().accept(this);
        likeExpression.getRightExpression().accept(this);
    }

    @Override
    public void visit(MinorThan minorThan) {
        minorThan.getLeftExpression().accept(this);
        minorThan.getRightExpression().accept(this);
    }

    @Override
    public void visit(MinorThanEquals minorThanEquals) {
        minorThanEquals.getLeftExpression().accept(this);
        minorThanEquals.getRightExpression().accept(this);
    }

    @Override
    public void visit(NotEqualsTo notEqualsTo) {
        notEqualsTo.getLeftExpression().accept(this);
        notEqualsTo.getRightExpression().accept(this);
    }

    @Override
    public void visit(Column column) {
        Table table = column.getTable();
        if (!StrUtil.isBlankIfStr(table)) {
            table.setName(addDoubleQuote(table.getName()));
        }
        String columnName = column.getColumnName();
        if (isQuote(columnName)) return;
        column.setColumnName(addDoubleQuote(columnName));
    }

    @Override
    public void visit(Table table) {
        String name = table.getName();
        if (!StrUtil.isBlankIfStr(name)) {
            table.setName(addDoubleQuote(name));
        }
        String schemaName = table.getSchemaName();
        if (StrUtil.isBlankIfStr(schemaName)) {
            table.setSchemaName("\"" + hBaseAPISource.getDbName() + "\"");
        } else {
            if (!isQuote(schemaName)) {
                table.setSchemaName(addDoubleQuote(schemaName));
            }
        }
        Alias alias = table.getAlias();
        if (!StrUtil.isBlankIfStr(alias)) {
            alias.setName(addDoubleQuote(alias.getName()));
        }
    }

    @Override
    public void visit(SubSelect subSelect) {

    }

    @Override
    public void visit(SubJoin subJoin) {

    }

    @Override
    public void visit(LateralSubSelect lateralSubSelect) {

    }

    @Override
    public void visit(ValuesList valuesList) {

    }

    @Override
    public void visit(TableFunction tableFunction) {

    }

    @Override
    public void visit(ParenthesisFromItem parenthesisFromItem) {

    }

    @Override
    public void visit(CaseExpression caseExpression) {

    }

    @Override
    public void visit(WhenClause whenClause) {

    }

    @Override
    public void visit(ExistsExpression existsExpression) {

    }

    @Override
    public void visit(AllComparisonExpression allComparisonExpression) {

    }

    @Override
    public void visit(AnyComparisonExpression anyComparisonExpression) {

    }

    @Override
    public void visit(Concat concat) {

    }

    @Override
    public void visit(Matches matches) {

    }

    @Override
    public void visit(BitwiseAnd bitwiseAnd) {

    }

    @Override
    public void visit(BitwiseOr bitwiseOr) {

    }

    @Override
    public void visit(BitwiseXor bitwiseXor) {

    }

    @Override
    public void visit(CastExpression castExpression) {

    }

    @Override
    public void visit(Modulo modulo) {

    }

    @Override
    public void visit(AnalyticExpression analyticExpression) {

    }

    @Override
    public void visit(ExtractExpression extractExpression) {

    }

    @Override
    public void visit(IntervalExpression intervalExpression) {

    }

    @Override
    public void visit(OracleHierarchicalExpression oracleHierarchicalExpression) {

    }

    @Override
    public void visit(RegExpMatchOperator regExpMatchOperator) {

    }

    @Override
    public void visit(JsonExpression jsonExpression) {

    }

    @Override
    public void visit(JsonOperator jsonOperator) {

    }

    @Override
    public void visit(RegExpMySQLOperator regExpMySQLOperator) {

    }

    @Override
    public void visit(UserVariable userVariable) {

    }

    @Override
    public void visit(NumericBind numericBind) {

    }

    @Override
    public void visit(KeepExpression keepExpression) {

    }

    @Override
    public void visit(MySQLGroupConcat mySQLGroupConcat) {

    }

    @Override
    public void visit(ValueListExpression valueListExpression) {

    }

    @Override
    public void visit(RowConstructor rowConstructor) {

    }

    @Override
    public void visit(OracleHint oracleHint) {

    }

    @Override
    public void visit(TimeKeyExpression timeKeyExpression) {

    }

    @Override
    public void visit(DateTimeLiteralExpression dateTimeLiteralExpression) {

    }

    @Override
    public void visit(NotExpression notExpression) {

    }

    @Override
    public void visit(NextValExpression nextValExpression) {

    }

    @Override
    public void visit(CollateExpression collateExpression) {

    }

    @Override
    public void visit(SimilarToExpression similarToExpression) {

    }

    @Override
    public void visit(ArrayExpression arrayExpression) {

    }

    @Override
    public void visit(PlainSelect plainSelect) {
        plainSelect.getSelectItems().forEach(o -> o.accept(this));
        plainSelect.getFromItem().accept(this);
        Expression where = plainSelect.getWhere();
        if (!StrUtil.isBlankIfStr(where)) {
            where.accept(this);
        }
        List<OrderByElement> orderByElements = plainSelect.getOrderByElements();
        if (!StrUtil.isBlankIfStr(orderByElements)) {
            orderByElements.forEach(o -> o.accept(this::visit));
        }
        GroupByElement groupBy = plainSelect.getGroupBy();
        if (!StrUtil.isBlankIfStr(groupBy)) {
            groupBy.accept(this::visit);
        }
        Expression having = plainSelect.getHaving();
        if (!StrUtil.isBlankIfStr(having)) {
            having.accept(this);
        }
    }

    @Override
    public void visit(SetOperationList setOperationList) {

    }

    @Override
    public void visit(WithItem withItem) {

    }

    @Override
    public void visit(ValuesStatement valuesStatement) {

    }

    @Override
    public void visit(GroupByElement groupByElement) {
        List<Expression> groupByExpressions = groupByElement.getGroupByExpressions();
        groupByExpressions.forEach(o -> o.accept(this));
    }

    @Override
    public void visit(OrderByElement orderByElement) {
        Expression expression = orderByElement.getExpression();
        expression.accept(this);
    }
}
