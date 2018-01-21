package org.mpisws.sddrservice.encounterhistory;

import org.mpisws.sddrservice.dbplatform.AggregatePersistenceModel;
import org.mpisws.sddrservice.dbplatform.PersistenceModel;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class EncounterHistoryAPM extends AggregatePersistenceModel {

    private static final EncounterHistoryAPM instance = new EncounterHistoryAPM();
    public static final PEncounters encounters = new PEncounters();
    public static final PDiscoveryEvents discoveryEvents = new PDiscoveryEvents();
    public static final PSharedSecrets sharedSecrets = new PSharedSecrets();
    public static final PMyAdverts myAdverts = new PMyAdverts();
    public static final PNewAdverts newAdverts = new PNewAdverts();
    public static final PLocation locations = new PLocation();
    public static final PBlooms blooms = new PBlooms();
    public static final List<PersistenceModel> models = new LinkedList<PersistenceModel>();

    static {
        models.add(encounters);
        models.add(discoveryEvents);
        models.add(sharedSecrets);
        models.add(locations);
        models.add(blooms);
        models.add(myAdverts);
        models.add(newAdverts);
    }

    private EncounterHistoryAPM() {
    }

    public String getContentProviderAuthority() {
        return "org.mpisws.sddrservice.encounterhistory";
    }

    public List<PersistenceModel> getPersistenceModels() {
        return Collections.unmodifiableList(models);
    }

    public static EncounterHistoryAPM getInstance() {
        return instance;
    }
}
