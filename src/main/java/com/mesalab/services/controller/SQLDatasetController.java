package com.mesalab.services.controller;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.geedgenetworks.utils.StringUtil;
import com.mesalab.common.entity.BaseResult;

import com.mesalab.common.entity.BaseResultGenerator;
import com.mesalab.common.enums.HttpStatusCodeEnum;
import com.mesalab.common.utils.sqlparser.AutoPeriodHelper;
import com.mesalab.qgw.constant.QGWMessageConst;
import com.mesalab.qgw.exception.QGWBusinessException;
import com.mesalab.common.exception.CommonErrorCode;
import com.mesalab.qgw.model.basic.AuditLog;
import com.mesalab.qgw.service.DatabaseService;

import com.mesalab.services.service.SQLDatasetService;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.parser.JJTCCJSqlParserState;
import net.sf.jsqlparser.statement.Statement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * SQL数据集市管理接口
 *
 * @Classname SQLDatasetController
 * @Date 2023/8/10 11:06
 * @Author wWei
 */
@RestController
@RequestMapping(value = "/v1")
public class SQLDatasetController {
    private static final Log log = LogFactory.get();
    private DatabaseService databaseService;
    private SQLDatasetService sqlDatasetService;

    @DeleteMapping(value = "sql/query/{query_id}")
    @AuditLog("DatabaseController.killQuery")
    public BaseResult killQuery(@PathVariable("query_id") String queryId) {
        log.warn("skill query, param: queryId is {}", queryId);
        if (StringUtil.isBlank(queryId)) {
            return BaseResultGenerator.success4Message("ok");
        }
        return sqlDatasetService.deleteQueryTask(queryId);
    }


    @RequestMapping(value = "sql/query/query_id", method = RequestMethod.POST)
    @AuditLog("DatabaseController.getQueryId")
    public BaseResult getQueryId(@RequestParam String query, @RequestParam(value = "result_id", required = false) Integer resultId) {
        log.info("get current SQL Query ID, parma: query ={}，result_id ={}", query, resultId);
        if (StringUtil.isEmpty(query)) {
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.PARAMETER_ERROR.getCode(),
                    String.format(CommonErrorCode.PARAMETER_ERROR.getMessage(), QGWMessageConst.QUERY_CANNOT_BE_EMPTY));
        }
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(query);
        } catch (JSQLParserException e) {
            log.error("Error Parsing SQL: {}", ExceptionUtil.getRootCauseMessage(e));
            throw new QGWBusinessException(HttpStatusCodeEnum.BAD_REQUEST.getCode(), CommonErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getCode(),
                    String.format(CommonErrorCode.BAD_REQUEST_SQL_SYNTAX_PARSE_EXCEPTION.getMessage(), e.getMessage()));
        }
        String queryId = databaseService.getCustomQueryId(resultId == null ? null : resultId.toString(), AutoPeriodHelper.buildSqlGranularity(statement).toString());
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> map = new HashMap<>(16);
        map.put("queryId", queryId);
        result.add(map);
        return BaseResultGenerator.success(result);
    }

    @GetMapping(value = "sql/query/{query_id}/progress")
    @AuditLog("DatabaseController.getProcessesByQueryId")
    public BaseResult getProcessesByQueryId(@PathVariable("query_id") String queryId) {
        log.info("get query status, param: queryId is {}", queryId);
        return sqlDatasetService.getProcessesByQueryId(queryId);
    }


    @Autowired
    public void setDatabaseService(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    @Autowired
    public void setSQLDatasetService(SQLDatasetService sqlDatasetService) {
        this.sqlDatasetService = sqlDatasetService;
    }

}