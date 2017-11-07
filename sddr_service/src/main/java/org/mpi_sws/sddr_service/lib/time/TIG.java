package org.mpi_sws.sddr_service.lib.time;

import java.util.GregorianCalendar;
import java.util.Random;

public class TIG {

    public final static Random THE_RNG_42 = new Random(42);
    public static final long t = new GregorianCalendar(2013, 8, 10, 14, 30).getTimeInMillis();

    public static TimeInterval get(final int tminusmin1, final int tminusmin2) {
        return get(tminusmin1, tminusmin2, 0, 0);
    }

    private static long getPlusMinusMins(final long i, final int pmMins) {
        return i - TimeConversion.min2ms(pmMins) + TimeConversion.min2ms(THE_RNG_42.nextInt(2 * pmMins + 1));
    }

    public static TimeInterval get(final int tminusmin1, final int tminusmin2, final int startPMMins, final int endPMMins) {
        return new TimeInterval(getPlusMinusMins(t - TimeConversion.min2ms(tminusmin1), startPMMins), getPlusMinusMins(t
                - TimeConversion.min2ms(tminusmin2), endPMMins));
    }

}
