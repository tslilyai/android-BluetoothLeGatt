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

public class ConfirmEncounterEvent extends EncounterEvent {
    private static final String TAG = ConfirmEncounterEvent.class.getSimpleName();
    protected static long pkid;
    protected static List<Identifier> sharedSecrets;
    protected static Long confirmationTime;

    public ConfirmEncounterEvent(long pkid, List<Identifier> sharedSecrets, Long confirmationTime) {
        super(pkid, null, null, null, null, null, null, confirmationTime, null, null, null, sharedSecrets);
        this.pkid = pkid;
        this.sharedSecrets = sharedSecrets;
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
        final ContentValues values = toContentValues(context, false);
        final Uri uri = ContentUris.withAppendedId(EncounterHistoryAPM.encounters.getContentURI(), pkid);
        final int updatedRows = context.getContentResolver().update(uri, values, null, null);
        if (updatedRows == 0) {
            Log.d(TAG, "Update returned 0 value");
            Utils.myAssert(false);
        }
        insertSharedSecrets(context);
    }
}
