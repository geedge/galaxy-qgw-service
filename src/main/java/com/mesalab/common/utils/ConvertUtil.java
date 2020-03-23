package com.mesalab.common.utils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mesalab.common.support.IConvertor;
import com.zdjizhi.utils.CommonUtil;
import com.zdjizhi.utils.DateUtils;
import com.zdjizhi.utils.StringUtil;
import org.nutz.lang.Lang;

import java.util.*;

/**
 * 转换器工具类
 *
 * @author dazzlzy
 * @date 2018/5/9
 */
public class ConvertUtil {

      enum  Fill {

        NONE(""),  NULL("NULL"), ZERO("0"), PREVIOUS("previous"), NEXT("next");

        private final String value;

        private Fill(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

    }


    /**
     * 将来源collection转换成目标list.
     *
     * @param sources          来源
     * @param convertor        转换器
     * @param ignoreNull       是否忽略空值
     * @param duplicateRemoval 是否去除重复
     * @param <Source>         来源
     * @param <Target>         目标对象
     * @return 转换后的List
     */
    public static <Source, Target> List<Target> convert(Collection<Source> sources, IConvertor<Source, Target> convertor,
                                                        boolean ignoreNull, boolean duplicateRemoval) {
        // 返回目标值
        Collection<Target> targets;

        //去重使用HashSet，不去冲使用ArrayList
        targets = duplicateRemoval ? new HashSet<>() : new ArrayList<>();

        // 来源不为空
        if (sources != null && sources.size() > 0) {
            for (Source source : sources) {
                Target target = convertor.convert(source);
                // 不忽略空值或者目标不为空
                if (!ignoreNull || target != null) {
                    // 添加目标
                    targets.add(convertor.convert(source));
                }
            }
        }
        // 转换成list
        return Lang.collection2list(targets);
    }

    /**
     * 将来源collection转换成目标list.
     *
     * @param sources   来源
     * @param convertor 转换器
     * @param <Source>  来源
     * @param <Target>  目标对象
     * @return 转换后的List
     */
    public static <Source, Target> List<Target> convert(Collection<Source> sources, IConvertor<Source, Target> convertor) {
        return convert(sources, convertor, false, false);
    }

    /**
     * 将来源按转换器的方式转换成Map<Key, Obj>的方式.
     *
     * @param sources   来源
     * @param convertor 转换器
     * @param <Key>     返回的Map的key的类型
     * @param <Obj>     返回的Map的value的类型，即来源集合中的类型
     * @return 转换后的对象类型
     */
    public static <Key, Obj> Map<Key, Obj> map(Collection<Obj> sources, IConvertor<Obj, Key> convertor) {
        if (sources == null) {
            return new HashMap<>(16);
        }
        Map<Key, Obj> mapping = new HashMap<>(sources.size());
        // 来源不为空
        if (sources.size() > 0) {
            for (Obj obj : sources) {
                Key key = convertor.convert(obj);
                mapping.put(key, obj);
            }
        }
        return mapping;
    }

    /**
     * 将来源按转换器的方式转换成Map<Key,List<Obj>>的方式.
     *
     * @param sources   来源
     * @param convertor 转换器
     * @param <Key>     返回的Map的key的类型
     * @param <Obj>     返回的Map的value的List包含类型
     * @return 转换后的对象类型
     */
    public static <Key, Obj> Map<Key, List<Obj>> maplist(Collection<Obj> sources, IConvertor<Obj, Key> convertor) {
        Map<Key, List<Obj>> mapping = new HashMap<>(16);
        // 来源不为空
        if (sources != null && sources.size() > 0) {
            for (Obj obj : sources) {
                Key key = convertor.convert(obj);
                List<Obj> list = mapping.get(key);

                if (list == null) {
                    mapping.put(key, Lang.list(obj));
                } else {
                    list.add(obj);
                }
            }
        }
        return mapping;
    }

    /**
     * 将时序特征的数据，转换成层级结构；
     * 例如：Map<groupKey, timeseries Map>
     *         Map<timeGran,Metrics Map>
     *             Map<metric,value>
     *
     * @param sources
     * @param groupLabel
     * @param timeGranLabel
     * @return
     */
    public static  Map<String, Map<String, Map<String, String>>> convertResultListToTimeseries(Collection<Object> sources,
                                                                                       String groupLabel, String timeGranLabel) {
        Map<String, Map<String, Map<String, String>>> resultMap = Maps.newHashMap();

        for (Object source : sources) {
            Map<String, String> sourceMap = ((Map<String, String>) source);
            String groupKey = sourceMap.get(groupLabel);
            String timeGran = sourceMap.get(timeGranLabel);

            sourceMap.remove(groupLabel);
            sourceMap.remove(timeGranLabel);

            Map groupMap = resultMap.get(groupKey);

            if (StringUtil.isEmpty(groupMap)) {
                groupMap = Maps.newTreeMap();
                groupMap.put(timeGran, sourceMap);
                resultMap.put(groupKey,groupMap);

            } else {

                groupMap.put(timeGran, sourceMap);
            }


        }

        return resultMap;

    }


    public static List<Map<String, String>> convertTimeseriesToList(Map<String, Map<String, Map<String, String>>> target, String groupLabel, String timeGranLabel) {
        List<Map<String, String>> results = Lists.newArrayList();

        for(String groupKey : target.keySet()) {
            Map<String, Map<String, String>> groupMap = target.get(groupKey);

            for (String timeGranKey : groupMap.keySet()) {

                Map<String, String> metricMap = groupMap.get(timeGranKey);

                Map<String, String> resultMap = Maps.newLinkedHashMap();

                resultMap.put(timeGranLabel, timeGranKey);
                resultMap.put(groupLabel, groupKey);

                for (String metricLabel : metricMap.keySet()) {
                    resultMap.put(metricLabel, metricMap.get(metricLabel));
                }

                results.add(resultMap);



            }


        }





        return results;
    }


    /**
     * 时序数据补全操作
     * @param sources TreeMap<timeGran, metricMap>
     * @param dateList
     * @param fill
     * @return
     */
    public static Map<String, Map<String, String>> completeTimeseries(TreeMap<String, Map<String, String>> sources,
                                                               List<Date> dateList, String fill) {
        Map<String, String> defaultElement = Maps.newHashMap(sources.firstEntry().getValue());


        if (fill.equalsIgnoreCase(Fill.ZERO.name())){
            for(String key: defaultElement.keySet()){
                defaultElement.put(key, Fill.ZERO.getValue());
            }

        } else if (fill.equalsIgnoreCase(Fill.NULL.name())){
            for(String key: defaultElement.keySet()) {
                defaultElement.put(key, Fill.NULL.value);
            }
        } else if (fill.equalsIgnoreCase(Fill.NONE.name())) {
            for(String key: defaultElement.keySet()) {
                defaultElement.put(key, Fill.NONE.value);
            }
        } else {
            for(String key: defaultElement.keySet()) {
                defaultElement.clear();
            }
        }

        TreeMap<String, Map<String, String>> target = Maps.newTreeMap(sources);
        for(Date date : dateList) {
            String key = DateUtils.getFormatDate(date, DateUtils.YYYY_MM_DD_HH24_MM_SS);
            Map<String, String> metricsMap = sources.get(key);
            if (StringUtil.isEmpty(metricsMap)) {
                target.put(key, defaultElement);
            }
        }



        if (StringUtil.isEmpty(defaultElement)) {

            defaultElement = getDefaultElement(sources.firstEntry().getValue());

            for (String key : target.keySet()) {

                if (StringUtil.isEmpty(target.get(key))) {

                    Map<String, String> element=  getPositionElement(target, key, fill);

                    if (StringUtil.isEmpty(element)) {
                        target.put(key, defaultElement);
                    } else {
                        target.put(key, element);
                    }


                }


            }

        }


        return target;
    }


    private static Map<String, String> getPositionElement(TreeMap<String, Map<String, String>> target,
                                                   String key, String fill) {
        Map<String, String> element = Maps.newHashMap();

        if (fill.equalsIgnoreCase(Fill.PREVIOUS.name())) {
            if (StringUtil.isNotEmpty(target.lowerEntry(key))) {
                element = target.lowerEntry(key).getValue();
            }


        } else if (fill.equalsIgnoreCase(Fill.NEXT.name())) {
            if (StringUtil.isNotEmpty(target.higherEntry(key))) {
                element = target.higherEntry(key).getValue();
            }


        }
        return element;
    }



    private static Map<String, String> getDefaultElement(Map<String, String> firstElement) {
        Map<String, String> element = Maps.newHashMap(firstElement);

        for (String key : element.keySet()) {
            element.put(key, Fill.NONE.getValue());
        }

        return element;
    }










}
