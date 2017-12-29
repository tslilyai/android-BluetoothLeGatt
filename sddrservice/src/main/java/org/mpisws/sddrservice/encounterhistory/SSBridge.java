package org.mpisws.sddrservice.encounterhistory;

import android.content.Context;
import android.database.Cursor;

import org.mpisws.sddrservice.lib.Identifier;

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
