package com.mesalab.qgw.dialect;

import cn.hutool.core.util.StrUtil;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.entity.DataTypeMapping;
import com.mesalab.common.enums.DBTypeEnum;
import com.mesalab.common.enums.OutputModeEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.utils.SpringContextUtil;
import com.mesalab.common.utils.sqlparser.SQLVisitorUtil;
import com.mesalab.common.utils.sqlparser.SQLFunctionUtil;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.exception.QGWErrorCode;
import com.mesalab.qgw.model.basic.QueryContext;
import com.mesalab.qgw.model.basic.EngineConfigSource;
import com.mesalab.qgw.model.basic.HBaseAPISource;
import com.mesalab.qgw.model.basic.SQLQuerySource;
import com.geedgenetworks.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.Select;


import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author wanghao
 * @Description HbaseDialect
 * @date 2021/10/26 15:20
 */

@Slf4j
public class HbaseDialect extends AbstractDataSourceDialect {
    public QueryContext param;
    public BaseResult baseResult;
    private Stopwatch watch = Stopwatch.createUnstarted();
    private static final String URL_PREFIX = "jdbc:phoenix:";
    private HBaseAPISource hBaseAPISource = (HBaseAPISource) SpringContextUtil.getBean("hBaseAPISource");
    private EngineConfigSource engineConfigSource = (EngineConfigSource) SpringContextUtil.getBean("engineConfigSource");
    private final static Map<String, String> metaMap = Maps.newHashMap();
    public static Properties PROPERTIES = new Properties();

    static {
        try {
            Class.forName("org.apache.phoenix.jdbc.PhoenixDriver");
            PROPERTIES.setProperty("phoenix.schema.isNamespaceMappingEnabled", "true");
            PROPERTIES.setProperty("phoenix.schema.mapSystemTablesToNamespace", "true");
            PROPERTIES.setProperty("zookeeper.recovery.retry.intervalmill", "200");
            PROPERTIES.setProperty("zookeeper.recovery.retry", "3");
            PROPERTIES.setProperty("hbase.client.retries.number", "3");
        } catch (ClassNotFoundException e) {
            log.error("PhoenixDriver load error. ", e);
        }
    }

    static {
        metaMap.put("INTEGER", DataTypeMapping.INT);
        metaMap.put("UNSIGNED_INT", DataTypeMapping.INT);
        metaMap.put("UNSIGNED_LONG", DataTypeMapping.LONG);
        metaMap.put("FLOAT", DataTypeMapping.FLOAT);
        metaMap.put("UNSIGNED_FLOAT", DataTypeMapping.FLOAT);
        metaMap.put("DOUBLE", DataTypeMapping.DOUBLE);
        metaMap.put("UNSIGNED_DOUBLE", DataTypeMapping.DOUBLE);
        metaMap.put("DATE", DataTypeMapping.DATE);
        metaMap.put("TIMESTAMP", DataTypeMapping.TIMESTAMP);
        metaMap.put("VARCHAR", DataTypeMapping.STRING);
    }

    public HbaseDialect(QueryContext param) {
        this.param = param;
    }

    @Override
    void init() {
        if (StringUtil.isNotEmpty(param.getDbQuerySource())) {
            param.getDbQuerySource().setExecSQL(param.getDbQuerySource().getSqlBody());
        } else {
            SQLQuerySource sqlQuerySource = new SQLQuerySource();
            sqlQuerySource.setExecSQL(param.getQuery());
            param.setDbQuerySource(sqlQuerySource);
        }
        baseResult = BaseResultGenerator.success();
    }

    @Override
    String convertQuery(String sql) {
        sql = SQLFunctionUtil.generateDateFunction(sql, DBTypeEnum.HBASE.getValue());
        try {
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            select.getSelectBody().accept(SQLVisitorUtil.getVisitorOfEscapeMetadataWithDoubleQuote());
            return select.toString();
        } catch (JSQLParserException | RuntimeException e) {
            log.error("SQL Syntax Error: Converter SQL Syntax Error, SQL is:{}, Error is:{}", sql, e);
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getMessage(), e.getMessage()));
        }
    }

    @Override
    String getBestQuery() {
        return new Optimizer().generateQueryLimit().generateSQL().build();
    }

    @Override
    String getOriginalQuery() {
        return param.getQuery();
    }

    @Override
    String getSampleQuery() {
        return convertQuery(param.getQuery());
    }

    @Override
    String getExplainQuery() {
        throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getCode(),
                String.format(QGWErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getMessage(), QGWMessageConst.HBASE_DIALECT_UNSUPPORTED_EXPLAIN_SYNTAX));
    }

    @Override
    public String simplyErrorMessage(Object errorMessage) {
        String message = StringUtil.isNotEmpty(errorMessage) ? String.valueOf(errorMessage)
                : "System Runtime Exception, Please contact Galaxy Data Platform Project manager";
        message = message.startsWith("ERROR ") ? message.substring(message.indexOf(":") + 1)
                : message;
        return StrUtil.removeAllLineBreaks(message).trim();
    }

    @Override
    public BaseResult generateBaseResult(String sql, Optional<String> message) {
        watch.start();
        Map<String, Object> results = executeQuery(sql);
        int status = Integer.parseInt(String.valueOf(results.get("status")));
        if (status == ResultStatusEnum.SUCCESS.getCode()) {
            baseResult = BaseResultGenerator.generate(status, ResultCodeEnum.SUCCESS.getCode(), message.get(),
                    results.get("data"), results.get("meta"), (Map<String, Object>) results.get("statistics"), OutputModeEnum.JSON.getValue());
        } else {
            baseResult = BaseResultGenerator.generate(status, QGWErrorCode.SQL_REWRITE_AND_TRANSFORMATION_EXCEPTION.getCode(), String.valueOf(results.get("message")),
                    results.get("data"), results.get("meta"), (Map<String, Object>) results.get("statistics"), param.getFormat());
        }
        return baseResult;
    }

    private Map<String, Object> executeQuery(String sql) {
        Connection conn = null;
        java.sql.Statement statement = null;
        ResultSet resultSet = null;
        try {
            log.info("{} engine execute query: {}", DBTypeEnum.HBASE.getValue(), sql);
            PROPERTIES.setProperty("hbase.rpc.timeout", hBaseAPISource.getRpcTimeout());
            PROPERTIES.setProperty("hbase.client.scanner.timeout.period", hBaseAPISource.getRpcTimeout());
            PROPERTIES.setProperty("phoenix.query.timeoutMs", hBaseAPISource.getRpcTimeout());
            String url = URL_PREFIX.concat(hBaseAPISource.getZookeeperQuorum()).concat(":").concat(hBaseAPISource.getZookeeperZnodeParent());
            conn = DriverManager.getConnection(url, PROPERTIES);
            statement = conn.createStatement();
            resultSet = statement.executeQuery(sql);
            return buildResult(resultSet);
        } catch (RuntimeException | SQLException e) {
            log.error("{} engine error: {}", DBTypeEnum.HBASE.getValue(), e);
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getMessage(),simplyErrorMessage(e.getMessage())));
        } finally {
            close(resultSet, statement, conn);
        }
    }

    private Map<String, Object> buildResult(ResultSet resultSet) throws SQLException {
        Map<String, Object> result = Maps.newHashMap();
        ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
        List<String> metaList = Lists.newArrayList();
        List<Map<String, String>> metaDataList = new ArrayList<>();
        for (int i = 0; i < resultSetMetaData.getColumnCount(); i++) {
            metaList.add(resultSetMetaData.getColumnName(i + 1));
            Map<String, String> metaData = Maps.newHashMap();
            metaData.put("name", metaList.get(i));
            metaData.put("type", metaMap.get(resultSetMetaData.getColumnTypeName(i + 1)));
            metaDataList.add(i, metaData);
        }
        List<Map<String, Object>> dataList = new ArrayList<>();
        while (resultSet.next()) {
            Map<String, Object> item = Maps.newHashMap();
            for (String meta : metaList) {
                item.put(meta, resultSet.getObject(meta));
            }
            dataList.add(item);
        }
        Map<String, Object> statistics = Maps.newLinkedHashMap();
        statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
        statistics.put("result_bytes", dataList.toString().getBytes().length);
        statistics.put("result_rows", dataList.size());
        result.put("status", ResultStatusEnum.SUCCESS.getCode());
        result.put("statistics", statistics);
        result.put("meta", metaDataList);
        result.put("data", dataList);
        return result;
    }

    @Override
    public boolean supportsLimit() {
        return true;
    }

    @Override
    public BaseResult executeKillQuery(String queryId) {
        return BaseResultGenerator.failure(ResultCodeEnum.PARAMETER_ERROR.getCode(), String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),"not support"));
    }

    @Override
    public BaseResult getProcesses(String queryId) {
        return BaseResultGenerator.failure(ResultCodeEnum.PARAMETER_ERROR.getCode(), String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),"not support"));
    }

    class Optimizer {
        String sql = null;

        /**
         * 对SQL最内层的结果数据集进行限制
         *
         * @return
         */
        public Optimizer generateQueryLimit() {

            setDefaultResultRows(StringUtil.isEmpty(param.getEngineQuerySource()) ? param.getDbQuerySource() : param.getEngineQuerySource(), engineConfigSource.getDefaultResultNum());
            return this;
        }

        private void setDefaultResultRows(SQLQuerySource sqlQuerySource, int defaultResultRows) {
            if (StringUtil.isBlank(sqlQuerySource.getLimit())) {
                sqlQuerySource.setSqlBody(sqlQuerySource.getSqlBody() + " limit " + defaultResultRows);
                sqlQuerySource.setLimit(String.valueOf(defaultResultRows));
            }

        }

        public Optimizer generateSQL() {
            SQLQuerySource sqlQuerySource = param.getDbQuerySource();
            sql = sqlQuerySource.getSqlBody();
            return this;
        }

        public String build() {
            return sql;
        }
    }

    private static void close(ResultSet rs, java.sql.Statement st, Connection conn) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                log.error("ResultSet close error : ", e);
            }

        }
        if (st != null) {
            try {
                st.close();
            } catch (SQLException e) {
                log.error("Statement close error : ", e);
            }

        }
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                log.error("Connection close error : ", e);
            }

        }
    }
}
