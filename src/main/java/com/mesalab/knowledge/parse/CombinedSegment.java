package com.mesalab.knowledge.parse;

import java.util.List;
import java.util.Objects;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.mesalab.knowledge.entity.*;
import com.zdjizhi.utils.DateUtils;

/**
 * @description: 连接片段
 * @author: zhq
 * @create: 2020-07-03
 **/
public class CombinedSegment {


    /**
     * @return com.knowledge.query.ql.QLConditions
     * @Author zhq
     * @Description //TODO 将查询条件片段连接
     * @Date 2020\7\6 0006
     * @Param [parameters]
     **/
    public static QLConditions getCondition(Parameters parameters) {
        StringBuffer sb = new StringBuffer();
        List<Match> matches = parameters.getMatches();
        if (matches != null && matches.size() > 0) {
            for (int i = 0; i < matches.size(); i++) {
                if (i == 0 || Objects.isNull(matches.get(i).getOperator())) {
                    sb.append((i == 0 ? "" : " and ") + matches.get(i).joinQueryExp() + " ");
                } else {
                    PredicateEnum symbol = PredicateEnum.match(matches.get(i).getOperator(), null);
                    sb.append(symbol + " " + matches.get(i).joinQueryExp() + " ");
                }
            }
        }

        List<Range> ranges = parameters.getRanges();
        if (ranges != null && ranges.size() > 0) {
            for (int i = 0; i < ranges.size(); i++) {
                if ((sb.length() < 1 && i == 0) || Objects.isNull(ranges.get(i).getOperator())) {
                    sb.append((i == 0 ? "" : " and ") + ranges.get(i).joinQueryExp() + " ");
                } else {
                    PredicateEnum symbol = PredicateEnum.match(ranges.get(i).getOperator(), null);
                    sb.append(symbol + " " + ranges.get(i).joinQueryExp() + " ");
                }
            }
        }


        List<String> intervals = parameters.getIntervals();
        if (!Objects.isNull(intervals)) {
            List<String> ins = Splitter.on("/").splitToList(intervals.get(0));
            if (sb.length() > 1) {
                sb.append(" and ");
            }
            if (Objects.isNull(ins.get(1))) {
                sb.append("FIRST_FOUND_TIME <=" + DateUtils.convertStringToTimestamp(ins.get(0), DateUtils.YYYY_MM_DD_HH24_MM_SS));
            } else if (Objects.isNull(ins.get(0))) {
                sb.append("LAST_FOUND_TIME >" + DateUtils.convertStringToTimestamp(ins.get(1), DateUtils.YYYY_MM_DD_HH24_MM_SS));
            } else {
                sb.append("FIRST_FOUND_TIME <=" + DateUtils.convertStringToTimestamp(ins.get(0), DateUtils.YYYY_MM_DD_HH24_MM_SS))
                        .append(" and ")
                        .append("FIRST_FOUND_TIME <=" + DateUtils.convertStringToTimestamp(ins.get(1), DateUtils.YYYY_MM_DD_HH24_MM_SS));
            }
        }

        List<Sort> sorts = parameters.getSorts();
        if (!Objects.isNull(sorts)) {
            sb.append(" order by ");
            for (Sort s : sorts) {
//                sb.append(Joiner.on(" ").join(s.getFiledKey(), s.getFiledValues(), ","));
            }
            sb.deleteCharAt(sb.length() - 1);
        }
        String limit = parameters.getLimit();
        if (Objects.isNull(limit)) {
            sb.append(" limit ").append(10000);
        } else {
            sb.append(" limit ").append(limit);
        }

        return QLConditions.builder().qlFilter(sb.toString()).build();
    }
}
