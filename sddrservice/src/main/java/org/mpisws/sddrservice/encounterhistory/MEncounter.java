package org.mpisws.sddrservice.encounterhistory;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

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

    public MEncounter(final long pkid, final List<Identifier> commonIDs, final TimeInterval timeInterval,
            final long lastTimeSeen, final long confirmationTime, final FacebookEventStatus facebookEventStatus, final long conduitID) {
        super(pkid);
        this.commonIDs = commonIDs;
        this.timeInterval = timeInterval;
        this.lastTimeSeen = lastTimeSeen;
        this.confirmationTime = confirmationTime;
        this.facebookEventStatus = facebookEventStatus;
        this.conduitID = conduitID;
    }

    public static Identifier convertSharedSecretToEncounterID(final Identifier sharedSecret) {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(sharedSecret.getBytes(), 0, sharedSecret.getBytes().length);
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

    public void updateEncounterMatchings(final Context context) {
        List<SDDR_Proto.Event.RetroactiveInfo.BloomInfo> blooms = getBlooms(context);
        if (blooms.size() == 0) {
            return;
        }
        final SDDR_Proto.Event eventsend = SDDR_Proto.Event.newBuilder()
                .setRetroactiveInfo(SDDR_Proto.Event.RetroactiveInfo.newBuilder()
                        .addAllBlooms(blooms).build()).build();
        Log.v(TAG, "BLOOMS: Retroactive for pkid " + pkid);
        byte[] eventBytes = SDDR_Native.c_getRetroactiveMatches(eventsend.toByteArray());
        if (eventBytes == null) {
            Log.v(TAG, "BLOOMS: No retroactive matches of signficance");
            return;
        }

        final SDDR_Proto.Event eventrecv;
        try {
            eventrecv = SDDR_Proto.Event.parseFrom(eventBytes);
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
            return;
        }
        Utils.myAssert(eventrecv.hasRetroactiveInfo());
        final SDDR_Proto.Event.RetroactiveInfo info = eventrecv.getRetroactiveInfo();
        List<ByteString> matchingSetPB = info.getMatchingSetList();
        if (matchingSetPB.size() > 0) {
            List<Identifier> matchingSet;
            matchingSet = new LinkedList<>();
            for (com.google.protobuf.ByteString matching : matchingSetPB) {
                matchingSet.add(new Identifier(matching.toByteArray()));
            }
            Log.v(TAG, "BLOOMS: Found " + matchingSet.size() + " retroactive matches with Encounter pkid " + pkid);
            Log.v(TAG, "BLOOMS: Previous " + getCommonIDs().size() + " non-retroactive matches with Encounter pkid " + pkid);

            EncounterRetroactiveEvent revent = new EncounterRetroactiveEvent(pkid, matchingSet);
            revent.broadcast(context);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("StartTime: ").append(Constants.fullTimeFormat.format(timeInterval.getStart())).append("\n");
        sb.append("EndTime: ").append(Constants.fullTimeFormat.format(timeInterval.getEnd())).append("\n");
        return sb.toString();
    }
}
