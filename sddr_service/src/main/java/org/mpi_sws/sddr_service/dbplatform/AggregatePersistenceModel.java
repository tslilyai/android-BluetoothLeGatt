package org.mpi_sws.sddr_service.dbplatform;

import android.net.Uri;

import java.util.List;

/**
 *
 * @author verdelyi
 */
public abstract class AggregatePersistenceModel {

    public abstract String getContentProviderAuthority();

    public abstract List<PersistenceModel> getPersistenceModels();
    
    public Uri getRootContentURI() {
        return Uri.parse("content://" + getContentProviderAuthority());
    }

}
