package com.mesalab.recommendation.entity;

import com.mesalab.common.enums.RangeEnum;
import com.zdjizhi.utils.StringUtil;
import lombok.Data;
import org.springframework.util.ObjectUtils;

import java.util.List;

/**
 * @description: 查询单位范围
 * @author: zhq
 * @create: 2020-07-02
 **/
@Data
public class Range extends QueryExp {

    /**
     * 获取List<Range> 查询语句
     *
     * @param ranges
     * @return
     */
    public static String getRangExp(List<Range> ranges, String alias) {

        StringBuffer rangesb = new StringBuffer();
        if (!ObjectUtils.isEmpty(ranges)) {
            for (int i = 0; i < ranges.size(); i++) {
                if (0 < i && !ObjectUtils.isEmpty(rangesb)) {
                    rangesb.append(" and ");
                }
                rangesb.append(getRangExp(ranges.get(i), alias));
            }
        }
        return rangesb.toString();
    }

    /**
     * 获取 Range 查询语句
     *
     * @param range
     * @return
     */
    public static String getRangExp(Range range, String alias) {

        StringBuffer rangesb = new StringBuffer();
        try {
            if (!ObjectUtils.isEmpty(range)) {
                rangesb.append(" ( ");
                alias = ObjectUtils.isEmpty(alias) ? "" : alias.concat(".");

                for (int j = 0; j < range.getFieldValues().size(); j++) {
                    if (0 < j && !ObjectUtils.isEmpty(rangesb)) {
                        rangesb.append(" or ");
                    }
                    rangesb.append(" ")
                            .append(alias)
                            .append(StringUtil.upperCase(range.getFieldKey()))
                            .append(RangeEnum.getSymbol(range.getType()))
                            .append(range.getFieldValues().get(j))
                            .append(" ");

                }
                rangesb.append(" ) ");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rangesb.toString();
    }

}
