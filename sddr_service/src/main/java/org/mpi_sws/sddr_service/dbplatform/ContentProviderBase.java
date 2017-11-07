package org.mpi_sws.sddr_service.dbplatform;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

import java.util.ArrayList;

public abstract class ContentProviderBase extends ContentProvider {

    private final String TAG = ContentProviderBase.class.getSimpleName();
    private final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
    protected SQLiteOpenHelper dbHelper;
    
    protected abstract AggregatePersistenceModel getAggregatePersistenceModel();
    protected abstract SQLiteOpenHelper getDBOpenHelper();

    private int modelToCode(final PersistenceModel model, final boolean isRow) {
        if (isRow) {
            return getAggregatePersistenceModel().getPersistenceModels().indexOf(model)+1000;
        } else {
            return getAggregatePersistenceModel().getPersistenceModels().indexOf(model)+2000;
        }
    }

    private MatchType codeToModel(final int code) {
        if (code == -1) {
            throw new IllegalArgumentException("Probably no match in matcher");
        } else if (code < 1500) {
            return new MatchType(getAggregatePersistenceModel().getPersistenceModels().get(code-1000), true);
        } else if (code > 1500) {
            return new MatchType(getAggregatePersistenceModel().getPersistenceModels().get(code-2000), false);
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public synchronized boolean onCreate() {
        dbHelper = getDBOpenHelper();
        for (PersistenceModel model : getAggregatePersistenceModel().getPersistenceModels()) {
            matcher.addURI(getAggregatePersistenceModel().getContentProviderAuthority(),
                    model.getTableName(), modelToCode(model, false));
            matcher.addURI(getAggregatePersistenceModel().getContentProviderAuthority(),
                    model.getTableName() + "/#", modelToCode(model, true));
        }
        return true;
    }

    @Override
    public synchronized Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        //Log.d(TAG, "Query starting:" + uri);
        final MatchType matchType = codeToModel(matcher.match(uri));
        final SQLiteDatabase db = dbHelper.getReadableDatabase();
        final String realselection =
                matchType.isRow() ? addPKIDConditionToWhereClause(selection, uri.getLastPathSegment()) : selection;
        final Cursor c = db.query(
                matchType.getModel().getTableName(), projection, realselection, selectionArgs, null, null, sortOrder);
        c.setNotificationUri(getContext().getContentResolver(), uri); // TODO ???
        //Log.d(TAG, "Query done");
        return c;
    }

    @Override
    public synchronized String getType(Uri uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized Uri insert(Uri uri, ContentValues values) {
        //Log.d(TAG, "Insert starting: " + uri);
        final SQLiteDatabase db = dbHelper.getWritableDatabase();
        final MatchType matchType = codeToModel(matcher.match(uri));
        final PersistenceModel dmb = matchType.getModel();
        final long rowID = db.insertOrThrow(dmb.getTableName(), null, values);
        if (rowID == -1) {
            throw new SQLException("Insert failed");
        }
        final Uri insertedRowURI = ContentUris.withAppendedId(dmb.getContentURI(), rowID);
        getContext().getContentResolver().notifyChange(insertedRowURI, null);
        //Log.d(TAG, "Insert done");
        return insertedRowURI;
    }

    public String addPKIDConditionToWhereClause(final String where, final String pkidString) { // TODO add to whereArgs instead
        final StringBuilder whereSB = new StringBuilder();
        if (where != null) {
            whereSB.append("(").append(where).append(") AND ");
        }
        whereSB.append("(" + PersistenceModel.Columns.pkid + " = ").append(pkidString).append(")");
        return whereSB.toString();
    }

    @Override
    public synchronized int delete(Uri uri, String where, String[] whereArgs) {
        //Log.d(TAG, "Delete starting: " + uri);
        final SQLiteDatabase db = dbHelper.getWritableDatabase();
        final MatchType matchType = codeToModel(matcher.match(uri));
        final String realWhere = matchType.isRow() ? addPKIDConditionToWhereClause(where, uri.getLastPathSegment()) : where;
        final int count = db.delete(matchType.getModel().getTableName(), realWhere, whereArgs);
        getContext().getContentResolver().notifyChange(uri, null);
        //Log.d(TAG, "Delete done");
        return count;
    }

    @Override
    public synchronized int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        //Log.d(TAG, "Update starting: " + uri);
        final SQLiteDatabase db = dbHelper.getWritableDatabase();
        final MatchType matchType = codeToModel(matcher.match(uri));
        final String realselection =
                matchType.isRow() ? addPKIDConditionToWhereClause(selection, uri.getLastPathSegment()) : selection;
        final int count = db.update(matchType.getModel().getTableName(), values, realselection, selectionArgs);
        getContext().getContentResolver().notifyChange(uri, null);
        //Log.d(TAG, "Update done");
        return count;
    }
    
    @Override
    public synchronized ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        final ContentProviderResult[] results = super.applyBatch(operations); // TODO ?!
        return results;
    }

}
