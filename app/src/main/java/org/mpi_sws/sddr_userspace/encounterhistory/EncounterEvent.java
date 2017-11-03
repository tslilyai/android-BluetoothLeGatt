package org.mpi_sws.sddr_userspace.encounterhistory;

import android.content.ContentValues;
import android.content.Context;
import android.util.Log;

import org.mpi_sws.sddr_userspace.SDDR_Proto;
import org.mpi_sws.sddr_userspace.lib.Constants;
import org.mpi_sws.sddr_userspace.lib.FacebookEventStatus;
import org.mpi_sws.sddr_userspace.lib.Identifier;
import org.mpi_sws.sddr_userspace.lib.Utils;
import org.mpi_sws.sddr_userspace.lib.time.TimeInterval;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

public abstract class EncounterEvent implements Serializable {
    private static final String TAG = EncounterEvent.class.getSimpleName();
    private static final long serialVersionUID = 4915344112998741643L;
    protected final long pkid;
    protected final Long startTime;
    protected final Long lastTimeSeen;
    protected final Long endTime;
    protected final List<RSSIEntry> newRSSIEntries;
    protected final List<Identifier> sharedSecrets;
    protected final List<SDDR_Proto.Event.RetroactiveInfo.BloomInfo> blooms;
    protected final List<Identifier> commonIDs;
    protected final String currentWirelessAddress;
    protected final Long confirmationTime;

    public EncounterEvent(long pkid, Long startTime, Long lastTimeSeen, Long endTime, List<RSSIEntry> newRSSIEntries,
                          List<Identifier> sharedSecrets, List<SDDR_Proto.Event.RetroactiveInfo.BloomInfo> blooms, List<Identifier> commonIDs, String currentWirelessAddress, Long confirmationTime) {
        this.pkid = pkid;
        this.startTime = startTime;
        this.lastTimeSeen = lastTimeSeen;
        this.endTime = endTime;
        this.newRSSIEntries = newRSSIEntries;
        this.sharedSecrets = sharedSecrets;
        this.blooms = blooms;
        this.commonIDs = commonIDs;
        this.currentWirelessAddress = currentWirelessAddress;
        this.confirmationTime = confirmationTime;
    }

    public abstract void broadcast(final Context context);

    public abstract void persistIntoDatabase(Context context);

    protected ContentValues toContentValues(final Context context, final boolean putPKID) {
        Log.d(TAG, "\tPKID: " + pkid + "\n\tstartTime: " + startTime + "\n\tlastTimeSeen: " + lastTimeSeen
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
        if (commonIDs != null) { // null means not updated (empty means updated to empty)
            values.put(PEncounters.Columns.commonIDs, identifierListToByteArray(commonIDs));
        }
        if (confirmationTime != null) {
            values.put(PEncounters.Columns.confirmationTime, confirmationTime);
        }
        values.put(PEncounters.Columns.facebookEventStatus, FacebookEventStatus.NonExistent.toInt());
        values.put(PEncounters.Columns.conduitID, -1);
        return values;
    }

    protected void insertSharedSecretsAndRSSIEntriesAndBlooms(final Context context) {
        if (newRSSIEntries != null) {
            insertRSSIEntries(context, newRSSIEntries);
        }
        if (sharedSecrets != null) {
            insertSharedSecrets(context, sharedSecrets);
        }
        if (blooms != null) {
            insertBloomFilters(context, blooms);
        }
    }

    // TODO this means that all common identifiers must be of size Constants.HANDSHAKE_DH_SIZE
    protected byte[] identifierListToByteArray(final Collection<Identifier> list) {
        final byte[] sharedSecretsConcatenated = new byte[list.size() * Constants.HANDSHAKE_DH_SIZE];
        int pos = 0;
        for (Identifier identifier : list) {
            byte[] bytes = identifier.getBytes();
            for (int i = 0; i < Constants.HANDSHAKE_DH_SIZE; ++i) {
                if (i >= bytes.length) {
                    sharedSecretsConcatenated[pos+i] = 0;
                } else {
                    sharedSecretsConcatenated[pos + i] = bytes[i];
                }
            }
            pos+=Constants.HANDSHAKE_DH_SIZE;
        }
        Utils.myAssert(pos == sharedSecretsConcatenated.length);
        return sharedSecretsConcatenated;
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

    private void insertSharedSecrets(final Context context, final List<Identifier> sharedSecrets) {
        for (Identifier sharedSecret : sharedSecrets) {
            final ContentValues values = new ContentValues();
            values.put(PSharedSecrets.Columns.encounterPKID, pkid);
            values.put(PSharedSecrets.Columns.sharedSecret, sharedSecret.getBytes());
            values.put(PSharedSecrets.Columns.encounterID, MEncounter.convertSharedSecretToEncounterID(sharedSecret).getBytes());
            values.put(PSharedSecrets.Columns.timestamp, System.currentTimeMillis());
            context.getContentResolver().insert(EncounterHistoryAPM.sharedSecrets.getContentURI(), values);
        }
    }

    private void insertBloomFilters(final Context context, final List<SDDR_Proto.Event.RetroactiveInfo.BloomInfo> blooms) {
        Log.d(TAG, "BLOOMS: Inserting " + blooms.size() + " blooms for encounterPKID " + pkid);
        for (SDDR_Proto.Event.RetroactiveInfo.BloomInfo bloom : blooms) {
            Log.d(TAG, "BLOOMS: Insert Bloom Prefix Size " + bloom.getPrefixSize() + " Prefix: " + bloom.getPrefixBytes().toString());
            final ContentValues values = new ContentValues();
            values.put(PBlooms.Columns.encounterPKID, pkid);
            values.put(PBlooms.Columns.bloom, bloom.toByteArray());
            values.put(PBlooms.Columns.timestamp, System.currentTimeMillis());
            context.getContentResolver().insert(EncounterHistoryAPM.blooms.getContentURI(), values);
        }
    }
}
