package com.mesalab.knowledge;

import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.knowledge.entity.DSLObject;
import com.mesalab.knowledge.enums.DataSourceEnum;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * @description: for validate
 * @author: zhq
 * @create: 2020-07-20
 **/
@EnableAutoConfiguration
public class ValidateTest extends GalaxyQGWApplicationTests {
    private static DSLObject.DSLQuery query = new DSLObject.DSLQuery();

    @Test
    public void queryDslTest(){
        String dataSource = query.getDataSource();
        DataSourceEnum dataSourceRouteEnum = DataSourceEnum.valueOf(dataSource);
        System.out.println(dataSource);


    }

}
