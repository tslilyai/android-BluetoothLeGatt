package org.mpisws.sddrservice.encounterhistory;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import org.mpisws.sddrservice.lib.Identifier;

import java.util.ArrayList;
import java.util.List;

public class NewAdvertsBridge {
    private static final String TAG = NewAdvertsBridge.class.getSimpleName();
    private final Context context;

    public class NewAdvertData {
        public Identifier advert;
        public Identifier myDHKey;
        public long epkid;

        public NewAdvertData(Identifier advert, Identifier myDHKey, long epkid) {
            this.advert = advert;
            this.myDHKey = myDHKey;
            this.epkid = epkid;
        }
    }

    public NewAdvertsBridge(final Context context) {
        this.context = context;
    }

    public List<NewAdvertData> getMyUnconfirmedAdverts() {
        List<NewAdvertData> results = new ArrayList<>();
        final Cursor cursor = context.getContentResolver().query(EncounterHistoryAPM.newAdverts.getContentURI(), null, null, null, null);
        while (cursor.moveToNext()) {
            Identifier advert = new Identifier(EncounterHistoryAPM.newAdverts.extractNewAdvert(cursor));
            Identifier myDHkey = new Identifier(EncounterHistoryAPM.newAdverts.extractMyDHKey(cursor));
            long epkid = EncounterHistoryAPM.newAdverts.extractEPKID(cursor);
            Log.d(TAG, "Unconfirmed advert " + advert.toString() + " " + myDHkey.toString());
            results.add(new NewAdvertData(advert, myDHkey, epkid));
        }
        cursor.close();
        return results;
    }

    public void deleteNewAdvert(Identifier newAdvert) {
        Log.d(TAG, "Deleted advert " + newAdvert.toString());
        context.getContentResolver().delete(EncounterHistoryAPM.newAdverts.getContentURI(),
                "HEX(" + PNewAdverts.Columns.advert + ") = ?", new String[] {newAdvert.toString()});
    }
}
