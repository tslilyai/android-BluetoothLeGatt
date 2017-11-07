package org.mpi_sws.sddr_service.linkability;

import android.content.Context;

import org.mpi_sws.sddr_service.dbplatform.AggregatePersistenceModel;
import org.mpi_sws.sddr_service.dbplatform.DBOpenHelperBase;

/**
 *
 * @author verdelyi
 */
public class LinkabilityDBOpenHelper extends DBOpenHelperBase {

    private static final String DATABASE_NAME = "linkability.db";
    private static final int DATABASE_VERSION = 1;

    public LinkabilityDBOpenHelper(final Context context) {
        super(context, DATABASE_NAME, DATABASE_VERSION);
    }

    @Override
    protected AggregatePersistenceModel getAggregatePersistenceModel() {
        return LinkabilityAPM.getInstance();
    }
}