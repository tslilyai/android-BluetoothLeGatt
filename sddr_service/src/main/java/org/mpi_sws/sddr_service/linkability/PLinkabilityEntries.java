package org.mpi_sws.sddr_service.linkability;

import android.database.Cursor;

import org.mpi_sws.sddr_service.dbplatform.AggregatePersistenceModel;
import org.mpi_sws.sddr_service.dbplatform.DBColumn;
import org.mpi_sws.sddr_service.dbplatform.PersistenceModel;
import org.mpi_sws.sddr_service.lib.Identifier;

import java.util.LinkedList;
import java.util.List;

public class PLinkabilityEntries extends PersistenceModel {

    @Override
    public AggregatePersistenceModel getAggregatePersistenceModel() {
        return LinkabilityAPM.getInstance();
    }

    public class Columns extends PersistenceModel.Columns {

        public static final String idValue = "idValue";
        public static final String principalName = "principal";
        public static final String mode = "mode";
        public static final String stickerID = "stickerID";
    }

    @Override
    public String getTableName() {
        return "linkabilityEntries";
    }

    @Override
    public List<DBColumn> getColumns() {
        final List<DBColumn> columns = new LinkedList<DBColumn>();
        columns.add(new DBColumn(Columns.pkid, "INTEGER PRIMARY KEY"));
        columns.add(new DBColumn(Columns.idValue, "BLOB UNIQUE"));
        columns.add(new DBColumn(Columns.principalName, "TEXT"));
        columns.add(new DBColumn(Columns.mode, "INTEGER"));
        columns.add(new DBColumn(Columns.stickerID, "INTEGER"));
        return columns;
    }
    
    public String extractPrincipal(final Cursor cursor) {
        return cursor.getString(cursor.getColumnIndexOrThrow(Columns.principalName));
    }

    public Identifier extractIDValue(final Cursor cursor) {
        return new Identifier(cursor.getBlob(cursor.getColumnIndexOrThrow(Columns.idValue)));
    }
    
    public LinkabilityEntryMode extractMode(final Cursor cursor) {
        return LinkabilityEntryMode.fromInt(cursor.getInt(cursor.getColumnIndexOrThrow(Columns.mode)));
    }
    
    public int extractStickerID(final Cursor cursor) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(Columns.stickerID));
    }
}
