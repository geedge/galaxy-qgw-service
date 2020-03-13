package com.mesalab.common.utils;

import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import lombok.Data;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UserDefinedFunctionUtils {

    public static Pattern fun1 = Pattern.compile("\\bTIME_FLOOR_WITH_FILL\\(", Pattern.CASE_INSENSITIVE);
    public static Pattern fun2 = Pattern.compile("\\bNUMBER_UNIT\\(", Pattern.CASE_INSENSITIVE);
    public static final HashMap<String, String> functions = Maps.newHashMap();

    static {
        /*Param param = new Param();
        param.setSize(3);
        param.setFormat("%s(%s) fill(%s)");*/
        functions.put("TIME_FLOOR_WITH_FILL", "%s(%s) fill(%s)");
        functions.put("NUMBER_UNIT", "%s%s");
    }

    public static String generateCustomFunction(String query) {
        query = conversionFun(fun1, query);
        query = conversionFun(fun2, query);
        return query;
    }

    private static String conversionFun(Pattern pattern, String sql) {
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

                List<String> paramsList = function.getParamsList();
                String replace = replace(function.getName(), paramsList.toArray(new String[paramsList.size()]));
                sql = sql.substring(0, matcher.start()) + replace + sql.substring(matcher.start() + 1 + end);
            }
        }
    }


    private static String replace(String funName, String... paramsList) {
        String s = functions.get(funName.toUpperCase());
        if (com.zdjizhi.utils.StringUtil.isBlank(s)) {
            new Throwable("user defined function replace error");
        }
        String format = String.format(s, paramsList);
        return format;
    }


    @Data
    static class Function {
        private String name;
        private List<String> paramsList = new ArrayList<>();
    }

    /*@Data
    static class Param {
        private int size;
        private String format;
    }*/
}
