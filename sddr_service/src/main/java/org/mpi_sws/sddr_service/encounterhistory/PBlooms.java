package org.mpi_sws.sddr_service.encounterhistory;

import android.database.Cursor;

import org.mpi_sws.sddr_service.dbplatform.AggregatePersistenceModel;
import org.mpi_sws.sddr_service.dbplatform.DBColumn;
import org.mpi_sws.sddr_service.dbplatform.PersistenceModel;

import java.util.LinkedList;
import java.util.List;

public class PBlooms extends PersistenceModel {

    public class Columns extends PersistenceModel.Columns {
        public static final String encounterPKID = "encPKID";
        public static final String bloom = "bloom";
        public static final String encounterID = "encID";
        public static final String timestamp = "time";
    }

    @Override
    public List<DBColumn> getColumns() {
        final List<DBColumn> columns = new LinkedList<DBColumn>();
        columns.add(new DBColumn(Columns.pkid, "INTEGER PRIMARY KEY AUTOINCREMENT"));
        columns.add(new DBColumn(Columns.encounterPKID, "INTEGER"));
        columns.add(new DBColumn(Columns.bloom, "BLOB"));
        columns.add(new DBColumn(Columns.timestamp, "INTEGER"));
        return columns;
    }

    public long extractEncounterPKID(final Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(Columns.encounterPKID));
    }

    public byte[] extractBloom(final Cursor cursor) {
        return cursor.getBlob(cursor.getColumnIndexOrThrow(Columns.bloom));
    }

    public long extractTimestamp(final Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(Columns.timestamp));
    }
    
    @Override
    public AggregatePersistenceModel getAggregatePersistenceModel() {
        return EncounterHistoryAPM.getInstance();
    }

}
