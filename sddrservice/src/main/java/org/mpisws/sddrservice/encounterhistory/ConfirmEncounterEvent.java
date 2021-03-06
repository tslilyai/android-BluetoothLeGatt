package org.mpisws.sddrservice.encounterhistory;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import org.mpisws.sddrservice.lib.Identifier;

public class ConfirmEncounterEvent extends EncounterEvent {
    private static final String TAG = ConfirmEncounterEvent.class.getSimpleName();
    protected static long pkid;
    protected static Identifier sharedSecret;
    protected static Long confirmationTime;

    public ConfirmEncounterEvent(long pkid, Identifier sharedSecret, Long confirmationTime) {
        super(pkid, null, null, null, null, null, null, confirmationTime, null, null, null, sharedSecret);
        this.pkid = pkid;
        this.sharedSecret = sharedSecret;
        this.confirmationTime = confirmationTime;
    }

    @Override
    public void broadcast(Context context) {
        Log.v(TAG, "Broadcasting confirmed encounter");
        final Intent i = new Intent(context, EncounterEventReceiver.class);
        i.putExtra("encounterEvent", this);
        context.sendBroadcast(i);
    }

    @Override
    public void persistIntoDatabase(Context context) {
        final ContentValues values = toContentValues(false);
        final Uri uri = ContentUris.withAppendedId(EncounterHistoryAPM.encounters.getContentURI(), pkid);
        final int updatedRows = context.getContentResolver().update(uri, values, null, null);
        if (updatedRows != 1) {
            throw new RuntimeException("Confirm returned non-1 value: " + updatedRows);
        }
        insertSharedSecret(context);
    }
}
