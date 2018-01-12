package org.mpisws.sddrservice.encounterhistory;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import org.mpisws.sddrservice.lib.Identifier;

import java.util.ArrayList;
import java.util.List;

public class SSBridge {

    private final Context context;

    public SSBridge(final Context context) {
        this.context = context;
    }
    
    public String getSharedSecretByEncounterID(final String encounterID) {
        byte[] result = null;
        final Cursor cursor = context.getContentResolver().query(EncounterHistoryAPM.sharedSecrets.getContentURI(), null,
                "HEX(" + PSharedSecrets.Columns.encounterID + ") = ?", new String[] { encounterID }, null);
        if (cursor.moveToNext()) {
            result = EncounterHistoryAPM.sharedSecrets.extractSharedSecret(cursor);
            Log.v("SSBridge", "Found secret " + result == null ? "" : new String(result));
            Log.v("SSBridge", "Found timestamp " + EncounterHistoryAPM.sharedSecrets.extractTimestamp(cursor));
        }
        cursor.close();
        if (result == null) {
            return null;
        }
        return new String(result);
    }

    public long getEncounterPKIDByEncounterID(final Identifier encounterID) {
        return getEncounterPKIDByEncounterID(encounterID.toString());
    }
    
    public long getEncounterPKIDByEncounterID(final String encounterID) {
        Long result = null;
        final Cursor cursor = context.getContentResolver().query(EncounterHistoryAPM.sharedSecrets.getContentURI(), null,
                "HEX(" + PSharedSecrets.Columns.encounterID + ") = ?", new String[] { encounterID }, null);
        if (cursor.moveToNext()) {
            result = EncounterHistoryAPM.sharedSecrets.extractEncounterPKID(cursor);
        }
        cursor.close();
        if (result == null) {
            return -1;
        }
        return result;
    }
    
}
