package org.mpisws.sddrservice.dbplatform;

public class MatchType {

    private PersistenceModel model;
    private boolean row;

    public MatchType(PersistenceModel model, boolean row) {
        this.model = model;
        this.row = row;
    }

    public PersistenceModel getModel() {
        return model;
    }

    public boolean isRow() {
        return row;
    }
}
