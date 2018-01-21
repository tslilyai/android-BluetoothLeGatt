package org.mpisws.sddrservice.encounterhistory;

import android.database.Cursor;

import org.mpisws.sddrservice.dbplatform.AggregatePersistenceModel;
import org.mpisws.sddrservice.dbplatform.DBColumn;
import org.mpisws.sddrservice.dbplatform.PersistenceModel;

import java.util.LinkedList;
import java.util.List;

public class PSharedSecrets extends PersistenceModel {

    public class Columns extends PersistenceModel.Columns {
        public static final String encounterPKID = "encPKID";
        public static final String sharedSecret = "ss";
        public static final String encounterID = "encID";
        public static final String timestamp = "time";
    }

    @Override
    public List<DBColumn> getColumns() {
        final List<DBColumn> columns = new LinkedList<DBColumn>();
        columns.add(new DBColumn(Columns.pkid, "INTEGER PRIMARY KEY AUTOINCREMENT"));
        columns.add(new DBColumn(Columns.encounterPKID, "INTEGER"));
        columns.add(new DBColumn(Columns.sharedSecret, "BLOB"));
        columns.add(new DBColumn(Columns.encounterID, "BLOB"));
        columns.add(new DBColumn(Columns.timestamp, "INTEGER"));
        return columns;
    }

    public long extractEncounterPKID(final Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(Columns.encounterPKID));
    }

    public byte[] extractSharedSecret(final Cursor cursor) {
        return cursor.getBlob(cursor.getColumnIndexOrThrow(Columns.sharedSecret));
    }
    
    public byte[] extractEncounterID(final Cursor cursor) {
        return cursor.getBlob(cursor.getColumnIndexOrThrow(Columns.encounterID));
    }

    public long extractTimestamp(final Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(Columns.timestamp));
    }
    
    @Override
    public AggregatePersistenceModel getAggregatePersistenceModel() {
        return EncounterHistoryAPM.getInstance();
    }

}
