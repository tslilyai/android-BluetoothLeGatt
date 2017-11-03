package org.mpi_sws.sddr_userspace.linkability;

import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;

import org.mpi_sws.sddr_userspace.dbplatform.AggregatePersistenceModel;
import org.mpi_sws.sddr_userspace.dbplatform.ContentProviderBase;

/**
 * 
 * @author verdelyi
 */
public class LinkabilityContentProvider extends ContentProviderBase {

    @Override
    protected AggregatePersistenceModel getAggregatePersistenceModel() {
        return LinkabilityAPM.getInstance();
    }

    @Override
    protected SQLiteOpenHelper getDBOpenHelper() {
        return new LinkabilityDBOpenHelper(getContext());
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        // TODO do we need this?
        return Bundle.EMPTY;
    }
}
