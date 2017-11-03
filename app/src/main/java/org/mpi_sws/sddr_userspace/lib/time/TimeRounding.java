package org.mpi_sws.sddr_userspace.lib.time;

import org.mpi_sws.sddr_userspace.lib.Utils;

import java.util.Calendar;

/**
 *
 * @author verdelyi
 */
public class TimeRounding {

    public static int getRoundedMinute(final int minute) {
        Utils.myAssert(minute >= 0 && minute <= 60, "getRoundedMinute: minute out of range: " + minute);
        int mPlus = minute;
        while (mPlus % 5 != 0) {
            mPlus++;
        }

        int mMinus = minute;
        while (mMinus % 5 != 0) {
            mMinus--;
        }

        final long distanceFromPlus = Math.abs(mPlus - minute);
        final long distanceFromMinus = Math.abs(mMinus - minute);

        if (distanceFromPlus <= distanceFromMinus) {
            mPlus = mPlus % 60;
            Utils.myAssert(minute >= 0 && minute <= 60,
                    "getRoundedMinute: result minute out of range: " + mPlus + " orig " + minute);
            return mPlus;
        } else {
            mMinus = mMinus % 60;
            Utils.myAssert(minute >= 0 && minute <= 60,
                    "getRoundedMinute: result minute out of range: " + mMinus + " orig " + minute);
            return mMinus;
        }
    }

    public static boolean isRoundedToXMinutes(final long timeInMillis, final int minutes) {
        final Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timeInMillis);
        return c.get(Calendar.MINUTE) % minutes == 0 && c.get(Calendar.SECOND) == 0 && c.get(Calendar.MILLISECOND) == 0;
    }

    // TODO doesn't work with DST changes
    public static long getRoundedLong(long timeInMillis) {
        final Calendar cPlus = Calendar.getInstance();
        cPlus.setTimeInMillis(timeInMillis);
        cPlus.set(Calendar.SECOND, 0);
        cPlus.set(Calendar.MILLISECOND, 0);

        final Calendar cMinus = (Calendar) cPlus.clone();
        
        while (!isRoundedToXMinutes(cPlus.getTimeInMillis(), 5)) {
            cPlus.add(Calendar.MINUTE, 1);
        }

        while (!isRoundedToXMinutes(cMinus.getTimeInMillis(), 5)) {
            cMinus.add(Calendar.MINUTE, -1);
        }

        final long distanceFromPlus = Math.abs(cPlus.getTimeInMillis() - timeInMillis);
        final long distanceFromMinus = Math.abs(cMinus.getTimeInMillis() - timeInMillis);

        Utils.myAssert(distanceFromPlus < TimeConversion.min2ms(6),
                "TimeRounding: distanceFromPlus too big: cPlus " + cPlus.getTimeInMillis()
                + " orig " + timeInMillis + " distance " + distanceFromPlus);
        Utils.myAssert(distanceFromMinus < TimeConversion.min2ms(6),
                "TimeRounding: distanceFromMinus too big: cMinus " + cMinus.getTimeInMillis()
                + " orig " + timeInMillis + " distance " + distanceFromMinus);

        final Calendar result = (distanceFromPlus <= distanceFromMinus) ? cPlus : cMinus;
        return result.getTimeInMillis();
    }
}
