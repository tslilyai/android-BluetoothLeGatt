package org.mpi_sws.sddr_service.dbplatform;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

import org.mpi_sws.sddr_service.lib.Utils;

import java.util.List;

/**
 *
 * @author verdelyi
 */
public abstract class PersistenceModel {

    
    public class Columns {

        public static final String pkid = "_id";
    }

    public String getTableName() {
        return getClass().getSimpleName();
    }

    public abstract List<DBColumn> getColumns();
    
    public abstract AggregatePersistenceModel getAggregatePersistenceModel();
    
    public Uri getContentURI() {
        return Uri.parse("content://" + getAggregatePersistenceModel().getContentProviderAuthority() + "/" + getTableName());
    }
    
    public void drop(final SQLiteDatabase db) {
        db.execSQL("DROP TABLE " + getTableName());
    }

    public void createTable(final SQLiteDatabase db) {
        final StringBuilder sb = new StringBuilder();
        Log.d("PersistenceModel", "Creating table " + getTableName());
        sb.append("CREATE TABLE IF NOT EXISTS ").append(getTableName()).append(" (");
        sb.append(Utils.collectionToStringV2(getColumns(), ","));
        sb.append(")");
        db.execSQL(sb.toString());
    }

    public void clearTable(final SQLiteDatabase db) {
        db.delete(getTableName(), null, null);
    }
    
    public long extractPKID(final Cursor cursor) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(Columns.pkid));
    }
}
