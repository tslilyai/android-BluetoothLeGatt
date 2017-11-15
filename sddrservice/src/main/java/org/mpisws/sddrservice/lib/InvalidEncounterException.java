package org.mpisws.sddrservice.lib;

public class InvalidEncounterException extends RuntimeException {

    private static final long serialVersionUID = 5546835421726853589L;

    public InvalidEncounterException(String detailMessage) {
        super(detailMessage);
    }

}
