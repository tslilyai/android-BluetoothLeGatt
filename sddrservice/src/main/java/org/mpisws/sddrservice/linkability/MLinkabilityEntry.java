package org.mpisws.sddrservice.linkability;

import org.mpisws.sddrservice.dbplatform.AbstractMemoryObject;
import org.mpisws.sddrservice.lib.Identifier;

public class MLinkabilityEntry extends AbstractMemoryObject {

    private final Identifier idValue;
    private final String principalName;
    private final LinkabilityEntryMode mode;
    private final int stickerID;

    public MLinkabilityEntry(Long pkid, Identifier idValue, String principalName, LinkabilityEntryMode mode, int stickerID) {
        super(pkid);
        this.idValue = idValue;
        this.principalName = principalName;
        this.mode = mode;
        this.stickerID = stickerID;
    }

    public Identifier getIdValue() {
        return idValue;
    }

    public String getPrincipalName() {
        return principalName;
    }

    public LinkabilityEntryMode getMode() {
        return mode;
    }

    public int getStickerID() {
        return stickerID;
    }
}
