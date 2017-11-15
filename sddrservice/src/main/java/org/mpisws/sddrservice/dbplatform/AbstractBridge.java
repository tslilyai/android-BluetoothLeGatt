package org.mpisws.sddrservice.dbplatform;

import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public abstract class AbstractBridge<T extends AbstractMemoryObject> {

    protected final Context context;

    public AbstractBridge(Context context) {
        this.context = context;
    }

    protected abstract PersistenceModel getPersistenceModel();

    protected abstract ContentValues itemToContentValues(T item);

    protected abstract T cursorToItem(final Cursor cursor);

    protected String getTag() {
        return AbstractBridge.class.getSimpleName();
    }

    public ContentProviderOperation getAddEntryOperation(final T item) {
        return ContentProviderOperation.newInsert(getPersistenceModel().getContentURI()).withValues(itemToContentValues(item))
                .build();
    }
    
    public ContentProviderOperation getRemoveAllItemsOperation() {
        return ContentProviderOperation.newDelete(getPersistenceModel().getContentURI()).build();
    }

    /**
     * @return the PKID of the inserted item
     */
    public long addItem(final T item) {
        final ContentValues values = itemToContentValues(item);
        final Uri insertedItem = context.getContentResolver().insert(getPersistenceModel().getContentURI(), values);
        return Long.parseLong(insertedItem.getLastPathSegment());
    }

    public List<T> getAllItems() {
        final JavaItemFilter<T> filter = new JavaItemFilter<T>() {

            @Override
            public boolean isNeeded(T item) {
                return true;
            }
        };
        return getFilteredItems(filter);
    }

    public List<T> getFilteredItems(final JavaItemFilter<T> filter) {
        final List<T> result = new LinkedList<T>();
        final Cursor cursor = context.getContentResolver().query(getPersistenceModel().getContentURI(), null, null, null, null);
        while (cursor.moveToNext()) {
            final T item = cursorToItem(cursor);
            if (filter.isNeeded(item)) {
                result.add(item);
            }
        }
        cursor.close();
        return result;
    }

    public T getItemByPKID(final long pkid) {
        T result;
        final Uri uri = ContentUris.withAppendedId(getPersistenceModel().getContentURI(), pkid);
        final Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        if (cursor.moveToNext()) {
            result = cursorToItem(cursor);
        } else {
            result = null;
        }
        assert(cursor.getCount() <= 1);
        cursor.close();
        return result;
    }

    public T getItemByColumn(final String column, final String value) {
        T result;
        final Cursor cursor = context.getContentResolver().query(getPersistenceModel().getContentURI(), null, column + " = ?",
                new String[] { value }, null);
        if (cursor.moveToNext()) {
            result = cursorToItem(cursor);
        } else {
            result = null;
        }
        assert(cursor.getCount() <= 1);
        cursor.close();
        return result;
    }

    public void updateFromContentValues(final long pkid, final ContentValues values) {
        Log.d(getTag(), "UpdateFromContentValues: pkid " + pkid + " #columns in values: " + values.keySet().size());
        final int updated = context.getContentResolver().update(
                ContentUris.withAppendedId(getPersistenceModel().getContentURI(), pkid), values, null, null);
        assert(updated == 1);
    }

    public void updateLongColumn(final long pkid, final String column, final Long value) {
        Log.d(getTag(), "UpdateColumn: pkid " + pkid + ", col " + column + ", val " + value);
        final ContentValues values = new ContentValues();
        values.put(column, value);
        updateFromContentValues(pkid, values);
    }

    public void removeAllItems() {
        context.getContentResolver().delete(getPersistenceModel().getContentURI(), null, null);
    }

    public void removeItemByPKID(final long pkid) {
        final Uri uri = ContentUris.withAppendedId(getPersistenceModel().getContentURI(), pkid);
        final int deleted = context.getContentResolver().delete(uri, null, null);
        assert(deleted == 1);
    }

    public void removeSingleItemWhereColumnEquals(final String column, final String value) {
        final int deleted = context.getContentResolver().delete(getPersistenceModel().getContentURI(), column + " = ?",
                new String[] { value });
        assert(deleted == 1);
    }

    public List<T> itemPKIDsToItems(final Collection<Long> itemPKIDs) {
        final List<T> result = new LinkedList<T>();
        for (long pkid : itemPKIDs) {
            final T item = getItemByPKID(pkid);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    public List<Long> itemsToPKIDs(final Collection<T> items) {
        final List<Long> result = new LinkedList<Long>();
        for (T item : items) {
            result.add(item.getPKID());
        }
        return result;
    }

}
