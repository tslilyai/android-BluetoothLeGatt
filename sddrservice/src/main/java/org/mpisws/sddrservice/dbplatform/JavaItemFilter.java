package org.mpisws.sddrservice.dbplatform;

public interface JavaItemFilter<T> {
    public boolean isNeeded(T item);
}
