package org.mpi_sws.sddr_userspace.encounterhistory;

import org.mpi_sws.sddr_userspace.dbplatform.AbstractMemoryObject;
import org.mpi_sws.sddr_userspace.lib.time.TimeInterval;

/**
 *
 * @author verdelyi
 */
public class MDiscoveryEvent extends AbstractMemoryObject {
    private final long encounterPKID;
    private final TimeInterval blockInterval;
    private final float avgRSSI;

    public MDiscoveryEvent(Long pkid, long encounterPKID, TimeInterval blockInterval, float avgRSSI) {
        super(pkid);
        this.encounterPKID = encounterPKID;
        this.blockInterval = blockInterval;
        this.avgRSSI = avgRSSI;
    }

    public long getEncounterPKID() {
        return encounterPKID;
    }

    public TimeInterval getBlockInterval() {
        return blockInterval;
    }

    public float getAvgRSSI() {
        return avgRSSI;
    }

}
