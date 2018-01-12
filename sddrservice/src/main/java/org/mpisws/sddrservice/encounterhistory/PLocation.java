package org.mpisws.sddrservice.encounterhistory;

import android.database.Cursor;

import org.mpisws.sddrservice.dbplatform.AggregatePersistenceModel;
import org.mpisws.sddrservice.dbplatform.DBColumn;
import org.mpisws.sddrservice.dbplatform.PersistenceModel;

import java.util.LinkedList;
import java.util.List;

public class PLocation extends PersistenceModel {

    public class Columns extends PersistenceModel.Columns {
        public static final String encounterPKID = "encPKID";
        public static final String latitude = "lat";
        public static final String longitude = "long";
        public static final String timestamp = "time";
    }

    @Override
    public List<DBColumn> getColumns() {
        final List<DBColumn> columns = new LinkedList<DBColumn>();
        columns.add(new DBColumn(Columns.pkid, "INTEGER PRIMARY KEY AUTOINCREMENT"));
        columns.add(new DBColumn(Columns.encounterPKID, "INTEGER"));
        columns.add(new DBColumn(Columns.latitude, "REAL"));
        columns.add(new DBColumn(Columns.longitude, "REAL"));
        columns.add(new DBColumn(Columns.timestamp, "INTEGER"));
        return columns;
    }

    public long extractEncounterPKID(final Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(Columns.encounterPKID));
    }

    public double extractLatitude(final Cursor cursor) {
        return cursor.getDouble(cursor.getColumnIndexOrThrow(Columns.latitude));
    }

    public double extractLongitude(final Cursor cursor) {
        return cursor.getDouble(cursor.getColumnIndexOrThrow(Columns.longitude));
    }

    public long extractTimestamp(final Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(Columns.timestamp));
    }
    
    @Override
    public AggregatePersistenceModel getAggregatePersistenceModel() {
        return EncounterHistoryAPM.getInstance();
    }

}
