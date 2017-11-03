package org.mpi_sws.sddr_userspace.lib;

import java.util.Set;

public class LinkabilityResult {

    private final Set<String> names;
    private final Set<Integer> stickerIDs;
    private final boolean linkable;

    public LinkabilityResult(Set<String> names, Set<Integer> stickerIDs, boolean linkable) {
        this.names = names;
        this.stickerIDs = stickerIDs;
        this.linkable = linkable;
    }

    public Set<String> getNames() {
        return names;
    }

    public boolean isLinkable() {
        return linkable;
    }

    public Set<Integer> getStickerIDs() {
        return stickerIDs;
    }
}
