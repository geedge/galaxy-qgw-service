package com.mesalab.network.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.google.common.collect.Sets;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.network.dsl.DSLObject;
import com.mesalab.network.dsl.DSLValidate;
import com.mesalab.network.service.NetworkMonitorService;
import com.zdjizhi.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashSet;


/**
 * @author wangwei
 * @version 1.0
 * @date 2020/6/30 10:38 上午
 */
@RestController
@RequestMapping(value = "/")
public class NetworkMonitorController {
    private static final Log log = LogFactory.get();
    @Autowired
    private DSLValidate dslValidate;
    @Autowired
    private NetworkMonitorService networkMonitorService;
    private static final String PROTOCOL = "protocol";
    private static final HashSet<String> ROUTES = Sets.newHashSet();

    static {
        ROUTES.add(PROTOCOL);
    }

    @PostMapping(value = "/traffic/v1/", produces = "application/json")
    public BaseResult trafficStatistics(HttpServletRequest request, @Validated @RequestBody DSLObject dslObject) {
        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String param = parameterNames.nextElement();
            if (!ROUTES.contains(StringUtil.lowerCase(param))
                    || StringUtil.isNotEmpty(request.getParameter(param))) {
                throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "Support：" + ROUTES, null);
            }
            log.debug("流量分布接口, 参数: queryString is {}, params is {}", param, dslObject);
            dslValidate.executeValidate(dslObject);
            if (PROTOCOL.equalsIgnoreCase(param)) {
                return networkMonitorService.build(dslObject);
            }
        }
        throw new BusinessException(ResultStatusEnum.FAIL.getCode(), ResultCodeEnum.PARAM_SYNTAX_ERROR.getCode(), "Support：" + ROUTES, null);
    }
}
