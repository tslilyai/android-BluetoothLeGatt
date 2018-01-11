package org.mpisws.sddrservice.linkability;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import org.mpisws.sddrservice.dbplatform.AbstractBridge;
import org.mpisws.sddrservice.dbplatform.PersistenceModel;
import org.mpisws.sddrservice.lib.Identifier;
import org.mpisws.sddrservice.lib.NotFoundException;
import org.mpisws.sddrservice.lib.Utils;

public class LinkabilityBridge extends AbstractBridge<MLinkabilityEntry> {

    private final static String TAG = Utils.getTAG(LinkabilityBridge.class);
    private final ContentResolver contentResolver;

    public LinkabilityBridge(final Context context) {
        super(context);
        this.contentResolver = context.getContentResolver();
    }

    // =====================================================================================
    //  Update
    // =====================================================================================
    public void setPrincipalName(final long entryPKID, final String principalName) {
        final ContentValues values = new ContentValues();
        values.put(PLinkabilityEntries.Columns.principalName, principalName);
        updateFromContentValues(entryPKID, values);
        Log.v(TAG, "Updated linkability entry #" + entryPKID + " with new name " + principalName);
    }

    public void setMode(final long entryPKID, final LinkabilityEntryMode mode) {
        final ContentValues values = new ContentValues();
        values.put(PLinkabilityEntries.Columns.mode, mode.toInt());
        updateFromContentValues(entryPKID, values);
    }

    // =====================================================================================
    //  Get
    // =====================================================================================

    public long getPKIDForIDValue(final Identifier idValue) throws NotFoundException {
        final Cursor cursor = contentResolver.query(LinkabilityAPM.linkabilityEntries.getContentURI(), null, "hex("
                + PLinkabilityEntries.Columns.idValue + ")= ?", new String[] { idValue.toString() }, null);
        if (cursor.moveToNext()) {
            final long pkid = LinkabilityAPM.linkabilityEntries.extractPKID(cursor);
            cursor.close();
            return pkid;
        } else {
            cursor.close();
            throw new NotFoundException();
        }
    }

    @Override
    protected PersistenceModel getPersistenceModel() {
        return LinkabilityAPM.linkabilityEntries;
    }

    @Override
    protected ContentValues itemToContentValues(MLinkabilityEntry item) {
        final ContentValues values = new ContentValues();
        values.put(PLinkabilityEntries.Columns.idValue, item.getIdValue().getBytes());
        values.put(PLinkabilityEntries.Columns.principalName, item.getPrincipalName());
        values.put(PLinkabilityEntries.Columns.mode, item.getMode().toInt());
        values.put(PLinkabilityEntries.Columns.stickerID, item.getStickerID());
        return values;
    }

    @Override
    public MLinkabilityEntry cursorToItem(Cursor cursor) {
        final long pkid = LinkabilityAPM.linkabilityEntries.extractPKID(cursor);
        final Identifier idvalue = LinkabilityAPM.linkabilityEntries.extractIDValue(cursor);
        final String principalName = LinkabilityAPM.linkabilityEntries.extractPrincipal(cursor);
        final LinkabilityEntryMode mode = LinkabilityAPM.linkabilityEntries.extractMode(cursor);
        final int stickerID = LinkabilityAPM.linkabilityEntries.extractStickerID(cursor);
        return new MLinkabilityEntry(pkid, idvalue, principalName, mode, stickerID);
    }
}
