package com.miniso.ecomm.bootdemoapp.schedule;

import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class ParameterUtils {

    private static final long ONE_DAY_IN_MILLISECONDS = TimeUnit.DAYS.toMillis(1L);

    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private static final SimpleDateFormat SIMPLE_DATE_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

    private static final String START_TIME_SUFFIX = " 00:00:00";

    private static final String END_TIME_SUFFIX = " 23:59:59";

    public static String[] getDateRange(String dateRange) {
        String toDay = SIMPLE_DATE_FORMAT.format(new Date());
        String fromDay = SIMPLE_DATE_FORMAT.format(new Date(System.currentTimeMillis() - 7 * ONE_DAY_IN_MILLISECONDS));
        if (!StringUtils.isEmpty(dateRange)) {
            String[] range = dateRange.split(":");
            if (range.length > 1) {
                return range;
            }
        }

        return new String[]{fromDay, toDay};
    }

    public static Date[] getDateRangeObj(String dateRange) {
        Date toDay = new Date(),
                fromDay = new Date(System.currentTimeMillis() - 7 * ONE_DAY_IN_MILLISECONDS);
        if (!StringUtils.isEmpty(dateRange)) {
            String[] range = dateRange.split(":");
            if (range.length > 1) {
                try {
                    return new Date[]{SIMPLE_DATE_TIME_FORMAT.parse(range[0] + START_TIME_SUFFIX),
                            SIMPLE_DATE_TIME_FORMAT.parse(range[1] + END_TIME_SUFFIX)};
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return new Date[]{fromDay, toDay};
    }

    public static String[] getShopIds(String dateRange) {
        if (org.apache.commons.lang3.StringUtils.isBlank(dateRange) || dateRange.indexOf(":") < 0) {
            return null;
        }
        String[] args = dateRange.split(":");
        if (args.length >= 3) {
            return args[2].split(",");
        }
        return null;
    }
}
