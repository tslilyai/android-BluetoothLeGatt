package org.mpisws.sddrservice.encounterhistory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Pair;

import org.mpisws.sddrservice.lib.Identifier;

import java.util.ArrayList;
import java.util.List;

public class MyAdvertsBridge {

    private final Context context;

    public MyAdvertsBridge(final Context context) {
        this.context = context;
    }

    public List<Pair<Identifier,Identifier>> getAdvertsUnposted() {
        List<Pair<Identifier, Identifier>> results = new ArrayList<>();
        final Cursor cursor = context.getContentResolver().query(EncounterHistoryAPM.myAdverts.getContentURI(), null,
                PMyAdverts.Columns.postedComment + " = ?", new String[] { String.valueOf(0) }, null);
        while (cursor.moveToNext()) {
            results.add(new Pair(EncounterHistoryAPM.myAdverts.extractAdvert(cursor), EncounterHistoryAPM.myAdverts.extractDHPubKey(cursor)));
        }
        cursor.close();
        return results;
    }

    public Pair<Boolean,Boolean> isAdvertAndDHPubKeyPosted(final Identifier myAdvert) {
        boolean result1 = false;
        boolean result2 = false;
        final Cursor cursor = context.getContentResolver().query(EncounterHistoryAPM.myAdverts.getContentURI(), null,
                PMyAdverts.Columns.myadvert + " = ?", new String[] { myAdvert.toString() }, null);
        if (cursor.moveToNext()) {
            result1 = EncounterHistoryAPM.myAdverts.extractPostedAdvert(cursor);
            result2 = EncounterHistoryAPM.myAdverts.extractPostedKey(cursor);
        }
        cursor.close();
        return new Pair(result1, result2);
    }

    protected ContentValues toContentValues(boolean postedAdvert, boolean postedDHPubKey) {
        ContentValues values = new ContentValues();
        if (postedAdvert) {
            values.put(PEncounters.Columns.myAdvert, postedAdvert);
        }
        if (postedDHPubKey) {
            values.put(PEncounters.Columns.myAdvert, postedDHPubKey);
        }
        return values;
    }

    public void updatePostedAdvert(final Identifier myAdvert) {
        final ContentValues values = toContentValues(true, false);
        final Uri uri = EncounterHistoryAPM.myAdverts.getContentURI();
        final int updatedRows = context.getContentResolver().update(uri, values, PMyAdverts.Columns.myadvert + " = ?", new String[] { myAdvert.toString() });
        if (updatedRows != 1) {
            throw new RuntimeException("Update returned non-1 value: " + updatedRows);
        }
    }
    public void updatePostedKey(final Identifier myAdvert) {
        final ContentValues values = toContentValues(false, true);
        final Uri uri = EncounterHistoryAPM.myAdverts.getContentURI();
        final int updatedRows = context.getContentResolver().update(uri, values, PMyAdverts.Columns.myadvert + " = ?", new String[] { myAdvert.toString() });
        if (updatedRows != 1) {
            throw new RuntimeException("Update returned non-1 value: " + updatedRows);
        }
    }
}
