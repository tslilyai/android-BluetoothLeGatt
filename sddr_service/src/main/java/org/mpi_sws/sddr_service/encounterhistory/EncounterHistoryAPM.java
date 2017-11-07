package org.mpi_sws.sddr_service.encounterhistory;

import org.mpi_sws.sddr_service.dbplatform.AggregatePersistenceModel;
import org.mpi_sws.sddr_service.dbplatform.PersistenceModel;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class EncounterHistoryAPM extends AggregatePersistenceModel {

    private static final EncounterHistoryAPM instance = new EncounterHistoryAPM();
    public static final PEncounters encounters = new PEncounters();
    public static final PDiscoveryEvents discoveryEvents = new PDiscoveryEvents();
    public static final PSharedSecrets sharedSecrets = new PSharedSecrets();
    public static final PBlooms blooms = new PBlooms();
    public static final List<PersistenceModel> models = new LinkedList<PersistenceModel>();

    static {
        models.add(encounters);
        models.add(discoveryEvents);
        models.add(sharedSecrets);
        models.add(blooms);
    }

    private EncounterHistoryAPM() {
    }

    public String getContentProviderAuthority() {
        return "org.mpi_sws.sddr_service.encounterhistory";
    }

    public List<PersistenceModel> getPersistenceModels() {
        return Collections.unmodifiableList(models);
    }

    public static EncounterHistoryAPM getInstance() {
        return instance;
    }
}
