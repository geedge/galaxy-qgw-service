package com.mesalab.qgw.common.property;

import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author TianGaoChao
 * @describe 提取.sql文件中的内容
 *
 */
public class SqlPropertySourceFactory implements PropertySourceFactory {

    private static final String KEY_START = "#sql";
    private static final String KEY_END = "#end";
    public static final Pattern pKey = Pattern.compile("\\(\"(.*?)\"\\)", Pattern.CASE_INSENSITIVE);
    int index = 0;

    @Override
    public PropertySource<?> createPropertySource(String name, EncodedResource resource) throws IOException {

        LinkedList<Pair> queries = new LinkedList<>();

        new BufferedReader(resource.getReader()).lines().forEach(line -> {
            if (line.startsWith(KEY_START)) {
                Matcher matcher = pKey.matcher(line);
                String group = null;
                while (matcher.find()) {
                    group = matcher.group(1);
                }
                queries.addLast(new Pair(group));
                index = 0;
            } else if (!line.endsWith(KEY_END) && index == 0) {
                Optional.ofNullable(queries.getLast()).ifPresent(pair -> pair.lines.add(line));
            } else if (line.endsWith(KEY_END)) {
                index = 1;
            }
        });

        Map<String, Object> sqlMap = queries.stream()
                .filter(pair -> !pair.lines.isEmpty())
                .collect(Collectors.toMap(pair -> pair.key,
                        pair -> String.join(System.lineSeparator(), pair.lines),
                        (r, pair) -> r, LinkedHashMap::new));
        return new MapPropertySource(resource.toString(), sqlMap);
    }

    private static class Pair {
        private String key;
        private List<String> lines = new LinkedList<>();

        Pair(String key) {
            this.key = key;
        }
    }
}