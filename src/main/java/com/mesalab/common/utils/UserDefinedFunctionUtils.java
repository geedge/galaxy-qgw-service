package com.mesalab.common.utils;

import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import com.mesalab.common.enums.DBTypeEnum;
import lombok.Data;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UserDefinedFunctionUtils {

    public static final String TIME_FLOOR_WITH_FILL = "TIME_FLOOR_WITH_FILL";

    public static Pattern periodOfPT = Pattern.compile("PT(\\d+)(\\w+)", Pattern.CASE_INSENSITIVE);
    public static Pattern periodOfP = Pattern.compile("P(\\d+)(\\w+)", Pattern.CASE_INSENSITIVE);

    public static Pattern pTimeFloorWithFill = Pattern.compile("\\b" + TIME_FLOOR_WITH_FILL + "\\(", Pattern.CASE_INSENSITIVE);

    public static final HashMap<String, String> functions = Maps.newHashMap();

    static {
        functions.put(TIME_FLOOR_WITH_FILL, null);
    }

    public static String generateCustomFunction(String query, String dbType) {
        query = conversionFun(pTimeFloorWithFill, query, dbType);
        return query;
    }

    private static String conversionFun(Pattern pattern, String sql, String dbType) {
        int count = 0;
        while (true) {
            Matcher matcher = pattern.matcher(sql);
            if (!matcher.find() || count++ >= 40) {
                return sql;
            }
            String sqlParse = sql.substring(matcher.start());
            int[] bracketsMatch = StringUtil.getBracketsMatch(sqlParse, "(", false);

            if (bracketsMatch[0] >= 1) {
                int start = StringUtils.indexOf(sqlParse, "(");
                Function function = new Function();
                function.setName(sqlParse.substring(0, start));
                int end = bracketsMatch[1];
                List<String> strings = Splitter.on(",").trimResults().omitEmptyStrings().splitToList(sqlParse.substring(start + 1, end));
                StringBuffer sb = new StringBuffer();
                for (String str : strings) {
                    sb.append(str);
                    int[] a = StringUtil.getBracketsMatch(sb.toString(), "(", true);
                    if (a[0] != -1) {
                        function.getParamsList().add(sb.toString());
                        sb = new StringBuffer();
                        continue;
                    } else {
                        sb.append(",");
                    }
                }

                String replace = replace(function, dbType);
                sql = sql.substring(0, matcher.start()) + replace + sql.substring(matcher.start() + 1 + end);
            }
        }
    }


    private static String replace(Function function, String dbType) {
        String name = function.getName();
        if (TIME_FLOOR_WITH_FILL.equals(name)) {
            String timestamp = function.getParamsList().get(0);
            String period = function.getParamsList().get(1);

            Matcher matcherPT = periodOfPT.matcher(period);
            if (matcherPT.find()) {
                String num = matcherPT.group(1);
                String unit = matcherPT.group(2);
                if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)) {
                    String format = "toDateTime(intDiv(toUInt32(toDateTime(toDateTime(%s))), %s ) * %s )";
                    if ("S".equalsIgnoreCase(unit)) {
                        num = num;
                    } else if ("M".equalsIgnoreCase(unit)) {
                        num = num + " * 60 ";
                    } else if ("H".equalsIgnoreCase(unit)) {
                        num = num + " * 3600 ";
                    }
                    return String.format(format, timestamp, num, num);
                } else if (DBTypeEnum.DRUID.getValue().equals(dbType)) {
                    String format = "time_floor(%s, %s)";
                    return String.format(format, timestamp, period);
                }
            }

            Matcher matcherP = periodOfP.matcher(period);
            if (matcherP.find()) {
                String num = matcherP.group(1);
                String unit = matcherP.group(2);
                if (DBTypeEnum.CLICKHOUSE.getValue().equals(dbType)) {
                    String format = null;
                    if ("1".equals(num)) {
                        if ("D".equalsIgnoreCase(unit)) {
                            format = "toStartOfDay(toDateTime(%s))";
                        } else if ("W".equalsIgnoreCase(unit)) {
                            format = "toDateTime(toMonday(toDateTime(%s)))";
                        } else if ("M".equalsIgnoreCase(unit)) {
                            format = "toDateTime(toStartOfMonth(toDateTime(%s)))";
                        } else if ("Y".equalsIgnoreCase(unit)) {
                            format = "toDateTime(toStartOfYear(toDateTime(%s)))";
                        }
                        System.out.println(String.format(format, timestamp));
                        return String.format(format, timestamp);
                    } else {
                        if ("D".equalsIgnoreCase(unit)) {
                            format = "toDateTime(toStartOfDay(toDateTime(intDiv(toUInt32(toDateTime(toDateTime(%s))), %s ) * %s)))";
                            num = num + " * 86400 ";
                        } else if ("W".equalsIgnoreCase(unit)) {
                            format = "toDateTime(toMonday(toDateTime(intDiv(toUInt32(toDateTime(toDateTime(%s))), %s ) * %s)))";
                            num = num + " * 604800 ";
                        } else if ("M".equalsIgnoreCase(unit)) {
                            format = "toDateTime(toStartOfMonth(toDateTime(intDiv(toUInt32(toDateTime(toDateTime(%s))), %s ) * %s)))";
                            num = num + " * 2592000 ";
                        } else if ("Y".equalsIgnoreCase(unit)) {
                            format = "toDateTime(toStartOfYear(toDateTime(intDiv(toUInt32(toDateTime(toDateTime(%s))), %s ) * %s)))";
                            num = num + " * 31536000 ";
                        }
                        return String.format(format, timestamp, num, num);
                    }
                } else if (DBTypeEnum.DRUID.getValue().equals(dbType)) {
                    String format = "time_floor(%s, %s)";
                    return String.format(format, timestamp, period);
                }
            }
        }
        return function.toString();
    }


    @Data
    static class Function {
        private String name;
        private List<String> paramsList = new ArrayList<>();
    }
}
