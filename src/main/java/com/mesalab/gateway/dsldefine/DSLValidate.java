package com.mesalab.gateway.dsldefine;

import com.mesalab.common.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * @Date: 2020-07-20 18:10
 * @Author : liuyongqiang
 * @ClassName : DSLValidate
 * @Description : DSL格式校验
 */
@Component
public class DSLValidate {

    //TODO
    //1.最好设计成Spring容器中的Bean
    //2.所有不符合校验规则的提示,以BusinessException抛出
    //3.所有校验规则的方法在内部定义,不依赖外部方法

    /**
     * 1.dbType字段必须在支持的范围之内(ArangoDB、ClickHouse、Druid)
     * 2.dataSource数据表是否在库中存在
     * 3.limit不能大于100000
     * 4.时间格式校验
     * 5.intervals.fidedValues长度必须==2
     * 6.intervals.type暂时只支持between方式
     * 7.sort.type支持desc和asc
     * 8.match.type在:["exactly"|"prefix"|"suffix"|"substring"]内
     * 9.range.type在:["gt"|"lt"|"eq"|"ge"|"le"|"ne"]
     * 10.对表中的字段做是否存在的验证
     * */

    public void executeValidate() throws BusinessException {

    }


}
