package com.mesalab.common.utils;

import java.text.DecimalFormat;
import java.util.Random;

/**
 * Utility class for generating random numbers.
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

    private static final Random RANDOM = new Random();
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");

    /**
     * Desc: Randomly generate a double within the range determined by the initial value and a percentage change ranging from 1% to 10%.
     *
     * @param initialValue initial value, null to generate a random initial value within the range
     * @param isDesc       true for decrement, false for increment, null for random choice
     * @return a double within the determined range
     */
    public static double generateRelativeRandomDouble(Double initialValue, Boolean isDesc) {
        if (initialValue == null) {
            initialValue = MIN_INIT_VALUE + (MAX_INIT_VALUE - MIN_INIT_VALUE) * RANDOM.nextDouble();
        }

        if (isDesc == null) {
            isDesc = RANDOM.nextBoolean();
        }

        double percentageChange = MIN_PERCENTAGE_CHANGE + RANDOM.nextDouble() * (MAX_PERCENTAGE_CHANGE - MIN_PERCENTAGE_CHANGE);
        double result = isDesc ? initialValue * (1 - percentageChange) : initialValue * (1 + percentageChange);
        return Double.parseDouble(DECIMAL_FORMAT.format(result));
    }

    /**
     * Desc: Randomly generate an integer within the range determined by the initial value and a percentage change ranging from 1% to 10%.
     *
     * @param initialValue initial value, null to generate a random initial value within the range
     * @param isDesc       true for decrement, false for increment, null for random choice
     * @return an integer within the determined range
     */
    public static int generateRelativeRandomInt(Integer initialValue, Boolean isDesc) {
        if (initialValue == null) {
            initialValue = RANDOM.nextInt(MAX_INIT_VALUE - MIN_INIT_VALUE + 1) + MIN_INIT_VALUE;
        }

        if (isDesc == null) {
            isDesc = RANDOM.nextBoolean();
        }

        double percentageChange = MIN_PERCENTAGE_CHANGE + RANDOM.nextDouble() * (MAX_PERCENTAGE_CHANGE - MIN_PERCENTAGE_CHANGE);
        double result = isDesc ? initialValue * (1 - percentageChange) : initialValue * (1 + percentageChange);
        return (int) result;
    }

    /**
     * Desc: Randomly generate an integer within the range of [min, max].
     *
     * @param min the minimum value of the range
     * @param max the maximum value of the range
     * @return a random integer within the specified range
     */
    public static int generateRangeRandomInt(int min, int max) {
        return RANDOM.nextInt(max - min + 1) + min;
    }
}
