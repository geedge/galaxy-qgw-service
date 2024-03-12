package com.mesalab.qgw.service;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.GalaxyQGWApplicationTests;
import com.mesalab.common.entity.DataTypeMapping;
import com.mesalab.common.utils.sqlparser.ExampleDataHelper;
import com.mesalab.common.utils.sqlparser.SelectItemHelper;
import com.mesalab.qgw.constant.DataTypeConst;
import com.mesalab.qgw.constant.MetaConst;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import java.util.List;
import java.util.Map;


@Slf4j
@EnableAutoConfiguration
public class ExampleDataTest extends GalaxyQGWApplicationTests {

    @Test
    public void testSelectItemHelper() throws JSQLParserException {
        String sql = "SELECT filed_1 AS \"Field A\", SUM(field_2) AS \"SUM(field_2)\", MAX(field_3) FROM table GROUP BY filed_1 ORDER BY \"Field A\" DESC LIMIT 10";
        Map<String, SelectItemHelper.AliasObject> selectItem = SelectItemHelper.getSelectItem(sql);
        Assert.assertEquals("ok", 3, selectItem.size());
    }

    @Test
    public void testExampleDataHelper() {
        List<Map<String, String>> meta = initMeta();
        List<Object> objects = ExampleDataHelper.buildExampleData(ExampleDataHelper.QUERY_TYPE_GROUP_BY, meta, 1L, null, true);
        Assert.assertEquals("ok", 1, objects.size());
    }

    private static List<Map<String, String>> initMeta() {
        List<Map<String, String>> meta = Lists.newArrayList();
        Map<String, String> item1 = Maps.newHashMap();
        item1.put(MetaConst.META_NAME, "Field A");
        item1.put(MetaConst.META_TYPE, DataTypeMapping.STRING);
        item1.put(MetaConst.META_CATEGORY, MetaConst.META_CATEGORY_DIMENSION);
        item1.put(MetaConst.META_DATA_TYPE, DataTypeConst.IP);
        meta.add(item1);

        Map<String, String> item2 = Maps.newHashMap();
        item2.put(MetaConst.META_NAME, "SUM(field_2)");
        item2.put(MetaConst.META_TYPE, DataTypeMapping.LONG);
        item2.put(MetaConst.META_CATEGORY, MetaConst.META_CATEGORY_METRIC);
        meta.add(item2);

        Map<String, String> item3 = Maps.newHashMap();
        item3.put(MetaConst.META_NAME, "MAX(field_3)");
        item3.put(MetaConst.META_TYPE, DataTypeMapping.LONG);
        item3.put(MetaConst.META_CATEGORY, MetaConst.META_CATEGORY_METRIC);
        meta.add(item3);
        return meta;
    }

}
