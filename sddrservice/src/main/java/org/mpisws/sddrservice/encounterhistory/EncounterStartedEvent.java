package org.mpisws.sddrservice.encounterhistory;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.mpisws.sddrservice.lib.Identifier;

import java.util.List;

public class EncounterStartedEvent extends EncounterEvent {
    private static final String TAG = EncounterStartedEvent.class.getSimpleName();
    private static final long serialVersionUID = 3977070185056288814L;

    public EncounterStartedEvent(long pkid, long startTime, List<Identifier> adverts, Identifier myAdvert, Identifier myDHPubKey, Identifier myDHKey) {
        super(pkid, startTime, startTime, -1L, adverts, null, null, -1L, myAdvert, myDHPubKey, myDHKey, null);
    }

    @Override
    public void broadcast(Context context) {
        Log.v(TAG, "Broadcasting started encounter");
        final Intent i = new Intent(context, EncounterEventReceiver.class);
        i.putExtra("encounterEvent", this);
        context.sendBroadcast(i);
    }

    @Override
    public void persistIntoDatabase(Context context) {
        final ContentValues values = toContentValues(true);
        context.getContentResolver().insert(EncounterHistoryAPM.encounters.getContentURI(), values);
        insertLocationRSSIandAdverts(context);
    }
}
