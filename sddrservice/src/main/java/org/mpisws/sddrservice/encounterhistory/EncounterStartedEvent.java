package org.mpisws.sddrservice.encounterhistory;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.mpisws.sddrservice.encounters.SDDR_Proto;
import org.mpisws.sddrservice.lib.Identifier;

import java.util.List;

public class EncounterStartedEvent extends EncounterEvent {
    private static final String TAG = EncounterStartedEvent.class.getSimpleName();
    private static final long serialVersionUID = 3977070185056288814L;

    public EncounterStartedEvent(long pkid, long startTime) {
        super(pkid, startTime, startTime, -1L, null, null, null, null, null, -1L);
    }

    public EncounterStartedEvent(long pkid, long startTime, List<RSSIEntry> newRSSIEntries, List<Identifier> sharedSecrets,
                                 List<SDDR_Proto.Event.RetroactiveInfo.BloomInfo> blooms, List<Identifier> commonIDs, String currentWirelessAddress, long confirmationTime) {
        super(pkid, startTime, startTime, -1L, newRSSIEntries, sharedSecrets, blooms, commonIDs, currentWirelessAddress, confirmationTime);
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
        final ContentValues values = toContentValues(context, true);
        context.getContentResolver().insert(EncounterHistoryAPM.encounters.getContentURI(), values);
        insertSharedSecretsAndRSSIEntriesAndBlooms(context);
        insertLocation(context);
    }
}
