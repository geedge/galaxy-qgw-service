package com.mesalab.common.utils;

import java.text.DecimalFormat;
import java.util.Random;

/**
 * TODO
 *
 * @Classname RandomNumberGenerator
 * @Date 2024/3/7 18:16
 * @Author wWei
 */

public class RandomNumberGenerator {
    private static final double MIN_PERCENTAGE_CHANGE = 0.1;
    private static final double MAX_PERCENTAGE_CHANGE = 0.2;
    private static final int MIN_INIT_VALUE = 1000;
    private static final int MAX_INIT_VALUE = 1000000;

    /**
     * Desc: TODO
     *
     * @param initialValue 初始值
     * @param isDesc       true 递增, false 递减, null 随机
     * @return {@link double}
     * @created by wWei
     * @date 2024/3/7 18:22
     */
    public static double generateRelativeRandomDouble(Double initialValue, Boolean isDesc) {
        Random random = new Random();
        if (isDesc == null) {
            initialValue = MIN_INIT_VALUE + (MAX_INIT_VALUE - MIN_INIT_VALUE) * random.nextDouble();
        }
        DecimalFormat df = new DecimalFormat("#.##");

        if (isDesc == null) {
            isDesc = random.nextBoolean();
        }
        double percentageChange = MIN_PERCENTAGE_CHANGE + random.nextDouble() * (MAX_PERCENTAGE_CHANGE - MIN_PERCENTAGE_CHANGE);
        double v = !isDesc ? initialValue * (1 + percentageChange) : initialValue * (1 - percentageChange);
        return Double.parseDouble(df.format(v));
    }

    /**
     * Desc: TODO
     *
     * @param initialValue 初始值
     * @param isDesc       true 递增, false 递减, null 随机
     * @return {@link double}
     * @created by wWei
     * @date 2024/3/7 18:22
     */
    public static int generateRelativeRandomInt(Integer initialValue, Boolean isDesc) {
        Random random = new Random();
        if (initialValue == null) {
            initialValue = random.nextInt(MAX_INIT_VALUE - MIN_INIT_VALUE + 1) + MIN_INIT_VALUE;
        }

        if (isDesc == null) {
            isDesc = random.nextBoolean();
        }
        double percentageChange = MIN_PERCENTAGE_CHANGE + random.nextDouble() * (MAX_PERCENTAGE_CHANGE - MIN_PERCENTAGE_CHANGE);
        double v = !isDesc ? initialValue * (1 + percentageChange) : initialValue * (1 - percentageChange);
        return (int) v;
    }

    public static int generateRangeRandomInt(int min, int max) {
        return new Random().nextInt(max - min + 1) + min;
    }

}
