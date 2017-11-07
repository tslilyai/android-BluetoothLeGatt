package org.mpi_sws.sddr_service.encounterhistory;

import android.database.Cursor;

import org.mpi_sws.sddr_service.dbplatform.AggregatePersistenceModel;
import org.mpi_sws.sddr_service.dbplatform.DBColumn;
import org.mpi_sws.sddr_service.dbplatform.PersistenceModel;
import org.mpi_sws.sddr_service.lib.time.TimeInterval;

import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author verdelyi
 */
public class PDiscoveryEvents extends PersistenceModel {

    @Override
    public AggregatePersistenceModel getAggregatePersistenceModel() {
        return EncounterHistoryAPM.getInstance();
    }

    public class Columns extends PersistenceModel.Columns {

        public static final String encounterPKID = "encPKID";
        public static final String blockStartTime = "bStart";
        public static final String blockEndTime = "bEnd";
        public static final String avgRSSI = "avgRSSI";
    }

    @Override
    public List<DBColumn> getColumns() {
        final List<DBColumn> columns = new LinkedList<DBColumn>();
        columns.add(new DBColumn(Columns.pkid, "INTEGER PRIMARY KEY AUTOINCREMENT"));
        columns.add(new DBColumn(Columns.encounterPKID, "INTEGER"));
        columns.add(new DBColumn(Columns.blockStartTime, "INTEGER"));
        columns.add(new DBColumn(Columns.blockEndTime, "INTEGER"));
        columns.add(new DBColumn(Columns.avgRSSI, "REAL"));
        return columns;
    }

    public long extractEncounterPKID(final Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(Columns.encounterPKID));
    }

    public TimeInterval extractBlockInterval(final Cursor cursor) {
        return new TimeInterval(cursor.getLong(cursor.getColumnIndexOrThrow(Columns.blockStartTime)), cursor.getLong(cursor
                .getColumnIndexOrThrow(Columns.blockEndTime)));
    }

    public float extractAvgRSSI(final Cursor cursor) {
        return cursor.getFloat(cursor.getColumnIndexOrThrow(Columns.avgRSSI));
    }
}
