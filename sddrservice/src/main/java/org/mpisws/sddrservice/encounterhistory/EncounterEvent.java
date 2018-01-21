package org.mpisws.sddrservice.encounterhistory;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import org.mpisws.sddrservice.EncountersService;
import org.mpisws.sddrservice.lib.FacebookEventStatus;
import org.mpisws.sddrservice.lib.Identifier;
import org.mpisws.sddrservice.lib.time.TimeInterval;

import java.io.Serializable;
import java.util.List;

public abstract class EncounterEvent implements Serializable {
    private static final String TAG = EncounterEvent.class.getSimpleName();
    private static final long serialVersionUID = 4915344112998741643L;
    protected final long pkid;
    protected final Long startTime;
    protected final Long lastTimeSeen;
    protected final Long endTime;
    protected final List<Identifier> adverts;
    protected final List<Identifier> sharedSecrets;
    protected final List<RSSIEntry> newRSSIEntries;
    protected final String currentWirelessAddress;
    protected final Long confirmationTime;
    private final Identifier myAdvert;
    private final Identifier myDHPubKey;
    private final Identifier myDHKey;

    public EncounterEvent(long pkid, Long startTime, Long lastTimeSeen, Long endTime, List<Identifier> adverts, List<RSSIEntry> newRSSIEntries,
                          String currentWirelessAddress, Long confirmationTime,
                          Identifier myAdvert, Identifier myDHPubKey, Identifier myDHKey, List<Identifier> secrets) {
        this.pkid = pkid;
        this.startTime = startTime;
        this.lastTimeSeen = lastTimeSeen;
        this.endTime = endTime;
        this.adverts = adverts;
        this.newRSSIEntries = newRSSIEntries;
        this.currentWirelessAddress = currentWirelessAddress;
        this.sharedSecrets = secrets;
        this.confirmationTime = confirmationTime;
        this.myAdvert = myAdvert;
        this.myDHPubKey = myDHPubKey;
        this.myDHKey = myDHKey;
    }

    public abstract void broadcast(final Context context);

    public abstract void persistIntoDatabase(Context context);

    protected ContentValues toContentValues(final Context context, final boolean putPKID) {
        Log.v(TAG, "\tPKID: " + pkid + "\n\tstartTime: " + startTime + "\n\tlastTimeSeen: " + lastTimeSeen
                + "\n\tendTime: " + endTime);
        final ContentValues values = new ContentValues();
        if (putPKID) {
            values.put(PEncounters.Columns.pkid, pkid);
        }
        if (startTime != null) {
            values.put(PEncounters.Columns.startTime, startTime);
        }
        if (lastTimeSeen != null) {
            values.put(PEncounters.Columns.lastTimeSeen, lastTimeSeen);
        }
        if (endTime != null) {
            values.put(PEncounters.Columns.endTime, endTime);
        }
        if (currentWirelessAddress != null) {
            values.put(PEncounters.Columns.currentWirelessAddress, currentWirelessAddress);
        }
        if (confirmationTime != null) {
            values.put(PEncounters.Columns.confirmationTime, confirmationTime);
        }
        if (myAdvert != null) {
            values.put(PEncounters.Columns.myAdvert, myAdvert.getBytes());
        }
        if (myDHKey!= null) {
            values.put(PEncounters.Columns.myDHKey, myDHKey.getBytes());
        }
        if (myDHPubKey!= null) {
            values.put(PEncounters.Columns.myDHPubKey, myDHPubKey.getBytes());
        }

        values.put(PEncounters.Columns.facebookEventStatus, FacebookEventStatus.NonExistent.toInt());
        values.put(PEncounters.Columns.conduitID, -1);
        return values;
    }

    protected void insertLocationRSSIandAdverts(final Context context) {
        if (newRSSIEntries != null) {
            insertRSSIEntries(context, newRSSIEntries);
        }
        insertLocation(context);
        insertAdverts(context);
    }

    private void insertRSSIEntries(final Context context, final List<RSSIEntry> rssiEntries) {
        if (rssiEntries.isEmpty()) {
            return;
        }
        float sumRSSI = 0;
        long minTimestamp = Long.MAX_VALUE, maxTimestamp = Long.MIN_VALUE;
        for (RSSIEntry rssiEntry : rssiEntries) {
            if (rssiEntry.getTimestamp() > maxTimestamp) {
                maxTimestamp = rssiEntry.getTimestamp();
            }
            if (rssiEntry.getTimestamp() < minTimestamp) {
                minTimestamp = rssiEntry.getTimestamp();
            }
            sumRSSI += rssiEntry.getRssi();
        }
        new DiscoveryEventsBridge(context).addItem(new MDiscoveryEvent(null, pkid, new TimeInterval(minTimestamp, maxTimestamp),
                sumRSSI / (float) rssiEntries.size()));
    }

    private void insertLocation(final Context context) {
        Log.v(TAG, "Inserting location");
        FusedLocationProviderClient mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mFusedLocationClient.getLastLocation()
                .addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        // Got last known location. In some rare situations this can be null.
                        if (location != null) {
                            double lat = location.getLatitude();
                            double longi = location.getLongitude();
                            Log.v(TAG, "Updated location with lat " + lat + " and long " + longi);
                            final ContentValues values = new ContentValues();
                            values.put(PLocation.Columns.encounterPKID, pkid);
                            values.put(PLocation.Columns.latitude, lat);
                            values.put(PLocation.Columns.longitude, longi);
                            values.put(PLocation.Columns.timestamp, System.currentTimeMillis());
                            context.getContentResolver().insert(EncounterHistoryAPM.locations.getContentURI(), values);
                        } else {
                            Log.v(TAG, "Location null");
                        }
                    }
                });
    }

    private void insertAdverts(final Context context) {
        if (adverts != null) {
            insertAdverts(context, adverts);
        }
    }

    private void insertAdverts(final Context context, final List<Identifier> adverts) {
        Log.v(TAG, "Inserting adverts " + adverts.size());
        for (Identifier advert: adverts) {
            final ContentValues values = new ContentValues();
            values.put(PSharedSecrets.Columns.encounterPKID, pkid);
            values.put(PSharedSecrets.Columns.advert, advert.getBytes());
            values.put(PSharedSecrets.Columns.timestamp, System.currentTimeMillis());
            context.getContentResolver().insert(EncounterHistoryAPM.sharedSecrets.getContentURI(), values);
        }
    }

    protected void insertSharedSecrets(final Context context) {
        if (sharedSecrets != null) {
            insertSharedSecrets(context, sharedSecrets);
        }
    }

    private void insertSharedSecrets(final Context context, final List<Identifier> sharedSecrets) {
        Log.v(TAG, "Inserting shared secrets " + sharedSecrets.size());
        for (Identifier sharedSecret : sharedSecrets) {
            if (sharedSecret.getBytes() != null) {
                Identifier eid = MEncounter.convertSharedSecretToEncounterID(sharedSecret.getBytes());
                final ContentValues values = new ContentValues();
                values.put(PSharedSecrets.Columns.encounterPKID, pkid);
                values.put(PSharedSecrets.Columns.sharedSecret, sharedSecret.getBytes());
                values.put(PSharedSecrets.Columns.encounterID, eid.getBytes());
                values.put(PSharedSecrets.Columns.timestamp, System.currentTimeMillis());
                context.getContentResolver().update(EncounterHistoryAPM.sharedSecrets.getContentURI(), values,
                    PSharedSecrets.Columns.encounterPKID + " = ?", new String[]{String.valueOf(pkid)});

                // this creates topics that may not be used (since an encounter only communicates over its first encounterID)
                Log.v(TAG, "Create topic for " + eid.toString());
                EncountersService.getInstance().createEncounterMsgingChannel(eid.toString());
            }
        }
    }
}
