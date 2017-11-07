package org.mpi_sws.sddr_service.encounterhistory;

import android.content.Context;
import android.database.Cursor;

import org.mpi_sws.sddr_service.lib.Identifier;

public class SSBridge {

    private final Context context;

    public SSBridge(final Context context) {
        this.context = context;
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
