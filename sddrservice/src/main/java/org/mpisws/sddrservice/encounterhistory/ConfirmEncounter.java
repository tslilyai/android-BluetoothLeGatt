package org.mpisws.sddrservice.encounterhistory;

import android.Manifest;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import org.mpisws.sddrservice.EncountersService;
import org.mpisws.sddrservice.encounters.SDDR_Proto;
import org.mpisws.sddrservice.lib.Constants;
import org.mpisws.sddrservice.lib.FacebookEventStatus;
import org.mpisws.sddrservice.lib.Identifier;
import org.mpisws.sddrservice.lib.Utils;
import org.mpisws.sddrservice.lib.time.TimeInterval;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

public class ConfirmEncounter {
    private static final String TAG = ConfirmEncounter.class.getSimpleName();
    protected static long pkid;
    protected static List<Identifier> sharedSecrets;
    protected static Long confirmationTime;

    public static void confirmWithSharedSecrets(Context context, long pkid, List<Identifier> sharedSecrets, Long confirmationTime) {
        pkid = pkid;
        sharedSecrets = sharedSecrets;
        confirmationTime = confirmationTime;
        persistIntoDatabase(context);
        insertSharedSecrets(context);
    }

    private static void persistIntoDatabase(Context context) {
        final ContentValues values = toContentValues(context, false);
        final Uri uri = ContentUris.withAppendedId(EncounterHistoryAPM.encounters.getContentURI(), pkid);
        final int updatedRows = context.getContentResolver().update(uri, values, null, null);
        if (updatedRows != 1) {
            throw new RuntimeException("Update returned non-1 value: " + updatedRows);
        }
    }

    private static ContentValues toContentValues(final Context context, final boolean putPKID) {
        final ContentValues values = new ContentValues();
        if (putPKID) {
            values.put(PEncounters.Columns.pkid, pkid);
        }
        if (confirmationTime != null) {
            values.put(PEncounters.Columns.confirmationTime, confirmationTime);
        }
        values.put(PEncounters.Columns.conduitID, -1);
        return values;
    }

    private static void insertSharedSecrets(final Context context) {
        if (sharedSecrets != null) {
            insertSharedSecrets(context, sharedSecrets);
        }
    }

    private static void insertSharedSecrets(final Context context, final List<Identifier> sharedSecrets) {
        Log.v(TAG, "Inserting shared secrets " + sharedSecrets.size());
        for (Identifier sharedSecret : sharedSecrets) {
            Identifier eid = MEncounter.convertSharedSecretToEncounterID(sharedSecret);
            final ContentValues values = new ContentValues();
            values.put(PSharedSecrets.Columns.encounterPKID, pkid);
            values.put(PSharedSecrets.Columns.sharedSecret, sharedSecret.getBytes());
            values.put(PSharedSecrets.Columns.encounterID, eid.getBytes());
            values.put(PSharedSecrets.Columns.timestamp, System.currentTimeMillis());
            context.getContentResolver().insert(EncounterHistoryAPM.sharedSecrets.getContentURI(), values);

            // this creates topics that may not be used (since an encounter only communicates over its first encounterID)
            Log.v(TAG, "Create topic for " + eid.toString());
            EncountersService.getInstance().createEncounterMsgingChannel(eid.toString());
        }
    }
}
