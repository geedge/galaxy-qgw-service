package com.mesalab.services.controller;

import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.mesalab.common.base.BaseResult;
import com.mesalab.common.enums.ResultCodeEnum;
import com.mesalab.common.enums.ResultStatusEnum;
import com.mesalab.common.exception.BusinessException;
import com.mesalab.services.common.dsl.ComDSLValidate;
import com.mesalab.services.common.dsl.ComDSLObject;
import com.mesalab.services.common.enums.EntityQueryType;
import com.mesalab.services.service.EntityService;
import com.zdjizhi.utils.StringUtil;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
/**
 * @author tiangaochao
 * @version *
 * @date 2021/8/24 15:42 下午
 */
@RestController
@RequestMapping(value = "/entity")
public class EntityController {
    private static final Log log = LogFactory.get();
    @Autowired
    private EntityService entityService;
    @Autowired
    private ComDSLValidate comDSLValidate;

    @PostMapping(value = "/v1/", produces = "application/json")
    public BaseResult EntityQuery(HttpServletRequest request, @Validated @RequestBody ComDSLObject dslObject) {
        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String param = parameterNames.nextElement();
            log.debug("实体推荐接口, 参数: queryString is {},params is {}", param, dslObject);
            if (!EnumUtils.isValidEnum(EntityQueryType.class, StringUtil.upperCase(param))
                    || StringUtil.isNotEmpty(request.getParameter(param))) {
                throw new BusinessException(ResultStatusEnum.FAIL.getCode(),
                        ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(), "Not Support", null);
            }
            comDSLValidate.validation(dslObject);
            return entityService.getEntityInfo(param, dslObject);
        }
        throw new BusinessException(ResultStatusEnum.FAIL.getCode(),
                ResultCodeEnum.ILLEGAL_ARGUMENT_EXCEPTION.getCode(), "Not Support", null);
    }
}
