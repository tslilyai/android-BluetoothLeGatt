package org.mpisws.sddrservice.encounterhistory;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import org.mpisws.sddrservice.lib.Identifier;
import org.mpisws.sddrservice.lib.Utils;

import java.util.List;

public class EncounterRetroactiveEvent extends EncounterEvent {
    private static final String TAG = Utils.getTAG(EncounterRetroactiveEvent.class);
    private static final long serialVersionUID = 1765308277559790385L;

    public EncounterRetroactiveEvent(long pkid, List<Identifier> commonIDs) {
        super(pkid, null, null, null, null, null, null, null);
    }

    @Override
    public void broadcast(Context context) {
        Log.v(TAG, "Broadcasting retroactive encounter");
        final Intent i = new Intent(context, EncounterEventReceiver.class);
        i.putExtra("encounterEvent", this);
        context.sendBroadcast(i);
    }

    @Override
    public void persistIntoDatabase(Context context) { // TODO applybatch for all events
        final ContentValues values = toContentValues(context, false);
        final Uri uri = ContentUris.withAppendedId(EncounterHistoryAPM.encounters.getContentURI(), pkid);
        final int updatedRows = context.getContentResolver().update(uri, values, null, null);
        if (updatedRows != 1) {
            throw new RuntimeException("Retroactive update returned non-1 value: " + updatedRows);
        }
    }
}
