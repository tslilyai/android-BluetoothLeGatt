package org.mpi_sws.sddr_service.lib;

public interface Transformator<SRC, TGT> {
    public TGT transform(final SRC src);
}
