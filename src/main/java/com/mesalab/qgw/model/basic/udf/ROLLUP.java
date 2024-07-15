package com.mesalab.qgw.model.basic.udf;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mesalab.common.entity.BaseResult;
import com.mesalab.common.entity.DataTypeMapping;
import com.mesalab.common.utils.sqlparser.SQLHelper;
import com.mesalab.qgw.constant.MetaConst;
import com.mesalab.qgw.model.basic.SelectStatement;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ROLLUP implements UDF {
    private String name;
    private String regularDimension;
    private String drillDownDimension;


    public ROLLUP(UDFElements udfElements) {
        this.name = udfElements.getName();
        this.regularDimension = SQLHelper.removeQuotesAndBackticks(udfElements.getParams().get(0).toString());
        this.drillDownDimension = SQLHelper.removeQuotesAndBackticks(udfElements.getParams().get(1).toString());
    }

    @Override
    public Object execute(SelectStatement selectStatement, boolean isDryRun, BaseResult baseResult) {
        List<Map<String, String>> meta = (List<Map<String, String>>) baseResult.getMeta();
        for (Map<String, String> stringStringMap : meta) {
            if (stringStringMap.get(MetaConst.META_CATEGORY).equals(MetaConst.META_CATEGORY_DIMENSION)) {
                stringStringMap.put(MetaConst.META_TYPE, DataTypeMapping.STRING);
            }
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) baseResult.getData();

        // TODO: 样例数据时，需要补充subtotal rows
        if (isDryRun) {
            List<Map<String, Object>> newData = new ArrayList<>();
            newData.add(Maps.newLinkedHashMap());
            for (Map<String, Object> element : data) {
                Map<String, Object> newElement = new LinkedHashMap<>(element);
                newElement.forEach((k, v) -> {
                    if (drillDownDimension.equals(k) || drillDownDimension.equals(selectStatement.getAliasFields().get(k))) {
                        newElement.put(k, null);
                    }
                });
                newData.add(newElement);
                newData.add(element);
            }
            data = newData;
        }
        // TODO: 遍历data 将rollup数据集结果标记subtotal、并且删除total总计行
        Iterator<Map<String, Object>> iterator = data.iterator();
        HashSet<Object> drillDownDimensionSets = Sets.newHashSet();
        AtomicBoolean hasRemoveRegularDimension = new AtomicBoolean(false);

        while (iterator.hasNext()) {
            Map<String, Object> next = iterator.next();
            if (!hasRemoveRegularDimension.get()) {
                // TODO  删除total rows
                if (meta.stream().filter(o -> MetaConst.META_CATEGORY_DIMENSION.equalsIgnoreCase(o.get(MetaConst.META_CATEGORY))).allMatch(o -> getRollupRowsDefaultValues().contains(next.get(o.get(MetaConst.META_NAME))))) {
                    iterator.remove();
                    hasRemoveRegularDimension.set(true);
                    continue;
                }
            }
            next.forEach((k, v) -> {
                if (drillDownDimension.equals(k) || drillDownDimension.equals(selectStatement.getAliasFields().get(k))) {
                    Object regularDimensionValue = next.get(regularDimension);
                    if (!next.containsKey(regularDimension)) {
                        Set<String> aliasSet = selectStatement.getAliasFields().keySet();
                        for (String alias : aliasSet) {
                            if (selectStatement.getAliasFields().get(alias).equals(regularDimension)) {
                                regularDimensionValue = next.get(alias);
                                break;
                            }
                        }
                    }
                    if (getRollupRowsDefaultValues().contains(next.get(k)) && (!drillDownDimensionSets.contains(regularDimensionValue))) {
                        next.put(k, "Subtotal");
                        drillDownDimensionSets.add(regularDimensionValue);
                    }
                }
                if (meta.stream().anyMatch(o -> o.get(MetaConst.META_NAME).equals(k)
                        && MetaConst.META_CATEGORY_DIMENSION.equals(o.get(MetaConst.META_CATEGORY)))
                        && next.get(k) != null
                        && !(next.get(k) instanceof List)) {
                    next.put(k, String.valueOf(next.get(k)));
                }
            });
        }
        return data;
    }

    private static List<Object> getRollupRowsDefaultValues() {
        List<Object> rollupValues = new ArrayList<>();
        rollupValues.add("");
        rollupValues.add(0);
        rollupValues.add(null);
        rollupValues.add(Lists.newArrayList());
        rollupValues.add("1970-01-01 00:00:00");
        rollupValues.add("1970-01-01 00:00:00.000");
        return rollupValues;
    }
}
