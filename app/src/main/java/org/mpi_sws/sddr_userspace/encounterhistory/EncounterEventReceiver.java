package org.mpi_sws.sddr_userspace.encounterhistory;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.mpi_sws.sddr_userspace.lib.Utils;

public class EncounterEventReceiver extends BroadcastReceiver {
    private final static String TAG = EncounterEventReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Intent received: " + intent.getAction());
        if (!intent.hasExtra("encounterEvent")) {
            Log.w(TAG, "!!!!!!!!!!!!!!!!!!!!!!! Ignoring invalid intent !!!!!!!!!!!!!!!!!!!!!!!!!");
            Utils.printIntent(TAG, intent);
            return;
        }
        final EncounterEvent encounterEvent = (EncounterEvent) intent.getSerializableExtra("encounterEvent");
        encounterEvent.persistIntoDatabase(context);
        Log.d(TAG, "Onreceive done");
    }
}