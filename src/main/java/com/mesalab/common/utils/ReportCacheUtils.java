package com.mesalab.common.utils;

import com.google.common.collect.Maps;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.common.enums.QueryFormatEnum;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.qgw.model.api.ApiParam;
import com.mesalab.qgw.model.api.HBaseHttpSource;
import com.mesalab.qgw.service.impl.HttpClientService;
import com.zdjizhi.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * @author gouge
 * @version 1.0
 * @date 2020/6/5 2:04 下午
 */
@Slf4j
public class ReportCacheUtils {

    private static HBaseHttpSource hBaseHttpSource = (HBaseHttpSource) SpringContextUtil.getBean("hBaseHttpSource");
    private static HttpClientService httpClientService = (HttpClientService) SpringContextUtil.getBean("httpClientService");

    public static BaseResult<Object> get(ApiParam param) {
        BaseResult<Object> baseResult;
        StringBuilder urlBuilder = new StringBuilder("http://")
                .append(hBaseHttpSource.getUrl()).append("/");

        StringBuilder queryParamBuilder = new StringBuilder(hBaseHttpSource.getDbName())
                .append(":").append(hBaseHttpSource.getTableName()).append("/")
                .append(getQueryId(param.getResultId(), param.getQuery())).append("/")
                .append(hBaseHttpSource.getColumnFamily()).append(":").append(hBaseHttpSource.getColumnName());

        Map<String, String> headers = Maps.newHashMap();
        headers.put("Accept", "application/json");

        Map<String, String> results = httpClientService.httpGet(urlBuilder.toString() + queryParamBuilder.toString(), headers);

        if (results.get("status").equals(String.valueOf(HttpStatus.SC_OK))) {
            Map result = (Map) JsonMapper.fromJsonString(decryptHbaseResult(results.get("result")), Map.class);
            baseResult = BaseResultGenerator.generate(Integer.parseInt(results.get("status")), ResultCodeEnum.EXECUTE_SUCCESS.getCode(), String.valueOf(result.get("message")),
                    result.get("data"), result.get("meta"), (Map<String, Object>) (result.get("statistics")), QueryFormatEnum.JSON.getValue());
        } else {
            baseResult = BaseResultGenerator.generate(Integer.parseInt(results.get("status")), ResultCodeEnum.SQL_EXECUTION_ERROR.getCode(), null,
                    results.get("result"), null, null, param.getFormat());
        }
        return baseResult;
    }


    /**
     * 解析json并将结果进行base64解密
     *
     * @param hbaseResult
     * @return
     */
    private static String decryptHbaseResult(String hbaseResult) {
        String result = StringUtil.EMPTY;
        try {
            Map<String, Object> maps = (Map<String, Object>) JsonMapper.fromJsonString(hbaseResult, Map.class);
            List<Object> rowList = (ArrayList) maps.get("Row");
            if (StringUtil.isNotEmpty(rowList)) {
                Map<String, Object> keyMap = (Map<String, Object>) rowList.get(0);
                String encryptResult = ((Map<String, Object>) ((ArrayList) keyMap.get("Cell")).get(0)).get("$").toString();
                result = new String(Base64.getDecoder().decode(encryptResult), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("Parser HBase Result error: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 通过SQL生成查询唯一kEY
     *
     * @param sql
     * @param resultId
     * @return
     */
    public static String getQueryId(String resultId, String sql) {
        return DigestUtils.md5Hex(resultId + sql.trim());
    }
}
