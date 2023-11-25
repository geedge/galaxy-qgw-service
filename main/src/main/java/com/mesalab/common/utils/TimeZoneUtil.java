package com.mesalab.common.utils;

import java.util.TimeZone;

/**
 * @author tiangaojie
 */
public class TimeZoneUtil {

    public static String timeZoneTransition(String dbTimeZone) {
        int rawOffset = TimeZone.getTimeZone(dbTimeZone).getRawOffset();
        int hours = rawOffset / 3600 / 1000;
        int minute = (rawOffset % (3600 * 1000)) / 60;
        String standardMinute = String.format("%02d", Math.abs(minute));
        String standardHour = hours > 0 ? String.format("+%02d", hours) : String.format("-%02d", Math.abs(hours));
        return standardHour + standardMinute;
    }
}