package org.mpisws.sddrservice.encounterhistory;

import android.database.Cursor;

import org.mpisws.sddrservice.dbplatform.AggregatePersistenceModel;
import org.mpisws.sddrservice.dbplatform.DBColumn;
import org.mpisws.sddrservice.dbplatform.PersistenceModel;

import java.util.LinkedList;
import java.util.List;

public class PNewAdverts extends PersistenceModel {

    public class Columns extends PersistenceModel.Columns {
        public static final String encounterPKID = "epkid";
        public static final String advert = "advert";
        public static final String myDHKey = "dhkey";
    }

    @Override
    public List<DBColumn> getColumns() {
        final List<DBColumn> columns = new LinkedList<DBColumn>();
        columns.add(new DBColumn(Columns.pkid, "INTEGER PRIMARY KEY AUTOINCREMENT"));
        columns.add(new DBColumn(Columns.encounterPKID, "INTEGER"));
        columns.add(new DBColumn(Columns.advert, "BLOB"));
        columns.add(new DBColumn(Columns.myDHKey, "BLOB"));
        return columns;
    }

    public long extractEPKID(final Cursor cursor) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(Columns.encounterPKID));
    }

    public byte[] extractNewAdvert(final Cursor cursor) {
        return cursor.getBlob(cursor.getColumnIndexOrThrow(Columns.advert));
    }

    public byte[] extractMyDHKey(final Cursor cursor) {
        return cursor.getBlob(cursor.getColumnIndexOrThrow(Columns.myDHKey));
    }

    @Override
    public AggregatePersistenceModel getAggregatePersistenceModel() {
        return EncounterHistoryAPM.getInstance();
    }

}
