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
    private static final double MIN_PERCENTAGE_CHANGE = 0.01;
    private static final double MAX_PERCENTAGE_CHANGE = 0.1;
    private static final int MIN_INIT_VALUE = 1000;
    private static final int MAX_INIT_VALUE = 1000000;

    /**
     * Desc: Randomly generate an double within the range determined by the initial value and a percentage change ranging from 1% to 10%.
     *
     * @param initialValue initial value
     * @param isDesc       true increment, false decrement, null random
     * @return {@link double}
     * @created by wWei
     * @date 2024/3/7 18:22
     */
    public static double generateRelativeRandomDouble(Double initialValue, Boolean isDesc) {
        Random random = new Random();
        if (initialValue == null) {
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
     * Desc: Randomly generate an integer within the range determined by the initial value and a percentage change ranging from 1% to 10%.
     *
     * @param initialValue initial value
     * @param isDesc       true increment, false decrement, null random
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

    /**
     * Desc: Randomly generate an integer within the range of [min, max]
     *
     * @param min
     * @param max
     * @return {@link int}
     * @created by wWei
     * @date 2024/3/11 15:49
     */
    public static int generateRangeRandomInt(int min, int max) {
        return new Random().nextInt(max - min + 1) + min;
    }

}
