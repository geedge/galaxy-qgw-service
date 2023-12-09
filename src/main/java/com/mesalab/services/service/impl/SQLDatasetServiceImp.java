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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

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
        implements SQLDatasetService {
    private static final Log log = LogFactory.get();

    public final static String queryIdSeparator = ":";

    @Autowired
    Environment env;
    @Autowired
    MetadataService metadataService;
    @Autowired
    QueryService queryService;

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
                    String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), QGWMessageConst.QUERY_ID_IS_NOT_SUPPORTED));
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
                    String.format(QGWErrorCode.SQL_SYNTAX_PARSE_EXCEPTION.getMessage(), e.getMessage()));
        }
        return dialect;
    }
}
