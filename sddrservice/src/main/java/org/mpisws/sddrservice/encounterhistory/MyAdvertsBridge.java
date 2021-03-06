package org.mpisws.sddrservice.encounterhistory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.util.Pair;

import org.mpisws.sddrservice.lib.Identifier;

import java.util.ArrayList;
import java.util.List;

public class MyAdvertsBridge {
    private static final String TAG = MyAdvertsBridge.class.getSimpleName();
    private final Context context;

    public MyAdvertsBridge(final Context context) {
        this.context = context;
    }

    public List<Pair<Identifier,Identifier>> getMyUnpostedAdverts() {
        List<Pair<Identifier, Identifier>> results = new ArrayList<>();
        final Cursor cursor = context.getContentResolver().query(EncounterHistoryAPM.myAdverts.getContentURI(), null, null, null, null);
        while (cursor.moveToNext()) {
            Identifier advert = new Identifier(EncounterHistoryAPM.myAdverts.extractMyAdvert(cursor));
            Identifier dhpubkey = new Identifier(EncounterHistoryAPM.myAdverts.extractMyDHPubKey(cursor));
            Log.d(TAG, "Got " + advert.toString() + " " +dhpubkey.toString());
            results.add(new Pair(advert, dhpubkey));
        }
        cursor.close();
        return results;
    }

    public void deleteMyAdvert(Identifier myAdvert) {
        Log.d(TAG, "Deleted advert " + myAdvert.toString());
        context.getContentResolver().delete(EncounterHistoryAPM.myAdverts.getContentURI(),
                "HEX(" + PMyAdverts.Columns.myAdvert + ") = ?", new String[] {myAdvert.toString()});
    }

    public void insertMyAdvert(Identifier myAdvert, Identifier myDHPubKey) {
        final ContentValues values = new ContentValues();
        values.put(PMyAdverts.Columns.myAdvert, myAdvert.getBytes());
        values.put(PMyAdverts.Columns.myDHPubKey, myDHPubKey.getBytes());
        context.getContentResolver().insert(EncounterHistoryAPM.myAdverts.getContentURI(), values);
        Log.d(TAG, "Insert advert " + myAdvert.toString());
        Log.d(TAG, "Insert dhpubkey " + myDHPubKey.toString());
    }
}
