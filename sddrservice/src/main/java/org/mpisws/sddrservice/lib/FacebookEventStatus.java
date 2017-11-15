package org.mpisws.sddrservice.lib;

public enum FacebookEventStatus {
    Base, BaseWithMessage, PendingValidation, IncomingInvitation, NonExistent;

    private static final EnumConverter<FacebookEventStatus> converter = new EnumConverter<FacebookEventStatus>();

    static {
        converter.addMapping(Base, 1);
        converter.addMapping(BaseWithMessage, 2);
        converter.addMapping(PendingValidation, 3);
        converter.addMapping(IncomingInvitation, 4);
        converter.addMapping(NonExistent, 5);
    }

    public int toInt() {
        return converter.toInt(this);
    }

    public static FacebookEventStatus fromInt(final int i) {
        return converter.fromInt(i);
    }
}
