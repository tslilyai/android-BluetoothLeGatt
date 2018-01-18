package org.mpisws.sddrservice.encounterhistory;

import android.database.Cursor;

import org.mpisws.sddrservice.dbplatform.AggregatePersistenceModel;
import org.mpisws.sddrservice.dbplatform.DBColumn;
import org.mpisws.sddrservice.dbplatform.PersistenceModel;

import java.util.LinkedList;
import java.util.List;

public class PMyAdverts extends PersistenceModel {

    public class Columns extends PersistenceModel.Columns {
        public static final String myadvert = "advert";
        public static final String myDHPubKey = "dhpubkey";
        public static final String myDHKey = "dhkey";
        public static final String postedTopic = "posted";
        public static final String postedComment = "commented";
    }

    @Override
    public List<DBColumn> getColumns() {
        final List<DBColumn> columns = new LinkedList<DBColumn>();
        columns.add(new DBColumn(Columns.myadvert, "BLOB PRIMARY KEY"));
        columns.add(new DBColumn(Columns.myDHPubKey, "BLOB"));
        columns.add(new DBColumn(Columns.myDHKey, "BLOB"));
        columns.add(new DBColumn(Columns.postedTopic, "INTEGER"));
        columns.add(new DBColumn(Columns.postedComment, "INTEGER"));
        return columns;
    }

    public byte[] extractAdvert(final Cursor cursor) {
        return cursor.getBlob(cursor.getColumnIndexOrThrow(Columns.myadvert));
    }

    public byte[] extractDHPubKey(final Cursor cursor) {
        return cursor.getBlob(cursor.getColumnIndexOrThrow(Columns.myDHPubKey));
    }

    public byte[] extractDHKey(final Cursor cursor) {
        return cursor.getBlob(cursor.getColumnIndexOrThrow(Columns.myDHKey));
    }

    public boolean extractPostedAdvert(final Cursor cursor) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(Columns.postedTopic)) != 0;
    }

    public boolean extractPostedKey(final Cursor cursor) {
        return cursor.getInt(cursor.getColumnIndexOrThrow(Columns.postedComment)) != 0;
    }

    @Override
    public AggregatePersistenceModel getAggregatePersistenceModel() {
        return EncounterHistoryAPM.getInstance();
    }

}
