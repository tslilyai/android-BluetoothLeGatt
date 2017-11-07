package org.mpi_sws.sddr_service.dbplatform;

import java.io.Serializable;

public abstract class AbstractMemoryObject implements Serializable {

    private static final long serialVersionUID = -4922745349919343427L;
    protected final Long pkid;

    public AbstractMemoryObject(final Long pkid) {
        this.pkid = pkid;
    }

    /** Primary key ID for the database */
    public long getPKID() {
        return pkid;
    }
    
    public Long getPKIDAsNullableLong() {
        return pkid;
    }

}
