package org.mpisws.sddrservice.lib;

import android.os.ParcelUuid;

import java.util.Collections;
import java.util.HashMap;
import java.util.IntSummaryStatistics;
import java.util.Map;
import java.util.UUID;

import static android.bluetooth.le.ScanSettings.SCAN_MODE_BALANCED;
import static android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY;
import static android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_POWER;

/**
 * 
 * @author verdelyi
 */
public class Constants {
    /**
     * UUID identified with this app - randomly generated by uuidgen
     */
    public static final UUID SERVICE_UUID = UUID
            .fromString("9fbc9d38-49fa-4063-bf59-b87b808ad211");
    public static final UUID CHARACTERISTIC_DHKEY_UUID = UUID
            .fromString("b3eede1a-5e06-4c85-a0e7-a844407befa2");
    public static final UUID DESCRIPTOR_CONFIG_UUID = UUID
            .fromString("82cbd8c1-fb1a-4c3d-86f5-bfe72367586e");
    public static final int REQUEST_ENABLE_BT = 1;
    public static final int REQUEST_ACCESS_FINE_LOCATION = 2;

    /**
     * Advertising-related constants
     */
    public static final int DHPUBKEY_LENGTH = 58;
    public static final int TOTAL_LENGTH = 31;
    public static final int PUUID_LENGTH = 16;
    public static final int ADDR_LENGTH = 4;
    public static final int ADVERT_LENGTH = TOTAL_LENGTH-PUUID_LENGTH-6;

    /**
     * Scanning-related constants
     */
    public static final long ACTIVE_CONNECT_INTERVAL = 120000;
    public static final long SCAN_BATCH_INTERVAL = 120000;
    public static final long CHANGE_EPOCH_TIME = 30000;

    /**
     * Other Constants
     */
    public static final android.icu.text.SimpleDateFormat fullTimeFormat = new android.icu.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    public static final int HANDSHAKE_DH_SIZE = 24;
}
