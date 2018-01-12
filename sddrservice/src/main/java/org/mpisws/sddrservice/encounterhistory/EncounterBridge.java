package org.mpisws.sddrservice.encounterhistory;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.location.Location;
import android.util.Log;

import org.mpisws.sddrservice.EncountersService;
import org.mpisws.sddrservice.dbplatform.AbstractBridge;
import org.mpisws.sddrservice.dbplatform.JavaItemFilter;
import org.mpisws.sddrservice.dbplatform.PersistenceModel;
import org.mpisws.sddrservice.lib.Constants;
import org.mpisws.sddrservice.lib.FacebookEventStatus;
import org.mpisws.sddrservice.lib.Identifier;
import org.mpisws.sddrservice.lib.time.TimeConversion;
import org.mpisws.sddrservice.lib.time.TimeInterval;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * 
 * @author verdelyi
 */
public class EncounterBridge extends AbstractBridge<MEncounter> {

    private final static String TAG = EncounterBridge.class.getSimpleName();
    private final ContentResolver contentResolver;

    public static class Methods {
        public static class FinalizeAbandonedEncounters {
            public static final String name = "finalizeAbandonedEncounters";
        }
        public static class GetAvgRSSIForEncounter {
            public static final String name = "getAvgRSSIForEncounter";
            public static class ParameterKeys {
                public static final String encounterPKID = "encounterPKID"; 
            }
            public static class ResultKeys {
                public static final String avgRSSI = "avgRSSI";
            }
        }
    }

    public EncounterBridge(final Context context) {
        super(context);
        this.contentResolver = context.getContentResolver();
    }

    private void checkSanity(final long start, final long lastseen, final long end) {
        boolean ok = true;
        if (end == -1 && start <= 0) { // Ongoing
            ok = false;
        } else if ((end != -1)
                && (end + 1000 < start || end + 1000 < lastseen || lastseen + 1000 < start || lastseen <= 0 || start <= 0 || end <= 0)) { // Ended
            ok = false;
        }
        if (!ok) {
            final String error = "Invalid encounter: " + start + " - " + lastseen + " - " + end;
            Log.w(TAG, error);
            //throw new InvalidEncounterException(error);
        }
    }

    @Override
    public MEncounter cursorToItem(Cursor cursor) {
        final long startTime = PEncounters.extractStartTime(cursor);
        final long lastSeen = PEncounters.extractLastTimeSeen(cursor);
        long endTime = PEncounters.extractEndTime(cursor);
        checkSanity(startTime, lastSeen, endTime);
        // If it's ongoing, pretend that it ends at the current time
        if (endTime == -1) {
            endTime = System.currentTimeMillis();
        }
        // If it's too short
        if (endTime - startTime < TimeConversion.min2ms(2)) {
            endTime = startTime + TimeConversion.min2ms(2);
        }
        final TimeInterval encounterTimeInterval = new TimeInterval(startTime, endTime);
        final long pkID = EncounterHistoryAPM.encounters.extractPKID(cursor);

        final byte[] commonIDs = PEncounters.extractCommonIDs(cursor);
        final List<Identifier> commonIDsList = new LinkedList<Identifier>();
        if (commonIDs != null) {
            for (int i = 0; i < commonIDs.length / Constants.HANDSHAKE_DH_SIZE; i++) {
                final byte[] b = new byte[Constants.HANDSHAKE_DH_SIZE];
                for (int j = 0; j < Constants.HANDSHAKE_DH_SIZE; j++) {
                    b[j] = commonIDs[i * Constants.HANDSHAKE_DH_SIZE + j];
                }
                commonIDsList.add(new Identifier(b));
            }
        }
        
        final long confirmationTime = PEncounters.extractConfirmationTime(cursor);
        final FacebookEventStatus facebookEventStatus = PEncounters.extractFacebookEventStatus(cursor);
        final long conduitID = PEncounters.extractConduitID(cursor);

        return new MEncounter(pkID, commonIDsList, encounterTimeInterval,
                lastSeen, confirmationTime, facebookEventStatus,
                conduitID);
    }
    /**
     * Gets all encounters that overlap with the requested filtered results. Not efficient since it's filtering the results after
     * retrieving them.
     */
    public List<MEncounter> getEncountersFiltered(final EncountersService.Filter filter) {
        if (filter == null) {
            return getAllItems();
        }
        final JavaItemFilter<MEncounter> dbfilter = encounter -> {
            boolean isNeeded = true;

            if (filter.getTimeInterval() != null) {
                isNeeded &= encounter.getTimeInterval().overlapsWith(filter.getTimeInterval());
            }

            if (filter.getLatitude() != -1 && filter.getLongitude() != -1) {
                List<LocationBridge.LocationStamp> locationStamps = new ArrayList<>();
                List<Identifier> eids = encounter.getEncounterIDs(context);
                SSBridge bridge = new SSBridge(context);
                LocationBridge locbridge = new LocationBridge(context);
                float[] results = new float[1];
                for (Identifier eid : eids) {
                    long pkid = bridge.getEncounterPKIDByEncounterID(eid);
                    LocationBridge.LocationStamp locationStamp = locbridge.getLocationByEncounterPKID(pkid);
                    Location.distanceBetween(
                            locationStamp.latitude, locationStamp.longitude,
                            filter.getLatitude(), filter.getLongitude(),
                            results);
                    if (results[0] <= filter.getRadius()) {
                        locationStamps.add(locationStamp);
                    }
                }
                long lengthOverlap = locationStamps.isEmpty() ? 0 :
                        locationStamps.get(locationStamps.size() - 1).timestamp - locationStamps.get(0).timestamp;
                isNeeded &= lengthOverlap >= filter.getOverlapTime();
            }

            if (filter.getMatches() != null) {
                isNeeded &= encounter.getCommonIDs().containsAll(filter.getMatches());
            }
            return isNeeded;
        };
        return getFilteredItems(dbfilter);
    }

    /**
     * Gets all encounters that overlap with the requested time interval. Not efficient since it's filtering the results after
     * retrieving them.
     */
    public List<MEncounter> getEncountersByTime(final TimeInterval requestedTimeInterval) {
        final JavaItemFilter<MEncounter> filter = new JavaItemFilter<MEncounter>() {

            @Override
            public boolean isNeeded(MEncounter encounter) {
                return encounter.getTimeInterval().overlapsWith(requestedTimeInterval);
            }

        };
        return getFilteredItems(filter);
    }

    public void finalizeAbandonedEncounters() {
        Log.v(TAG, "FinalizeAbandonedEncounters starting");
        contentResolver.call(EncounterHistoryAPM.encounters.getContentURI(), Methods.FinalizeAbandonedEncounters.name, null, null);
        Log.v(TAG, "FinalizeAbandonedEncounters done");
    }

    public void addNewConduit(long encPkid, long evPkid, FacebookEventStatus state) {
        updateLongColumn(encPkid, PEncounters.Columns.conduitID, evPkid);
        updateLongColumn(encPkid, PEncounters.Columns.facebookEventStatus, (long) state.toInt());
    }

    public void removeConduit(long encPkid) {
        updateLongColumn(encPkid, PEncounters.Columns.conduitID, (long) -1);
        updateLongColumn(encPkid, PEncounters.Columns.facebookEventStatus, (long) FacebookEventStatus.NonExistent.toInt());
    }

    public void updateConduitStatus(long encPkid, FacebookEventStatus status) {
        updateLongColumn(encPkid, PEncounters.Columns.facebookEventStatus, (long) status.toInt());
    }

    public void updateConduitID(long encPkid, long apkid) {
        updateLongColumn(encPkid, PEncounters.Columns.conduitID, apkid);
    }

    @Override
    protected PersistenceModel getPersistenceModel() {
        return EncounterHistoryAPM.encounters;
    }

    @Override
    protected ContentValues itemToContentValues(MEncounter item) {
        throw new UnsupportedOperationException();
    }
}
