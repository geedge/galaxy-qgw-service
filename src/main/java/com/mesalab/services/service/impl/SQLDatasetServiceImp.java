package com.mesalab.services.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.geedgenetworks.utils.Encodes;
import com.geedgenetworks.utils.StringUtil;
import com.google.common.base.CaseFormat;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayway.jsonpath.JsonPath;
import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.*;
import com.mesalab.common.nacos.NacosConst;
import com.mesalab.common.utils.sqlparser.SQLHelper;
import com.mesalab.common.utils.sqlparser.SQLVisitorUtil;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.dialect.Dialect;
import com.mesalab.qgw.dialect.HbaseDialect;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.qgw.exception.QGWErrorCode;
import com.mesalab.qgw.model.basic.*;
import com.mesalab.qgw.service.MetadataService;
import com.mesalab.qgw.service.QueryService;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.mesalab.services.service.SQLDatasetService;
import lombok.Data;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.commons.collections.CollectionUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TODO
 *
 * @Classname SQLDatasetServiceImp
 * @Date 2023/8/10 13:54
 * @Author wWei
 */
@Service("SQLDatasetService")
@PropertySource(value = "classpath:http-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class SQLDatasetServiceImp
        implements SQLDatasetService
{
    private static final Log log = LogFactory.get();
    private static Pattern pFieldVariable = Pattern.compile("\\$\\{(metric|dimension)_(.*?)\\}", Pattern.CASE_INSENSITIVE);
    private static Pattern pLeftRightFlag = Pattern.compile("\\[\\[(.*?)\\]\\]", Pattern.CASE_INSENSITIVE);
    private final static String TEMPLATE = "template";
    private final static String EXECUTE_ENGINE = "execute_engine";

    public final static String queryIdSeparator = ":";

    @Autowired
    Environment env;
    @Autowired
    MetadataService metadataService;
    @Autowired
    QueryService queryService;
    @Autowired
    private HttpClientService httpClientService;
    @Autowired
    private HttpConfig httpConfig;
    @Autowired
    private ClickHouseHttpSource clickHouseHttpSource;
    @Autowired
    private DruidIoHttpSource druidIoHttpSource;

    @Override
    public BaseResult getProcessesByQueryId(String queryId) {
        Dialect dialect = buildAndGetDialect(queryId);
        return dialect.getProcesses(queryId);
    }

    @Override
    public BaseResult deleteQueryTask(String queryId) {
        Dialect dialect = buildAndGetDialect(queryId);
        return dialect.executeKillQuery(queryId);
    }

    @Override
    public String getCustomQueryId(String resultId, String query) {
        String tableName = SQLHelper.getTableName(query).get(0);
        String dbType = metadataService.getDBTypeByTableName(tableName);
        return DigestUtil.md5Hex(dbType) + queryIdSeparator + DigestUtil.md5Hex(resultId + query.trim());
    }

    @Override
    public List<LinkedHashMap> getVariable()
    {
        Object codeInfo = metadataService.getCfg(NacosConst.SQL_DATASETS_VARIABLES);
        if (StringUtil.isNotEmpty(codeInfo)) {
            Object json = JSON.toJSON(codeInfo);
            return JSON.parseArray(json.toString(), LinkedHashMap.class);
        }
        return Lists.newArrayList();
    }

    @Override
    public Map<String, Object> getDataset(String datasetId)
    {
        List<Map<String, Object>> results = new ArrayList<>();
        List<Record> list = Db.find(buildGetDatasetSQL(Lists.newArrayList(datasetId), null, null));
        if (CollectionUtils.isEmpty(list)) {
            return Maps.newHashMap();
        }
        list.forEach(record -> results.add(record.getColumns()));
        Map<String, Object> result = results.get(0);
        log.info("SQL Dataset, ID is: {}, SQL is: {}", datasetId, result);
        return result;
    }

    @Override
    public Map<String, Object> getDatasets(List<String> ids, String category, String title)
    {
        String sql = buildGetDatasetSQL(ids, category, title);
        List<Map<String, Object>> data = new ArrayList<>();
        List<Record> list = Db.find(sql);
        if (CollectionUtils.isEmpty(list)) {
            return Maps.newHashMap();
        }
        list.forEach(record -> data.add(record.getColumns()));
        Map<String, Object> result = Maps.newHashMap();
        result.put("list", data);
        return result;
    }

    @Override
    public BaseResult getPreview(String datasetId, String option) {
        Map<String, Object> dataset = getDataset(datasetId);
        if (dataset.isEmpty() || StrUtil.isBlankIfStr(dataset.get(TEMPLATE))) {
            return BaseResultGenerator.success();
        }
        String sqlTemplate = String.valueOf(dataset.get(TEMPLATE));
        String execute_engine = String.valueOf(dataset.get(EXECUTE_ENGINE));
        String sql = buildExecSQL(getVariable(), sqlTemplate);
        log.info("Dataset Preview, ID is: {}, Exec SQL is: {}", datasetId, sql);
        if (DiagnosisOptionEnum.EXECUTION.getValue().equalsIgnoreCase(option) || StrUtil.isBlank(option)) {
            if (DBTypeEnum.CLICKHOUSE.getValue().equalsIgnoreCase(execute_engine)) {
                return generateClickHouseBaseResult(sql);
            }
            if (DBTypeEnum.DRUID.getValue().equalsIgnoreCase(execute_engine)) {
                return generateDruidBaseResult(sql);
            }
            if (DBTypeEnum.HBASE.getValue().equalsIgnoreCase(execute_engine)) {
                return generateHbaseBaseResult(sql);
            }
            return getBaseResultByEngine(QueryProfile.builder().option(QueryOptionEnum.REAL_TIME.getValue()).query(sql).build());
        } else if (DiagnosisOptionEnum.PARSE.getValue().equalsIgnoreCase(option)) {
            return getBaseResultByEngine(QueryProfile.builder().option(QueryOptionEnum.SYNTAX_PARSE.getValue()).query(sql).build());
        } else if (DiagnosisOptionEnum.VALIDATION.getValue().equalsIgnoreCase(option)) {
            return getBaseResultByEngine(QueryProfile.builder().option(QueryOptionEnum.SYNTAX_VALIDATION.getValue()).query(sql).build());
        } else {
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(),
                    ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.OPTION_IS_NOT_SUPPORTED));
        }
    }


    @Override
    public String buildExecSQL(List<LinkedHashMap> variables, String sql)
    {
        Matcher matcher = pLeftRightFlag.matcher(sql);
        while (matcher.find()) {
            sql = processOptionalClause(sql, "[[", "]]");
        }
        sql = processFieldVariable(sql);
        for (LinkedHashMap linkedHashMap : variables) {
            String variable = String.valueOf(linkedHashMap.get("key"));
            String def = String.valueOf(linkedHashMap.get("default"));
            if ("filter".equals(variable) && StringUtil.isBlank(def)) {
                def = " 1 = 1";
            }
            String parameter = "${".concat(variable).concat("}");
            sql = sql.replace(parameter, def);
        }
        return sql;
    }

    public Dialect buildAndGetDialect(String queryId) {
        QueryProfile queryProfile = new QueryProfile();
        queryProfile.setDbType(parseDBType(queryId));
        return getDialect(queryProfile);
    }

    private String parseDBType(String queryId) {
        for (DBTypeEnum dbTypeEnum : DBTypeEnum.values()) {
            if (queryId.startsWith(DigestUtil.md5Hex(dbTypeEnum.getValue()).concat(queryIdSeparator))) {
                return dbTypeEnum.getValue();
            }
        }
        if (queryId.contains(queryIdSeparator)) {
            throw new QGWBusinessException(ResultStatusEnum.NOT_FOUND.getCode(), ResultCodeEnum.PARAMETER_ERROR.getCode(),
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(),QGWMessageConst.QUERY_ID_IS_NOT_SUPPORTED));
        }
        return DBTypeEnum.CLICKHOUSE.getValue();
    }

    private Dialect getDialect(QueryProfile queryProfile) {
        Dialect dialect;
        try {
            Class dialectClazz = Class.forName("com.mesalab.qgw.dialect." +
                    CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, queryProfile.getDbType()) + "Dialect");
            Constructor constructor = dialectClazz.getConstructor(QueryProfile.class);
            dialect = (Dialect) constructor.newInstance(queryProfile);
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.error("Dialect conversion instance exception:{}", e);
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.SQL_SYNTAX_PARSE_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.SQL_SYNTAX_PARSE_EXCEPTION.getMessage(),e.getMessage()));
        }
        return dialect;
    }

    private String buildGetDatasetSQL(List<String> ids, String category, String title)
    {
        List<String> filterList = Lists.newArrayList();
        if (!ids.isEmpty()) {
            filterList.add(" identifier_name IN ( '".concat(String.join("', '", ids)).concat("')"));
        }
        if (StrUtil.isNotBlank(category)) {
            filterList.add(" category = '".concat(category.replace("'", "\\'")).concat("'"));
        }
        if (StrUtil.isNotBlank(title)) {
            filterList.add(" title = '".concat(title.replace("'", "\\'")).concat("'"));
        }
        String filter = String.join(" AND ", filterList);
        return String.format(Objects.requireNonNull(env.getProperty("SQL_DATASETS")), StrUtil.isNotBlank(filter) ? "WHERE ".concat(filter) : "");
    }

    private BaseResult getBaseResultByEngine(QueryProfile queryProfile) {
        return queryService.executeQuery(queryProfile);
    }

    private BaseResult generateDruidBaseResult(String sql) {
        BaseResult baseResult;
        Object dataObject;
        Stopwatch watch = Stopwatch.createUnstarted();
        watch.start();
        Map<String, String> results = druidExecuteHttpPost(sql);
        if (Integer.parseInt(results.get("status")) == ResultStatusEnum.SUCCESS.getCode()) {
            baseResult = BaseResultGenerator.generate(Integer.valueOf(results.get("status")), ResultCodeEnum.SUCCESS.getCode(), "ok",
                    (List<Object>) JSON.parseObject(results.get("result"), Object.class), null, null, QueryFormatEnum.JSON.getValue());
            dataObject = baseResult.getData();
            List<Object> allResults = (List<Object>) dataObject;
            Map<String, Object> statistics = Maps.newLinkedHashMap();
            statistics.put("elapsed", watch.elapsed(TimeUnit.MILLISECONDS));
            statistics.put("rows_read", allResults.size());
            baseResult.setData(allResults);
            baseResult.setStatistics(statistics);
        } else {
            baseResult = BaseResultGenerator.generate(Integer.parseInt(results.get("status")), QGWErrorCode.SQL_BUILDER_EXCEPTION.getCode(), results.get("message"),
                    results.get("result"), null, null, QueryFormatEnum.JSON.getValue());
            dataObject = baseResult.getData();
            baseResult.setMessage("SQL Execution Error: ".concat(JsonPath.read(dataObject.toString(), "$.errorMessage")));
        }
        return baseResult;
    }


    private Map<String, String> druidExecuteHttpPost(String sql) {
        String queryURL = URLUtil.normalize(druidIoHttpSource.getUrl());
        DruidQueryParam druidQueryParam = new DruidQueryParam();
        druidQueryParam.setQuery(sql);
        druidQueryParam.getContext().put("skipEmptyBuckets", druidIoHttpSource.getSkipEmptyBuckets());
        druidQueryParam.setResultFormat("object");
        log.info("DB Engine is :{}, Execute Query is: {}", DBTypeEnum.DRUID.getValue(), JSON.toJSONString(druidQueryParam));
        int socketTimeOut = httpConfig.getDruidSocketTimeOut();
        return httpClientService.httpPost(queryURL, JSON.toJSONString(druidQueryParam), socketTimeOut);
    }


    /**
     * @param sql
     * @return com.mesalab.common.base.BaseResult
     * @Description ck SQL测试集在ClickHouse数据源查询并获取查询结果
     * @author wanghao
     * @date 2021/9/9 17:39
     */
    private BaseResult generateClickHouseBaseResult(String sql) {
        BaseResult baseResult;
        Object dataObject;
        Map<String, String> results = clickHouseExecuteHttpPost(sql);
        if (Integer.parseInt(results.get("status")) == ResultStatusEnum.SUCCESS.getCode()) {
            baseResult = BaseResultGenerator.generate(Integer.parseInt(results.get("status")), ResultCodeEnum.SUCCESS.getCode(), "ok",
                    JSON.parseObject(results.get("result"), Map.class), null, null, QueryFormatEnum.JSON.getValue());
            dataObject = baseResult.getData();
            Map<String, Object> resultMap = (Map<String, Object>) dataObject;
            Map<String, Object> statisticMap = (Map<String, Object>) resultMap.get("statistics");
            Map<String, Object> statistics = Maps.newLinkedHashMap();
            statistics.put("elapsed", Math.round(Double.parseDouble(String.valueOf(statisticMap.get("elapsed"))) * 1000));
            statistics.put("rows_read", statisticMap.get("rows_read"));
            baseResult.setData(resultMap.get("data"));
            baseResult.setStatistics(statistics);
        } else {
            baseResult = BaseResultGenerator.generate(Integer.parseInt(results.get("status")), QGWErrorCode.SQL_BUILDER_EXCEPTION.getCode(), null,
                    results.get("result"), null, null, QueryFormatEnum.JSON.getValue());
            dataObject = baseResult.getData();
            baseResult.setStatus((baseResult.getStatus() < 500) || (baseResult.getStatus() > 400) ? ResultStatusEnum.SERVER_ERROR.getCode() : baseResult.getStatus());
            baseResult.setMessage("SQL Execution Error: ".concat(JsonPath.read(dataObject.toString(), "$.errorMessage")));
        }
        return baseResult;
    }

    private Map<String, String> clickHouseExecuteHttpPost(String sql) {
        String ckQuery = Encodes.urlEncode(sql);
        String queryURL = URLUtil.normalize(clickHouseHttpSource.getUrl() + "/?");
        StringBuilder queryParamBuilder = new StringBuilder("user=")
                .append(clickHouseHttpSource.getRealTimeAccountUserName()).append("&")
                .append("password=").append(clickHouseHttpSource.getRealTimeAccountPin()).append("&")
                .append("database=").append(clickHouseHttpSource.getDbName());
        ClickHouseHttpQuery clickHouseHttpQuery = new ClickHouseHttpQuery();
        clickHouseHttpQuery.setSocketTimeOut(httpConfig.getCkRealTimeAccountSocketTimeOut());
        clickHouseHttpQuery.setQueryParameter(queryParamBuilder.toString());
        log.info("DB Engine is :{}, Execute Http Query is: {}", DBTypeEnum.CLICKHOUSE.getValue(), JSON.toJSONString(clickHouseHttpQuery));
        List<NameValuePair> values = URLEncodedUtils.parse(clickHouseHttpQuery.getQueryParameter(), Charset.forName("UTF-8"));
        return httpClientService.httpPost(queryURL + URLEncodedUtils.format(values, "utf-8"), sql.concat(" FORMAT ").concat(QueryFormatEnum.JSON.getValue().toUpperCase()), clickHouseHttpQuery.getSocketTimeOut());
    }



    /**
     * @param sql
     * @return com.mesalab.common.base.BaseResult
     * @Description hbase SQL测试集在hbase数据源查询并获取查询结果
     * @author wanghao
     * @date 2021/11/11 10:01
     */
    private BaseResult generateHbaseBaseResult(String sql) {
        BaseResult baseResult = null;
        HbaseDialect hbaseDialect = new HbaseDialect(queryBuild(sql));
        baseResult = hbaseDialect.generateBaseResult(converter(sql), Optional.of("OK"));
        return baseResult;
    }

    private String converter(String sql) {
        try {
            Select select = (Select) CCJSqlParserUtil.parse(sql);
            select.getSelectBody().accept(SQLVisitorUtil.getVisitorOfEscapeMetadataWithDoubleQuote());
            return select.toString();
        } catch (JSQLParserException | RuntimeException e) {
            log.error("SQL Syntax Error: Converter SQL Syntax Error, SQL is:{}, Error is:{}", sql, e);
            throw new QGWBusinessException(ResultStatusEnum.BAD_REQUEST.getCode(), QGWErrorCode.SQL_SYNTAX_PARSE_EXCEPTION.getCode(),
                    String.format(QGWErrorCode.SQL_SYNTAX_PARSE_EXCEPTION.getMessage(),e.getMessage()));
        }
    }

    private QueryProfile queryBuild(String sql) {
        return QueryProfile.builder().format(QueryFormatEnum.JSON.getValue()).query(sql).build();
    }


    private String processFieldVariable(String str)
    {
        Matcher matcher = pFieldVariable.matcher(str);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, matcher.group(2));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String processOptionalClause(String str, String leftFlag, String rightFlag)
    {
        String left = "";
        String right = "";
        String center = "";
        boolean leftMark = false;
        for (int i = 0; i < str.length(); i++) {
            String element = str.substring(i, i + 1);
            if (leftFlag.startsWith(element) && leftFlag.equals(str.substring(i, i + leftFlag.length()))) {
                left = str.substring(0, i);
                leftMark = true;
                continue;
            }
            if (leftMark) {
                if (rightFlag.startsWith(element) && rightFlag.equals(str.substring(i, i + rightFlag.length()))) {
                    right = str.substring(i + rightFlag.length());
                    center = str.substring(left.length(), str.length() - (right.length()));
                    break;
                }
            }
        }
        if (StrUtil.isNotEmpty(center)) {
            String substring = center.substring(leftFlag.length(), center.length() - leftFlag.length());
            str = left.concat(substring).concat(right);
        }
        return str;
    }
    @Data
    class DruidQueryParam {
        private String query;
        private Map<String, String> context = Maps.newHashMap();
        private String resultFormat;
    }

}
