package org.mpisws.sddrservice.linkability;

import org.mpisws.sddrservice.dbplatform.AggregatePersistenceModel;
import org.mpisws.sddrservice.dbplatform.PersistenceModel;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * @author verdelyi
 */
public class LinkabilityAPM extends AggregatePersistenceModel {

    public static final PLinkabilityEntries linkabilityEntries = new PLinkabilityEntries();
    public static final List<PersistenceModel> models = new LinkedList<PersistenceModel>();
    private static final LinkabilityAPM instance = new LinkabilityAPM();

    static {
        models.add(linkabilityEntries);
    }

    private LinkabilityAPM() {
    }

    public String getContentProviderAuthority() {
        return "org.mpisws.sddrservice.linkability";
    }

    public List<PersistenceModel> getPersistenceModels() {
        return Collections.unmodifiableList(models);
    }

    public static LinkabilityAPM getInstance() {
        return instance;
    }
}
