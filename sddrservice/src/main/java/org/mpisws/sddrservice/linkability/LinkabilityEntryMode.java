package org.mpisws.sddrservice.linkability;

/**
 *
 * @author verdelyi
 */
public enum LinkabilityEntryMode {

    ListenOnly, AdvertiseAndListen;

    public int toInt() {
        switch (this) {
            case ListenOnly:
                return 0;
            case AdvertiseAndListen:
                return 1;
            default:
                throw new IllegalStateException();
        }
    }

    public static LinkabilityEntryMode fromInt(final int rawMode) {
        switch (rawMode) {
            case 0:
                return ListenOnly;
            case 1:
                return AdvertiseAndListen;
            default:
                throw new IllegalStateException();
        }
    }
}
