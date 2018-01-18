package org.mpisws.sddrservice.encounterhistory;

import android.database.Cursor;

import org.mpisws.sddrservice.dbplatform.AggregatePersistenceModel;
import org.mpisws.sddrservice.dbplatform.DBColumn;
import org.mpisws.sddrservice.dbplatform.PersistenceModel;

import java.util.LinkedList;
import java.util.List;

public class PMyAdverts extends PersistenceModel {

    public class Columns extends PersistenceModel.Columns {
        public static final String myAdvert = "advert";
        public static final String myDHPubKey = "dhpubkey";
        public static final String myDHKey = "dhkey";
    }

    @Override
    public List<DBColumn> getColumns() {
        final List<DBColumn> columns = new LinkedList<DBColumn>();
        columns.add(new DBColumn(Columns.pkid, "INTEGER PRIMARY KEY AUTOINCREMENT"));
        columns.add(new DBColumn(Columns.myAdvert, "BLOB"));
        columns.add(new DBColumn(Columns.myDHPubKey, "BLOB"));
        columns.add(new DBColumn(Columns.myDHKey, "BLOB"));
        return columns;
    }

    public byte[] extractAdvert(final Cursor cursor) {
        return cursor.getBlob(cursor.getColumnIndexOrThrow(Columns.myAdvert));
    }

    public byte[] extractDHPubKey(final Cursor cursor) {
        return cursor.getBlob(cursor.getColumnIndexOrThrow(Columns.myDHPubKey));
    }

    public byte[] extractDHKey(final Cursor cursor) {
        return cursor.getBlob(cursor.getColumnIndexOrThrow(Columns.myDHKey));
    }

    @Override
    public AggregatePersistenceModel getAggregatePersistenceModel() {
        return EncounterHistoryAPM.getInstance();
    }

}
