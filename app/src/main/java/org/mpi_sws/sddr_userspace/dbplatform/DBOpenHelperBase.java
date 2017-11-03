package org.mpi_sws.sddr_userspace.dbplatform;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public abstract class DBOpenHelperBase extends SQLiteOpenHelper {

    private final static String TAG = DBOpenHelperBase.class.getSimpleName();

    public DBOpenHelperBase(final Context context, final String databaseName, final int databaseVersion) {
        super(context, databaseName, null, databaseVersion);
    }

    protected abstract AggregatePersistenceModel getAggregatePersistenceModel();

    public void recreateDatabase() {
        Log.d(TAG, "RecreateDatabase");
        final SQLiteDatabase db = getWritableDatabase();
        try {
            for (PersistenceModel model : getAggregatePersistenceModel().getPersistenceModels()) {
                model.drop(db);
            }
        } catch (final SQLException ex) {
            Log.d(getClass().getSimpleName(), "(Drop table failed)", ex);
        }
        onCreate(db);
        db.close();
    }
    
    private void createTables(final SQLiteDatabase db) {
        for (PersistenceModel model : getAggregatePersistenceModel().getPersistenceModels()) {
            model.createTable(db);
        }
    }

    @Override
    public void onCreate(final SQLiteDatabase db) {
        Log.d(TAG, "OnCreate");
        createTables(db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        Log.d(TAG, "OnOpen: creating tables if they don't exist");
        createTables(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }
}
