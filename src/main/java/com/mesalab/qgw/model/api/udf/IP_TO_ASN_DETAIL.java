package com.mesalab.qgw.model.api.udf;

import com.google.common.collect.Lists;
import com.mesalab.common.utils.IPUtil;
import com.mesalab.qgw.model.api.SQLQuerySource;
import com.zdjizhi.utils.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
public class IP_TO_ASN_DETAIL implements  UDF {
    private String name;
    private String address;


    public IP_TO_ASN_DETAIL(UDFElements udfElements) {
        this.name = udfElements.getName();
        this.address = udfElements.getParams().get(0).toString();
    }

    @Override
    public Object execute(SQLQuerySource sqlQuerySource, List<Object> results) {
        List<Map<String, Object>>  targetResult = Lists.newArrayList();
        String addressLabel = StringUtil.EMPTY;


        for (String alias : sqlQuerySource.getAliasFields().keySet()) {
            if (sqlQuerySource.getAliasFields().get(alias).contains(address) &&
                    sqlQuerySource.getAliasFields().get(alias).contains(name) ) {
                addressLabel = alias;
                break;
            }
        }


        for (Object source: results) {
            Map<String, Object> sourceMap = ((Map<String, Object>) source);
            if (sourceMap.containsKey(addressLabel)) {
                sourceMap.put(addressLabel, IPUtil.getAsnDetail(sourceMap.get(addressLabel).toString()));
            }
            targetResult.add(sourceMap);
        }



        return targetResult;
    }


}
