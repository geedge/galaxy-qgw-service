package com.mesalab.qgw.model.api.udf;

import com.google.common.collect.Lists;
import com.mesalab.common.utils.IPUtil;
import com.mesalab.qgw.model.api.SQLQuerySource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.StringValue;

import java.util.List;
import java.util.Map;

@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
public class IP_TO_CITY implements  UDF{
    private String name;
    private String address;


    public IP_TO_CITY(UDFElements udfElements) {
        this.name = udfElements.getName();
        this.address = ((StringValue) udfElements.getParams().get(0)).getValue();
    }

    @Override
    public Object execute(SQLQuerySource sqlQuerySource, List<Object> results) {
        List<Map<String, Object>>  targetResult = Lists.newArrayList();
        try {
             for (Object source: results) {
                 Map<String, Object> sourceMap = ((Map<String, Object>) source);
                 if (sourceMap.containsKey(address)) {
                     sourceMap.put(address, IPUtil.getCityDetail(sourceMap.get(address).toString()));
                 }
                   targetResult.add(sourceMap);
             }

        } catch (Exception e) {
            log.error("execute IpToGEO ERROR: " + e);
        }

        return targetResult;
    }


}
