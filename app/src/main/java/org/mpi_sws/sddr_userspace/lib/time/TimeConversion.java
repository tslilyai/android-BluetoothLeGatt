package org.mpi_sws.sddr_userspace.lib.time;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 *
 * @author verdelyi
 */
public class TimeConversion {
    
    private static final SimpleDateFormat monthDayHourMinSDF = new SimpleDateFormat("MMM dd, HH:mm");
    private static final DecimalFormat twoDigitIntegralDF = new DecimalFormat("00");
    private static final SimpleDateFormat dayMonthSDF = new SimpleDateFormat("dd MMM");
    
    public static String convertToMonthDayHourMin(final Date d) {
        return monthDayHourMinSDF.format(d);
    }
    
    public static String convertToMonthDayHourMin(final long t, final String negativeString) {
        if (t < 0) {
            return negativeString;
        } else {
            return convertToMonthDayHourMin(new Date(t));
        }
    }
    
    public static String convertToMonthDayHourMin(final long t) {
        return convertToMonthDayHourMin(t, "N/A");
    }
    
    public static String convertToDateString(final long t) {
        final Calendar c = new GregorianCalendar();
        c.setTimeInMillis(t);
        return convertToDateString(c);
    }
    
    public static String convertToTimeString(final long t) {
        final Calendar c = new GregorianCalendar();
        c.setTimeInMillis(t);
        return convertToTimeString(c);
    }
    
    public static String convertToDateString(final Calendar cal) {
        return dayMonthSDF.format(cal.getTime());
    }
    
    public static String convertToTimeString(final Calendar cal) {
        return convertToTimeString(cal, ":");
    }
    
    public static String convertToTimeString(final long t, final String separator) {
        final Calendar c = new GregorianCalendar();
        c.setTimeInMillis(t);
        return convertToTimeString(c, separator);
    }
    
    public static String convertToTimeString(final Calendar cal, final String separator) {
        final StringBuilder sb = new StringBuilder();
        sb.append(twoDigitIntegralDF.format(cal.get(Calendar.HOUR_OF_DAY))).append(separator);
        sb.append(twoDigitIntegralDF.format(cal.get(Calendar.MINUTE)));
        return sb.toString();
    }
    
    public static long min2ms(final double min) {
        return (long)(min * 60 * 1000);
    }

    public static long hour2ms(final int hour) {
        return min2ms(hour * 60);
    }
}
