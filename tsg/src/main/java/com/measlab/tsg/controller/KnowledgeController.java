package com.measlab.tsg.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.common.enums.ResultCodeEnum;
import com.measlab.tsg.enums.QueryTypeEnum;
import com.measlab.tsg.exception.TSGErrorMessage;
import com.measlab.tsg.service.KnowledgeService;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.zdjizhi.utils.StringUtil;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;

@RestController
@RequestMapping(value = "/knowledge")
public class KnowledgeController {
    private static final Log log = LogFactory.get();
    @Autowired
    KnowledgeService knowledgeService;

    @GetMapping("/info")
    public String info() {
        return "You Know, For Graph Search!";
    }

    /***
     *
     * @Date 2020\7\16
     * @Param [request, dSLObject]
     * @return com.mesalab.common.base.BaseResult
     **/
    @PostMapping(value = "/v1/", produces = "application/json")
    public BaseResult knowledgeQuery(HttpServletRequest request, @RequestBody Object object) {
        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String param = parameterNames.nextElement();
            log.debug("服务推荐接口, 参数: queryString is {},params is {}", param, object);
            if (!EnumUtils.isValidEnum(QueryTypeEnum.class, StringUtil.upperCase(param))
                    || StringUtil.isNotEmpty(request.getParameter(param))) {
                throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(),
                        ResultCodeEnum.PARAMETER_ERROR.getCode(),
                        String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), TSGErrorMessage.NOT_SUPPORT));
            }
            return knowledgeService.query(object);
        }
        throw new BusinessException(ResultStatusEnum.BAD_REQUEST.getCode(),
                ResultCodeEnum.PARAMETER_ERROR.getCode(),
                String.format(ResultCodeEnum.PARAMETER_ERROR.getMessage(), TSGErrorMessage.NOT_SUPPORT));
    }
}
