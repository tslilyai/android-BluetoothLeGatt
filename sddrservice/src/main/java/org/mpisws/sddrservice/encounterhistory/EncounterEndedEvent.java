package org.mpisws.sddrservice.encounterhistory;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.util.List;

public class EncounterEndedEvent extends EncounterEvent {
    private static final String TAG = EncounterEndedEvent.class.getSimpleName();
    private static final long serialVersionUID = -6822428069212190684L;

    public EncounterEndedEvent(final long pkid, final long endTime, final List<RSSIEntry> rssiEntries, final String currentAddress) {
        super(pkid, null, endTime, endTime, null, rssiEntries, currentAddress, null);
    }

    @Override
    public void broadcast(Context context) {
        Log.v(TAG, "Broadcasting ended encounter");
        final Intent i = new Intent(context, EncounterEventReceiver.class);
        i.putExtra("encounterEvent", this);
        context.sendBroadcast(i);
    }

    @Override
    public void persistIntoDatabase(Context context) {
        final Uri uri = ContentUris.withAppendedId(EncounterHistoryAPM.encounters.getContentURI(), pkid);
        final ContentValues values = toContentValues(context, false);
        // TODO slight hack...
        // Update (regardless IF confirmed, *will* touch unconfirmed ones)
        context.getContentResolver().update(uri, values, null, null);
        // Delete IF unconfirmed, will not touch confirmed ones
        // We don't want to do this because we might confirm this retroactively
        //context.getContentResolver().delete(uri, PEncounters.Columns.confirmationTime + " == -1", null);
        insertLocationRSSIandAdverts(context);
    }
}
