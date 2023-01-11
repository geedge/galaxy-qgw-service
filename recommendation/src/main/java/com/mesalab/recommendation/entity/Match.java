package com.mesalab.recommendation.entity;

import com.google.common.base.CharMatcher;
import com.mesalab.common.enums.MatchEnum;
import com.zdjizhi.utils.StringUtil;
import lombok.Data;
import org.apache.commons.lang3.EnumUtils;
import org.springframework.util.ObjectUtils;

import java.text.MessageFormat;
import java.util.List;
import java.util.function.Predicate;

import static java.util.stream.Collectors.joining;

/**
 * @description: 查询单位字符串
 * @author: zhq
 * @create: 2020-07-02
 **/
@Data
public class Match extends QueryExp {


    public static String like(List<Match> matches, Predicate<Match> predicate) {

        return ObjectUtils.isEmpty(matches) ?"": matches.stream().filter(m -> predicate.test(m)).map(m -> {

            MatchEnum match = EnumUtils.getEnum(MatchEnum.class, StringUtil.upperCase(m.getType()));
            return m.getFieldValues().stream().filter(mv -> mv != null).map(mv -> {
                String matchExp = match.getMatchExp();
                String value = String.valueOf(mv);

                if (MatchEnum.REGEX.getType().equals(m.getType())) {
                    if (value.startsWith("$")) {
                        matchExp = MatchEnum.EXACTLY.getMatchExp();
                        value = CharMatcher.anyOf("$").trimLeadingFrom(value);
                    } else if (value.startsWith("*") && !value.endsWith("*")) {
                        matchExp = MatchEnum.SUFFIX.getMatchExp();
                        value = CharMatcher.anyOf("*").trimLeadingFrom(value);
                    } else if (value.endsWith("*") && !value.startsWith("*")) {
                        matchExp = MatchEnum.PREFIX.getMatchExp();
                        value = CharMatcher.anyOf("*").trimTrailingFrom(value);
                    } else if (value.startsWith("*") && value.endsWith("*")) {
                        matchExp = MatchEnum.SUBSTRING.getMatchExp();
                        value = CharMatcher.anyOf("*").trimFrom(value);
                        value = MessageFormat.format(matchExp, value);

                    } else {
                        matchExp = MatchEnum.SUBSTRING.getMatchExp();
                        value = MessageFormat.format(matchExp, value);
                    }
                }

                return value;

            }).collect(joining(" or doc._key like ", "  ( doc._key like ", ") "));

        }).collect(joining(" and ", " filter ", " "));
    }

    public static String like(List<Match> matches, Predicate<Match> predicate, List<Range> ranges) {


        return ObjectUtils.isEmpty(matches) ?"": matches.stream().filter(m -> predicate.test(m)).map(m -> {

            MatchEnum match = EnumUtils.getEnum(MatchEnum.class, StringUtil.upperCase(m.getType()));
            return m.getFieldValues().stream().filter(mv -> mv != null).map(mv -> {
                String matchExp = match.getMatchExp();
                String value = String.valueOf(mv);

                if (MatchEnum.REGEX.getType().equals(m.getType())) {
                    if (value.startsWith("$")) {
                        matchExp = MatchEnum.EXACTLY.getMatchExp();
                        value = CharMatcher.anyOf("$").trimLeadingFrom(value);
                        value = getVsysValues(ranges, matchExp, value);

                    } else if (value.startsWith("*") && !value.endsWith("*")) {
                        matchExp = MatchEnum.SUFFIX.getMatchExp();
                        value = CharMatcher.anyOf("*").trimLeadingFrom(value);
                        value = getVsysValues(ranges, matchExp, value);

                    } else if (value.endsWith("*") && !value.startsWith("*")) {
                        matchExp = MatchEnum.PREFIX.getMatchExp();
                        value = CharMatcher.anyOf("*").trimTrailingFrom(value);
                        value = MessageFormat.format(matchExp, value);

                    } else if (value.startsWith("*") && value.endsWith("*")) {
                        matchExp = MatchEnum.SUBSTRING.getMatchExp();
                        value = CharMatcher.anyOf("*").trimFrom(value);
                        value = MessageFormat.format(matchExp, value);

                    } else {
                        matchExp = MatchEnum.SUBSTRING.getMatchExp();
                        value = MessageFormat.format(matchExp, value);
                    }
                } else if (MatchEnum.EXACTLY.getType().equals(m.getType())||MatchEnum.SUFFIX.getType().equals(m.getType())){
                    value = getVsysValues(ranges, matchExp, value);
                } else {
                    value = MessageFormat.format(matchExp, value);
                }

                return value;

            }).collect(joining(" or doc._key like ", "  ( doc._key like ", ") "));

        }).collect(joining(" and ", " filter ", " "));
    }

    private static String getVsysValues(List<Range> ranges, String matchExp, String value) {
        final Integer defVsysId = 1 ;
        Boolean vsysIdNull = true;
        //查询_key ,_from 需要加上 vsys_id
        if(!ObjectUtils.isEmpty(ranges)){
            for (Range range : ranges) {
                String fieldKey = range.getFieldKey();
                if("vsys_id".equalsIgnoreCase(fieldKey)){
                    vsysIdNull = false;
                    StringBuilder vsb = new StringBuilder();
                    List<Object> fieldValues = range.getFieldValues();
                    for (int i = 0; i < fieldValues.size(); i++) {
                        String vsysId = String.valueOf(fieldValues.get(i));
                        if(0 < i){
                            vsb.append(" or doc._key like ");
                        }
                        String valueTmp = value + "-" + vsysId;
                        vsb.append( MessageFormat.format(matchExp,valueTmp));
                    }
                    value = vsb.toString();
                }
            }
        }
        if(vsysIdNull){
            value = MessageFormat.format(matchExp, value + "-" + defVsysId);
        }
        return value;
    }

}