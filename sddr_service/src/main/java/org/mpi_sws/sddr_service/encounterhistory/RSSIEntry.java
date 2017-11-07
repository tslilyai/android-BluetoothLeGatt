package org.mpi_sws.sddr_service.encounterhistory;

import java.io.Serializable;

/**
 *
 * @author verdelyi
 */
public class RSSIEntry implements Serializable {

    private static final long serialVersionUID = -6927871720339378052L;
    private final long timestamp;
    private final int rssi;

    public RSSIEntry(long timestamp, int rssi) {
        this.timestamp = timestamp;
        this.rssi = rssi;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getRssi() {
        return rssi;
    }
}
