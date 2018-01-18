package org.mpisws.sddrservice.encounterhistory;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.mpisws.sddrservice.dbplatform.AbstractMemoryObject;
import org.mpisws.sddrservice.encounters.SDDR_Native;
import org.mpisws.sddrservice.encounters.SDDR_Proto;
import org.mpisws.sddrservice.lib.Constants;
import org.mpisws.sddrservice.lib.FacebookEventStatus;
import org.mpisws.sddrservice.lib.Identifier;
import org.mpisws.sddrservice.lib.LinkabilityResult;
import org.mpisws.sddrservice.lib.Utils;
import org.mpisws.sddrservice.lib.time.TimeInterval;
import org.mpisws.sddrservice.linkability.MLinkabilityEntry;

import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class MEncounter extends AbstractMemoryObject implements Serializable {
    private static final String TAG = MEncounter.class.getSimpleName();
    private static final long serialVersionUID = 7973198346712274576L;
    private final List<Identifier> commonIDs;
    private final TimeInterval timeInterval;
    private final long lastTimeSeen;
    private final long confirmationTime;
    private final FacebookEventStatus facebookEventStatus;
    private final long conduitID;
    private final Identifier myAdvert;
    private final Identifier myDHPubKey;
    private final Identifier myDHKey;

    public MEncounter(final long pkid, final List<Identifier> commonIDs, final TimeInterval timeInterval,
            final long lastTimeSeen, final long confirmationTime, final FacebookEventStatus facebookEventStatus, final long conduitID,
                      final Identifier myAdvert, final Identifier myDHPubKey, final Identifier myDHKey) {
        super(pkid);
        this.commonIDs = commonIDs;
        this.timeInterval = timeInterval;
        this.lastTimeSeen = lastTimeSeen;
        this.confirmationTime = confirmationTime;
        this.facebookEventStatus = facebookEventStatus;
        this.conduitID = conduitID;
        this.myAdvert = myAdvert;
        this.myDHPubKey = myDHPubKey;
        this.myDHKey = myDHKey;
    }

    public static Identifier convertSharedSecretToEncounterID(final byte[] sharedSecret) {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(sharedSecret, 0, sharedSecret.length);
            return new Identifier(md.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }
    }

    public List<SDDR_Proto.Event.RetroactiveInfo.BloomInfo> getBlooms(final Context context) {
        final List<SDDR_Proto.Event.RetroactiveInfo.BloomInfo> result = new LinkedList<SDDR_Proto.Event.RetroactiveInfo.BloomInfo>();
        final String selection = "(" + PDiscoveryEvents.Columns.encounterPKID + " = ?)";
        final String[] selectionArgs = new String[] { String.valueOf(pkid) };
        final Cursor cursor = context.getContentResolver().query(EncounterHistoryAPM.blooms.getContentURI(), null, selection,
                selectionArgs, PBlooms.Columns.timestamp + " ASC");
        while (cursor.moveToNext()) {
            try {
                result.add(SDDR_Proto.Event.RetroactiveInfo.BloomInfo.parseFrom(EncounterHistoryAPM.blooms.extractBloom(cursor)));
            } catch (InvalidProtocolBufferException e) {
                Log.v(TAG, "Invalid bloom bytes");
                e.printStackTrace();
                return null;
            }
        }
        cursor.close();
        return result;
    }

    public List<Identifier> getEncounterIDs(final Context context) { // Ordered by PKID ASC
        final List<Identifier> encounterIDs = new LinkedList<Identifier>();
        final Cursor cursor = context.getContentResolver().query(EncounterHistoryAPM.sharedSecrets.getContentURI(), null,
                PSharedSecrets.Columns.encounterPKID + " = ?", new String[] { String.valueOf(pkid) },
                PSharedSecrets.Columns.pkid + " ASC");
        while (cursor.moveToNext()) {
            encounterIDs.add(new Identifier(EncounterHistoryAPM.sharedSecrets.extractEncounterID(cursor)));
        }
        cursor.close();
        return encounterIDs;
    }

    public List<Identifier> getAdverts(final Context context) { // Ordered by PKID ASC
        final List<Identifier> adverts = new LinkedList<>();
        final Cursor cursor = context.getContentResolver().query(EncounterHistoryAPM.sharedSecrets.getContentURI(), null,
                PSharedSecrets.Columns.encounterPKID + " = ?", new String[] { String.valueOf(pkid) },
                PSharedSecrets.Columns.pkid + " ASC");
        while (cursor.moveToNext()) {
            adverts.add(new Identifier(EncounterHistoryAPM.sharedSecrets.extractAdvert(cursor)));
        }
        cursor.close();
        return adverts;
    }

    public TimeInterval getTimeInterval() {
        return timeInterval;
    }

    public List<Identifier> getCommonIDs() {
        return commonIDs;
    }

    public boolean isConfirmed() {
        return confirmationTime != -1;
    }

    public Long getConfirmationTime() {
        return confirmationTime;
    }

    public long getLastTimeSeen() {
        return lastTimeSeen;
    }

    public Identifier getMyAdvert() {
        return myAdvert;
    }
    public Identifier getMyDHKey() {
        return myDHKey;
    }
    public Identifier getMyDHPubKey() {
        return myDHPubKey;
    }

    public FacebookEventStatus getFacebookEventStatus() {
        return facebookEventStatus;
    }

    public long getConduitID() {
        return conduitID;
    }

    public LinkabilityResult checkLinkability(final Collection<MLinkabilityEntry> linkabilityEntries) {
        final Set<Integer> stickerIDs = new HashSet<Integer>();
        final Set<String> names = new HashSet<String>();
        boolean isLinkable = false;
        for (Identifier commonID : commonIDs) {
            for (MLinkabilityEntry linkabilityEntry : linkabilityEntries) {
                if (linkabilityEntry.getIdValue().equals(commonID)) {
                    names.add(linkabilityEntry.getPrincipalName());
                    stickerIDs.add(linkabilityEntry.getStickerID());
                    isLinkable = true;
                }
            }
        }
        return new LinkabilityResult(names, stickerIDs, isLinkable);
    }

    public LinkabilityResult checkLinkabilityForSingleEncounter(final Context context, List<MLinkabilityEntry> links) {
        return checkLinkability(links);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("StartTime: ").append(Constants.fullTimeFormat.format(timeInterval.getStart())).append("\n");
        sb.append("EndTime: ").append(Constants.fullTimeFormat.format(timeInterval.getEnd())).append("\n");
        return sb.toString();
    }
}
