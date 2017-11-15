package org.mpisws.sddrservice.lib;

public interface Transformator<SRC, TGT> {
    public TGT transform(final SRC src);
}
