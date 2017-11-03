package org.mpi_sws.sddr_userspace;

import java.util.ArrayList;

/**
 * Created by tslilyai on 10/29/17.
 */

public class SDDR_Native {

    static { System.loadLibrary("c_SDDRRadio"); }
    static public native void c_mallocRadio();
    static public native void c_freeRadio();
    static public native SDDR_Core.RadioAction c_getNextRadioAction();
    static public native byte[] c_changeAndGetAdvert();
    static public native void c_changeEpoch();
    static public native byte[] c_getRandomAddr();
    static public native void c_processScanResult(byte[] addr, int rssi, byte[] advert);
    static public native void c_preDiscovery();
    static public native void c_postDiscovery();
    static public native void c_updateLinkability(byte[] arr);
    static public native byte[] c_getRetroactiveMatches(byte[] arr);

    static public long c_RadioPtr;
    static protected ArrayList<byte[]> c_EncounterMsgs = new ArrayList<>();
}