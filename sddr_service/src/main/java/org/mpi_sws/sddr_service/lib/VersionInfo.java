package org.mpi_sws.sddr_service.lib;

import java.io.Serializable;

public class VersionInfo implements Serializable {

    private static final long serialVersionUID = 315567862069311546L;
    private final long code;
    private final String name;

    public VersionInfo(long code, String name) {
        this.code = code;
        this.name = name;
    }

    public long getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name + " (code " + code + ")";
    }
}
