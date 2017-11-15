package org.mpisws.sddrservice.encounterhistory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import org.mpisws.sddrservice.dbplatform.AbstractBridge;
import org.mpisws.sddrservice.dbplatform.PersistenceModel;
import org.mpisws.sddrservice.lib.time.TimeInterval;

import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author verdelyi
 */
public class DiscoveryEventsBridge extends AbstractBridge<MDiscoveryEvent> {

    private static final String TAG = DiscoveryEventsBridge.class.getSimpleName();

    public DiscoveryEventsBridge(final Context context) {
        super(context);
    }

    public List<MDiscoveryEvent> getDiscoveryEventsForEncounter(final TimeInterval timeInterval, final long encounterPKID) {
        final List<MDiscoveryEvent> result = new LinkedList<MDiscoveryEvent>();
        final String[] projection = new String[] { PDiscoveryEvents.Columns.blockStartTime,
                PDiscoveryEvents.Columns.blockEndTime, PDiscoveryEvents.Columns.avgRSSI }; // TODO use this
        final String selection = "(" + PDiscoveryEvents.Columns.blockEndTime + " >= ?)" + " AND " + 
                "(" + PDiscoveryEvents.Columns.blockStartTime + " <= ?)" + " AND " +
                "(" + PDiscoveryEvents.Columns.encounterPKID + " = ?)";
        final String[] selectionArgs = new String[] { String.valueOf(timeInterval.getStartL()),
                String.valueOf(timeInterval.getEndL()), String.valueOf(encounterPKID) };
        final Cursor cursor = context.getContentResolver().query(EncounterHistoryAPM.discoveryEvents.getContentURI(), null, selection,
                selectionArgs, PDiscoveryEvents.Columns.blockStartTime + " ASC");
        while (cursor.moveToNext()) {
            result.add(cursorToItem(cursor));
        }
        cursor.close();
        return result;
    }

    @Override
    protected PersistenceModel getPersistenceModel() {
        return EncounterHistoryAPM.discoveryEvents;
    }

    @Override
    protected ContentValues itemToContentValues(MDiscoveryEvent item) {
        final ContentValues values = new ContentValues();
        values.put(PDiscoveryEvents.Columns.encounterPKID, item.getEncounterPKID());
        values.put(PDiscoveryEvents.Columns.blockStartTime, item.getBlockInterval().getStartL());
        values.put(PDiscoveryEvents.Columns.blockEndTime, item.getBlockInterval().getEndL());
        values.put(PDiscoveryEvents.Columns.avgRSSI, item.getAvgRSSI());
        return values;
    }

    @Override
    protected MDiscoveryEvent cursorToItem(Cursor cursor) {
        final long pkid = EncounterHistoryAPM.discoveryEvents.extractPKID(cursor);
        final long encounterPKID = EncounterHistoryAPM.discoveryEvents.extractEncounterPKID(cursor);
        final TimeInterval blockInterval = EncounterHistoryAPM.discoveryEvents.extractBlockInterval(cursor);
        final float avgRSSI = EncounterHistoryAPM.discoveryEvents.extractAvgRSSI(cursor);
        return new MDiscoveryEvent(pkid, encounterPKID, blockInterval, avgRSSI);
    }
}
