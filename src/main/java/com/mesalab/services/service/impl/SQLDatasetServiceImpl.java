package com.mesalab.services.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.base.CaseFormat;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.enums.*;
import com.mesalab.common.utils.sqlparser.SQLHelper;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.dialect.Dialect;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.basic.*;
import com.mesalab.qgw.service.DatabaseService;
import com.mesalab.qgw.service.SQLSyncQueryService;
import com.mesalab.services.common.property.SqlPropertySourceFactory;
import com.mesalab.services.service.SQLDatasetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;

/**
 * TODO
 *
 * @Classname SQLDatasetServiceImp
 * @Date 2023/8/10 13:54
 * @Author wWei
 */
@Service("SQLDatasetService")
@PropertySource(value = "classpath:http-sql-template.sql", factory = SqlPropertySourceFactory.class)
public class SQLDatasetServiceImpl
        implements SQLDatasetService {
    private static final Log log = LogFactory.get();

    public final static String queryIdSeparator = ":";

    @Autowired
    private Environment env;
    @Autowired
    private DatabaseService databaseService;
    @Autowired
    private SQLSyncQueryService queryService;

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
        String dbType = databaseService.getDBTypeByTableName(tableName);
        return DigestUtil.md5Hex(dbType) + queryIdSeparator + DigestUtil.md5Hex(resultId + query.trim());
    }


    public Dialect buildAndGetDialect(String queryId) {
        QueryContext queryProfile = new QueryContext();
        queryProfile.setDbType(parseDBType(queryId));
        return getDialect(queryProfile);
    }

    private String parseDBType(String queryId) {
        for (DBType dbTypeEnum : DBType.values()) {
            if (queryId.startsWith(DigestUtil.md5Hex(dbTypeEnum.getValue()).concat(queryIdSeparator))) {
                return dbTypeEnum.getValue();
            }
        }
        if (queryId.contains(queryIdSeparator)) {
            throw new QGWBusinessException(HttpStatusCodeEnum.NOT_FOUND.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.QUERY_ID_IS_NOT_SUPPORTED));
        }
        return DBType.CLICKHOUSE.getValue();
    }

    private Dialect getDialect(QueryContext queryProfile) {
        Dialect dialect;
        try {
            Class dialectClazz = Class.forName("com.mesalab.qgw.dialect." +
                    CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, queryProfile.getDbType()) + "Dialect");
            Constructor constructor = dialectClazz.getConstructor(QueryContext.class);
            dialect = (Dialect) constructor.newInstance(queryProfile);
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.error("Dialect conversion instance exception:{}", e);
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getMessage(), e.getMessage()));
        }
        return dialect;
    }
}
