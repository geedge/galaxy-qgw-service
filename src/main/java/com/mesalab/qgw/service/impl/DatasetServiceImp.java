package com.mesalab.qgw.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.alibaba.fastjson2.JSON;
import com.geedgenetworks.utils.DateUtils;
import com.geedgenetworks.utils.StringUtil;
import com.google.common.collect.Lists;
import com.jfinal.plugin.activerecord.Db;
import com.jfinal.plugin.activerecord.Record;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.enums.*;
import com.mesalab.common.nacos.NacosConst;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.basic.*;
import com.mesalab.qgw.service.DatabaseService;
import com.mesalab.qgw.service.QueryJobService;
import com.mesalab.qgw.service.SQLSyncQueryService;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.mesalab.qgw.service.DatasetService;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service("datasetService")
@PropertySource(value = "classpath:http-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class DatasetServiceImp
        implements DatasetService {
    private static final Log log = LogFactory.get();
    private static Pattern pFieldVariable = Pattern.compile("\\$\\{(metric|dimension)_(.*?)\\}", Pattern.CASE_INSENSITIVE);
    private static Pattern pLeftRightFlag = Pattern.compile("\\[\\[(.*?)\\]\\]", Pattern.CASE_INSENSITIVE);
    private final static String TEMPLATE = "template";
    private final static String BACKEND_ENGINE = "backend_engine";

    private final static String DATASET_TYPE = "type";
    @Autowired
    Environment env;
    @Autowired
    DatabaseService databaseService;
    @Autowired
    SQLSyncQueryService sqlSyncQueryService;
    @Autowired
    private QueryJobService queryJobService;

    @Override
    public List<LinkedHashMap> getVariable() {
        Object codeInfo = databaseService.getCfg(NacosConst.DATASETS_VARIABLES);
        if (StringUtil.isNotEmpty(codeInfo)) {
            Object json = JSON.toJSON(codeInfo);
            List<LinkedHashMap> variables = JSON.parseArray(json.toString(), LinkedHashMap.class);
            for (LinkedHashMap variable : variables) {
                String key = String.valueOf(variable.get("key"));
                String def = String.valueOf(variable.get("default"));
                Date currentDate = DateUtils.convertStringToDate(DateUtils.getCurrentDate(DateUtils.YYYY_MM_DD_HH24_MM_SS), DateUtils.YYYY_MM_DD_HH24_MM_SS);
                if ("start_time".equals(key) && StringUtil.isBlank(def)) {
                    def = DateUtils.getFormatDate(DateUtils.getSomeHour(currentDate, -1), DateUtils.YYYY_MM_DD_HH24_MM_SS);
                    variable.put("default", def);
                }
                if ("end_time".equals(key) && StringUtil.isBlank(def)) {
                    def = DateUtils.getFormatDate(currentDate, DateUtils.YYYY_MM_DD_HH24_MM_SS);
                    variable.put("default", def);
                }
            }
            return variables;
        }
        return Lists.newArrayList();
    }

    @Override
    public Map<String, Object> getDataset(String datasetId) {
        List<Record> list = Db.find(buildGetDatasetSQL(Lists.newArrayList(datasetId), null, null));
        if (CollectionUtils.isEmpty(list)) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), QGWMessageConst.DATASET_ID_NOT_EXIST));
        }
        List<Map<String, Object>> results = formatResultData(list);
        Map<String, Object> result = results.get(0);
        log.info("ID is: {}, Dataset is: {}", datasetId, result);
        return result;
    }

    @Override
    public List<Map<String, Object>> getDatasets(List<String> ids, String category, String backendEngine) {
        String sql = buildGetDatasetSQL(ids, category, backendEngine);
        List<Record> list = Db.find(sql);
        if (CollectionUtils.isEmpty(list)) {
            return Lists.newArrayList();
        }
        return formatResultData(list);
    }

    private static List<Map<String, Object>> formatResultData(List<Record> list) {
        List<Map<String, Object>> results = new ArrayList<>();
        list.forEach(record -> {
            Map<String, Object> columns = record.getColumns();
            columns.put("generated_time", Instant.ofEpochSecond(Long.parseLong(String.valueOf(columns.get("generated_time")))));
            columns.put("last_update_time", Instant.ofEpochSecond(Long.parseLong(String.valueOf(columns.get("last_update_time")))));
            results.add(record.getColumns());
        });
        return results;
    }

    @Override
    public BaseResult getPreview(String datasetId) {
        Map<String, Object> dataset = getDataset(datasetId);
        String template = buildExecDSL(getVariable(), String.valueOf(dataset.get(TEMPLATE)));
        String datasetType = String.valueOf(dataset.get(DATASET_TYPE));
        if (datasetType.equalsIgnoreCase("sql")) {
            SqlQueryRequestParam sqlQueryRequest = JSON.parseObject(template, SqlQueryRequestParam.class);
            sqlQueryRequest.setExecutionMode(ExecutionMode.ONESHOT.getValue());
            log.info("Dataset Preview, ID is: {}, Type is: {}, Exec SQL is: {}", datasetId, "sql", sqlQueryRequest.getStatement());
            return queryJobService.createSQLAdHocQuery(sqlQueryRequest);
        } else if (datasetType.equalsIgnoreCase("dsl")) {
            DSLQueryRequestParam dslQueryRequest = JSON.parseObject(template, DSLQueryRequestParam.class);
            dslQueryRequest.setExecutionMode(ExecutionMode.ONESHOT.getValue());
            log.info("Dataset Preview, ID is: {}, Type is: {}, DSL is: {}", datasetId, "dsl", template);
            return queryJobService.createDSLAdHocQuery(dslQueryRequest);
        } else {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.BAD_REQUEST_PARAM_SYNTAX_EXCEPTION.getMessage(), "not Supported"));
        }
    }


    @Override
    public String buildExecDSL(List<LinkedHashMap> variables, String template) {
        Matcher matcher = pLeftRightFlag.matcher(template);
        while (matcher.find()) {
            template = processOptionalClause(template, "[[", "]]");
        }
        for (LinkedHashMap linkedHashMap : variables) {
            String variable = String.valueOf(linkedHashMap.get("key"));
            Object def = linkedHashMap.get("default");
            String parameter;
            if (def instanceof Map) {
                parameter = "\"${".concat(variable).concat("}\"");
            } else {
                parameter = "${".concat(variable).concat("}");
            }
            template = template.replace(parameter, String.valueOf(def));
        }
        template = processFieldVariable(template);
        return template;
    }

    private String buildGetDatasetSQL(List<String> ids, String category, String backendEngine) {
        List<String> filterList = Lists.newArrayList();
        if (!ids.isEmpty()) {
            filterList.add(" identifier_name IN ( '".concat(String.join("', '", ids)).concat("')"));
        }
        if (StrUtil.isNotBlank(category)) {
            filterList.add(" category = '".concat(category.replace("'", "\\'")).concat("'"));
        }
        if (StrUtil.isNotBlank(backendEngine)) {
            filterList.add(" backend_engine = '".concat(backendEngine.replace("'", "\\'")).concat("'"));
        }
        String filter = String.join(" AND ", filterList);
        return String.format(Objects.requireNonNull(env.getProperty("SQL_DATASETS")), StrUtil.isNotBlank(filter) ? "WHERE ".concat(filter) : "");
    }

    private String processFieldVariable(String str) {
        Matcher matcher = pFieldVariable.matcher(str);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, matcher.group(2));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String processOptionalClause(String str, String leftFlag, String rightFlag) {
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
}
