package org.mpisws.sddrservice.encounterhistory;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;

import org.mpisws.sddrservice.dbplatform.AggregatePersistenceModel;
import org.mpisws.sddrservice.dbplatform.ContentProviderBase;
import org.mpisws.sddrservice.encounterhistory.EncounterBridge.Methods.FinalizeAbandonedEncounters;
import org.mpisws.sddrservice.encounterhistory.EncounterBridge.Methods.GetAvgRSSIForEncounter;

/**
 * 
 * @author verdelyi
 */
public class EncounterHistoryContentProvider extends ContentProviderBase {

    @Override
    protected AggregatePersistenceModel getAggregatePersistenceModel() {
        return EncounterHistoryAPM.getInstance();
    }

    @Override
    protected SQLiteOpenHelper getDBOpenHelper() {
        return new EncounterHistoryDBOpenHelper(getContext());
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (FinalizeAbandonedEncounters.name.equals(method)) {
            final SQLiteDatabase db = dbHelper.getWritableDatabase();
            final String encTable = EncounterHistoryAPM.encounters.getTableName();
            final String endTimeCol = PEncounters.Columns.endTime;
            final String lastTimeSeenCol = PEncounters.Columns.lastTimeSeen;
            db.beginTransaction();
            // Remove unconfirmed encounters
            db.delete(encTable, PEncounters.Columns.confirmationTime + " = -1", null);
            // End normal encounters
            db.execSQL("UPDATE " + encTable + " SET " + endTimeCol + " = " + lastTimeSeenCol + " WHERE " + endTimeCol + " = -1");
            db.setTransactionSuccessful();
            db.endTransaction();
            return Bundle.EMPTY;
        } else if (GetAvgRSSIForEncounter.name.equals(method)) {
            final String rssiCol = "avgavgrssi", countCol = "avgavgrssicount";
            final Bundle result = new Bundle();
            final SQLiteDatabase db = dbHelper.getReadableDatabase();
            final String[] projection = new String[] { "AVG(" + PDiscoveryEvents.Columns.avgRSSI + ") AS " + rssiCol,
                    "COUNT(" + PDiscoveryEvents.Columns.avgRSSI + ") AS " + countCol};
            final String[] selectionArgs = new String[] { String.valueOf(extras
                    .getLong(GetAvgRSSIForEncounter.ParameterKeys.encounterPKID)) };
            final Cursor cursor = db.query(EncounterHistoryAPM.discoveryEvents.getTableName(), projection,
                    PDiscoveryEvents.Columns.encounterPKID + " = ?", selectionArgs, null, null, null);
            if (cursor.moveToNext()) {
                final int count = cursor.getInt(cursor.getColumnIndexOrThrow(countCol));
                final float avgrssi = cursor.getFloat(cursor.getColumnIndexOrThrow(rssiCol));
                result.putFloat(GetAvgRSSIForEncounter.ResultKeys.avgRSSI, count > 0 ? avgrssi : -9999f ); // TODO -9999 HACK
            } else {
                throw new IllegalStateException();
            }
            cursor.close();
            return result;
        } else {
            throw new IllegalArgumentException();
        }
    }
}
