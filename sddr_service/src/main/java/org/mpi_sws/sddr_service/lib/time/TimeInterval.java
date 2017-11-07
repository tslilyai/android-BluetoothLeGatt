package org.mpi_sws.sddr_service.lib.time;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class TimeInterval implements Serializable {

    private static final long serialVersionUID = -6870164433943316009L;
    private final Date startTime;
    private final Date endTime;

    public TimeInterval(Date startTime, Date endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public TimeInterval(long startTime, long endTime) { // TODO try to enforce startTime, endTime > 0
        this.startTime = new Date(startTime);
        this.endTime = new Date(endTime);
    }
    
    public TimeInterval(final TimeInterval ti) {
        this(ti.getStartL(), ti.getEndL());
    }
    
    public Date getStart() {
        return startTime;
    }

    public Date getEnd() {
        return endTime;
    }

    public long getDuration_ms() {
        return endTime.getTime() - startTime.getTime();
    }

    public long getStartL() {
        return startTime.getTime();
    }

    public long getEndL() {
        return endTime.getTime();
    }
    
    public long getMidpoint() {
        return (getStartL() + getEndL()) / 2;
    }
    
    public TimeInterval blindUnionWith(final TimeInterval other) { // simply take earlier start, later end
        final long newStart = Math.min(other.getStartL(), getStartL());
        final long newEnd = Math.max(other.getEndL(), getEndL());
        return new TimeInterval(newStart, newEnd);
    }

    public boolean overlapsWith(final TimeInterval other) {
        final long intersectionStart = Math.max(this.getStartL(), other.getStartL());
        final long intersectionEnd = Math.min(this.getEndL(), other.getEndL());
        return intersectionStart < intersectionEnd;
    }

    public static boolean areThere4OverlappingIntervals(final List<TimeInterval> timeIntervals) {
        for (TimeInterval t1 : timeIntervals) {
            for (TimeInterval t2 : timeIntervals) {
                for (TimeInterval t3 : timeIntervals) {
                    for (TimeInterval t4 : timeIntervals) {
                        if (t1 != t2 && t1 != t3 && t1 != t4 && t2 != t3 && t2 != t4 && t3 != t4) {
                            final TimeInterval[] fourIntervals = new TimeInterval[]{t1, t2, t3, t4};
                            if (TimeInterval.overlapWithEachOther(Arrays.asList(fourIntervals))) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public static boolean overlapWithEachOther(final List<TimeInterval> timeIntervals) {
        long intersectionStart = Long.MIN_VALUE; // max of starttimes
        long intersectionEnd = Long.MAX_VALUE; // min of endtimes
        for (TimeInterval timeInterval : timeIntervals) {
            if (timeInterval.getStartL() > intersectionStart) {
                intersectionStart = timeInterval.getStartL();
            }
            if (timeInterval.getEndL() < intersectionEnd) {
                intersectionEnd = timeInterval.getEndL();
            }
        }
        return intersectionStart < intersectionEnd;
    }

    public boolean contains(final long time) {
        return time >= startTime.getTime() && time <= endTime.getTime();
    }

    @Override
    public String toString() {
        return startTime + " - " + endTime;
    }
    
    public String toBriefStartEndString() {
        return TimeConversion.convertToMonthDayHourMin(startTime) + " - " + TimeConversion.convertToMonthDayHourMin(endTime);
    }
}
