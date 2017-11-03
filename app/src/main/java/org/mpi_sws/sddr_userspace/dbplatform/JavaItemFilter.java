package org.mpi_sws.sddr_userspace.dbplatform;

public interface JavaItemFilter<T> {
    public boolean isNeeded(T item);
}
