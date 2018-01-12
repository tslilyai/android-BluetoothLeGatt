package org.mpisws.sddrservice.encounterhistory;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

public class LocationBridge {

    private final Context context;

    public class LocationStamp {
        double latitude;
        double longitude;
        long timestamp;
        LocationStamp(double latitude, double longitude, long timestamp) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.timestamp = timestamp;
        }
    }

    public LocationBridge(final Context context) {
        this.context = context;
    }
    
    public LocationStamp getLocationByEncounterPKID(final long pkid) {
        LocationStamp result = null;
        final Uri uri = ContentUris.withAppendedId(EncounterHistoryAPM.locations.getContentURI(), pkid);
        final Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        if (cursor.moveToNext()) {
            result = new LocationStamp(
                        EncounterHistoryAPM.locations.extractLatitude(cursor),
                        EncounterHistoryAPM.locations.extractLongitude(cursor),
                        EncounterHistoryAPM.locations.extractTimestamp(cursor));
        }
        cursor.close();
        return result;
    }
}
