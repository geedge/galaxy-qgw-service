package com.mesalab.knowledge.controller;

import com.mesalab.common.base.BaseResult;
import com.mesalab.common.base.BaseResultGenerator;
import com.mesalab.knowledge.entity.DSLObject;
import com.mesalab.knowledge.enums.QueryTypeEnum;
import com.mesalab.knowledge.service.KnowledgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@RestController
@RequestMapping(value = "/knowledge")
public class KnowledgeController {

    @Autowired
    KnowledgeService knowledgeService;

    @GetMapping("/info")
    public String info() {
        return "You Konow, For Graph Search!";
    }

    /***
     * @Description //TODO
     * @Date 2020\7\16
     * @Param [request, dSLObject]
     * @return com.mesalab.common.base.BaseResult
     **/
    @PostMapping(value = "/v1/", produces = "application/json")
    public BaseResult knowledgeQuery(HttpServletRequest request,  @RequestBody DSLObject dSLObject) {
        if (QueryTypeEnum.match(request.getQueryString()) != null) {
            log.info("服务推荐接口, 参数: queryString is {},params is {}", request.getQueryString(), dSLObject);
            return knowledgeService.query(dSLObject);
        } else {
            log.error("服务推荐接口, 错误:queryString is {},params is {}", request.getQueryString(), dSLObject);
            return BaseResultGenerator.failure("Support protocol Only , Now");
        }
    }
}
