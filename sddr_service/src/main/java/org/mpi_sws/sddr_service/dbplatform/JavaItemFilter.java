package org.mpi_sws.sddr_service.dbplatform;

public interface JavaItemFilter<T> {
    public boolean isNeeded(T item);
}
