package org.mpi_sws.sddr_userspace.encounterhistory;

import android.database.Cursor;

import org.mpi_sws.sddr_userspace.dbplatform.AggregatePersistenceModel;
import org.mpi_sws.sddr_userspace.dbplatform.DBColumn;
import org.mpi_sws.sddr_userspace.dbplatform.PersistenceModel;
import org.mpi_sws.sddr_userspace.lib.FacebookEventStatus;

import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author verdelyi
 */
public class PEncounters extends PersistenceModel {

    @Override
    public AggregatePersistenceModel getAggregatePersistenceModel() {
        return EncounterHistoryAPM.getInstance();
    }

    public class Columns extends PersistenceModel.Columns {

        public static final String startTime = "start";
        public static final String endTime = "end";
        public static final String commonIDs = "commonIDs";
        public static final String lastTimeSeen = "lastseen";
        public static final String confirmationTime = "conftime";
        public static final String currentWirelessAddress = "mac";
        public static final String facebookEventStatus = "facebookEventStatus";
        public static final String conduitID = "conduitID";
    }

    public List<DBColumn> getColumns() {
        final List<DBColumn> columns = new LinkedList<DBColumn>();
        columns.add(new DBColumn(Columns.pkid, "INTEGER PRIMARY KEY AUTOINCREMENT"));
        columns.add(new DBColumn(Columns.commonIDs, "BLOB"));
        columns.add(new DBColumn(Columns.startTime, "INTEGER"));
        columns.add(new DBColumn(Columns.endTime, "INTEGER"));
        columns.add(new DBColumn(Columns.lastTimeSeen, "INTEGER"));
        columns.add(new DBColumn(Columns.confirmationTime, "INTEGER"));
        columns.add(new DBColumn(Columns.currentWirelessAddress, "TEXT"));
        columns.add(new DBColumn(Columns.facebookEventStatus, "INTEGER"));
        columns.add(new DBColumn(Columns.conduitID, "INTEGER"));
        return columns;
    }

    public static long extractStartTime(final Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(Columns.startTime));
    }

    public static long extractEndTime(final Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(Columns.endTime));
    }

    public static byte[] extractCommonIDs(final Cursor cursor) {
        return cursor.getBlob(cursor.getColumnIndexOrThrow(Columns.commonIDs));
    }

    public static long extractLastTimeSeen(final Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(Columns.lastTimeSeen));
    }

    public static long extractConfirmationTime(final Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(Columns.confirmationTime));
    }

    public static String extractCurrentWirelessAddress(final Cursor cursor) {
        return cursor.getString(cursor.getColumnIndexOrThrow(Columns.currentWirelessAddress));
    }

    public static FacebookEventStatus extractFacebookEventStatus(final Cursor cursor) {
        return FacebookEventStatus.fromInt(cursor.getInt(cursor.getColumnIndexOrThrow(Columns.facebookEventStatus)));
    }

    public static long extractConduitID(final Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(Columns.conduitID));
    }
}
