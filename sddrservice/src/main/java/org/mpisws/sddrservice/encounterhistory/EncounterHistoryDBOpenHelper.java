package org.mpisws.sddrservice.encounterhistory;

import android.content.Context;

import org.mpisws.sddrservice.dbplatform.AggregatePersistenceModel;
import org.mpisws.sddrservice.dbplatform.DBOpenHelperBase;

/**
 *
 * @author verdelyi
 */
public class EncounterHistoryDBOpenHelper extends DBOpenHelperBase {

    private static final String DATABASE_NAME = "encounterHistory.db";
    private static final int DATABASE_VERSION = 1;

    public EncounterHistoryDBOpenHelper(final Context context) {
        super(context, DATABASE_NAME, DATABASE_VERSION);
    }

    @Override
    protected AggregatePersistenceModel getAggregatePersistenceModel() {
        return EncounterHistoryAPM.getInstance();
    }
}