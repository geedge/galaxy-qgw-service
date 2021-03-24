package com.mesalab.common.utils;

import org.apache.commons.lang.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * String 工具类
 *
 * @author dazzlzy
 * @date 2018/4/26
 */
public class StringUtil {

    private static Pattern linePattern = Pattern.compile("_(\\w)");
    private static Pattern humpPattern = Pattern.compile("[A-Z]");

    /**
     * 下划线转驼峰
     *
     * @param str 下划线字符串
     * @return 驼峰字符串
     */
    public static String lineToHump(String str) {
        if (null == str || "".equals(str)) {
            return str;
        }
        str = str.toLowerCase();
        Matcher matcher = linePattern.matcher(str);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, matcher.group(1).toUpperCase());
        }
        matcher.appendTail(sb);

        str = sb.toString();
        str = str.substring(0, 1).toUpperCase() + str.substring(1);

        return str;
    }

    /**
     * 驼峰转下划线,效率比上面高
     *
     * @param str 驼峰字符串
     * @return 下划线字符串
     */
    public static String humpToLine(String str) {
        Matcher matcher = humpPattern.matcher(str);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "_" + matcher.group(0).toLowerCase());
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 驼峰转下划线(简单写法，效率低于{@link #humpToLine(String)})
     *
     * @param str 驼峰字符串
     * @return 下划线字符串
     */
    public static String humpToLine2(String str) {
        return str.replaceAll("[A-Z]", "_$0").toLowerCase();
    }

    /**
     * 首字母转小写
     *
     * @param s 字符串
     * @return 首字母小写的字符串
     */
    public static String toLowerCaseFirstOne(String s) {
        if (StringUtils.isBlank(s)) {
            return s;
        }
        if (Character.isLowerCase(s.charAt(0))) {
            return s;
        } else {
            return (new StringBuilder()).append(Character.toLowerCase(s.charAt(0))).append(s.substring(1)).toString();
        }
    }

    /**
     * 首字母转大写
     *
     * @param s 字符串
     * @return 首字母大写的字符串
     */
    public static String toUpperCaseFirstOne(String s) {
        if (StringUtils.isBlank(s)) {
            return s;
        }
        if (Character.isUpperCase(s.charAt(0))) {
            return s;
        } else {
            return (new StringBuffer()).append(Character.toUpperCase(s.charAt(0))).append(s.substring(1)).toString();
        }
    }

    /**
     * object转String
     *
     * @param object object
     * @return String
     */
    public static String getString(Object object) {
        return getString(object, "");
    }

    /**
     * object转String，提供默认值
     *
     * @param object       object
     * @param defaultValue 默认值
     * @return String
     */
    public static String getString(Object object, String defaultValue) {
        if (null == object) {
            return defaultValue;
        }
        return object.toString();
    }

    /**
     * object转int
     *
     * @param object object
     * @return int
     */
    public static int getInt(Object object) {
        return getInt(object, -1);
    }

    /**
     * object转int，提供默认值
     *
     * @param object       object
     * @param defaultValue 默认值
     * @return int
     */
    public static int getInt(Object object, Integer defaultValue) {
        if (null == object) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(object.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * object转Boolean
     *
     * @param object object
     * @return boolean
     */
    public static boolean getBoolean(Object object) {
        return getBoolean(object, false);
    }

    /**
     * object转boolean，提供默认值
     *
     * @param object       object
     * @param defaultValue 默认值
     * @return boolean
     */
    public static boolean getBoolean(Object object, Boolean defaultValue) {
        if (null == object) {
            return defaultValue;
        }
        return Boolean.parseBoolean(object.toString());
    }



    /**
     * 获取第一个括号对应右括号的索引以及包含括号的个数,向后匹配
     * @param str
     * @param bracketL 支持：( OR [ OR {
     * @param bool 是否将传入的字符串全部匹配完成,若不需要则在匹配到第一个括号匹配完成时结束
     * @return int[0]:匹配括号的个数,int[1]:第一个括号对应右括号的索引;若无匹配括号返回{0,-1};若有其中一个不成对则返回{-1,index}
     */
    public static int[] getBracketsMatch(String str, String bracketL, boolean bool) {
        int[] result = {0, -1};
        if (str == null) {
            return result;
        }
        String bracketR = ")";
        if (bracketL == null) {
            bracketL = "(";
        } else if (bracketL.equals("[")) {
            bracketR = "]";
        } else if (bracketL.equals("{")) {
            bracketR = "}";
        }
        StringBuffer sb = new StringBuffer(str);
        int countL = 0, countR = 0;
        for (int a = 0; a < sb.length(); a++) {
            if (sb.indexOf(bracketR) == a) {
                ++countR;
                sb.replace(a, a + 1, "$");
            } else if (sb.indexOf(bracketL) == a) {
                ++countL;
                sb.replace(a, a + 1, "$");
            }
            if (countR > countL) {
                result[0] = -1;
                result[1] = -1;
                return result;
            }
            result[0] = countL;
            result[1] = a;
            if (countL != 0 && (countL == countR) && !bool) {
                return result;
            }
        }
        if (countR != countL) {
            result[0] = -1;
        }
        return result;
    }

    /**
     * 获取最后一个括号对应左括号的索引以及包含括号的个数,向前匹配
     * @param str
     * @param bracketR 支持：) OR ] OR }
     * @param bool 是否将传入的字符串全部匹配完成,若不需要则在匹配到第一个括号匹配完成时结束
     * @return int[0]:匹配括号的个数,int[1]:第一个括号对应左括号的索引;若无匹配括号返回{0,-1};若有其中一个不成对则返回{-1,index}
     */
    public static int[] getBracketsMatchReverse(String str, String bracketR, boolean bool) {
        int[] result = {0, -1};
        if (str == null) {
            return result;
        }
        StringBuffer sb = new StringBuffer(str);
        String bracketL = "(";
        if (bracketR == null) {
            bracketR = ")";
        } else if (bracketR.equals("]")) {
            bracketL = "[";
        } else if (bracketR.equals("}")) {
            bracketL = "{";
        }
        int countL = 0, countR = 0;
        for (int a = sb.length() - 1; a >= 0; a--) {
            if (sb.indexOf(bracketR) == a) {
                ++countR;
                sb.replace(a, a + 1, "$");
            } else if (sb.indexOf(bracketL) == a) {
                ++countL;
                sb.replace(a, a + 1, "$");
            }
            if (countL > countR) {
                result[0] = -1;
                result[1] = -1;
                return result;
            }
            result[0] = countL;
            result[1] = a;
            if (countR != 0 && (countL == countR) && !bool) {
                return result;
            }
        }
        if (countR != countL) {
            result[0] = -1;
        }
        return result;
    }
}
