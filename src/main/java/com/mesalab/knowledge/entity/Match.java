package com.mesalab.knowledge.entity;

import com.google.common.base.CharMatcher;
import com.mesalab.knowledge.enums.MatchEnum;
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
                    } else {
                        matchExp = MatchEnum.SUBSTRING.getMatchExp();
                    }
                }
                return MessageFormat.format(matchExp, value);
            }).collect(joining(" or doc._key like ", "  ( doc._key like ", ") "));

        }).collect(joining(" and ", " filter ", " "));
    }

}