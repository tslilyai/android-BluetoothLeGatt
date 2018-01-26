package org.mpisws.sddrservice.encounters;

/**
 * Created by tslilyai on 10/18/17.
 *
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import org.mpisws.sddrservice.lib.Constants;
import org.mpisws.sddrservice.lib.Identifier;
import org.mpisws.sddrservice.lib.Utils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_POWER;

/**
 * Scans for Bluetooth Low Energy Advertisements matching a filter and displays them to the user.
 */
public class Scanner {
    private static final String TAG = "SDDR_API: " + Scanner.class.getSimpleName();
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private ScanCallback mScanCallback;
    private SDDR_Core core;
    private Set<String> uniqueDevicesCtr = new HashSet<>();
    private Map<String, Integer> deviceAdverts = new HashMap<>();
    protected boolean serverRunning;
    private Context context;

    public Scanner(Context context) {
        this.serverRunning = false;
        this.context = context;
    }

    public void initialize(BluetoothAdapter btAdapter, SDDR_Core core) {
        this.mBluetoothAdapter = btAdapter;
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        this.core = core;
    }

    /**
     * Start scanning for BLE Advertisements (and stop after a set period of time).
     */
    public void startScanning() {
        if (mScanCallback == null) {
            Log.v(TAG, "Creating new scan callback");
            mScanCallback = new SDDRScanCallback();
            mBluetoothLeScanner.startScan(buildScanFilters(), buildScanSettings(), mScanCallback);
        } else {
            mBluetoothLeScanner.startScan(buildScanFilters(), buildScanSettings(), mScanCallback);
        }
        Log.v(TAG, "Starting Scanning");
    }

    /**
     * Stop scanning for BLE Advertisements.
     */
   public void stopScanning() {
        Log.v(TAG, "Stopping Scanning");
        mBluetoothLeScanner.stopScan(mScanCallback);
   }


    /**
     * Filter our scans so we only discover SDDR_API devices
     */
    private List<ScanFilter> buildScanFilters() {
        List<ScanFilter> scanFilters = new ArrayList<>();
        return scanFilters;
    }

    /**
     * Return a {@link ScanSettings} object (default settings for now)
     */
    private ScanSettings buildScanSettings() {
        ScanSettings.Builder builder = new ScanSettings.Builder();
        builder.setScanMode(SCAN_MODE_LOW_POWER);
        builder.setReportDelay(Constants.SCAN_BATCH_INTERVAL);
        return builder.build();
    }

    /**
     * Custom ScanCallback object. Calls the native function to process discoveries for encounters.
     */
    private class SDDRScanCallback extends ScanCallback {
        private void processResult(ScanResult result) {
            String addr = result.getDevice().getAddress();
            Log.v(TAG, "Scan result processing " + result.getDevice().getAddress());
           ScanRecord record = result.getScanRecord();
            if (record == null) {
                Log.v(TAG, "No scan record");
                return;
            } else {
                Map<ParcelUuid, byte[]> datamap = record.getServiceData();
                if (datamap.keySet().size() == 0) {
                    Log.d(TAG, "No data");
                } else {
                    uniqueDevicesCtr.add(addr);
                    if (deviceAdverts.containsKey(addr)) {
                        int count = deviceAdverts.get(addr);
                        deviceAdverts.put(addr, count + 1);
                    } else {
                        deviceAdverts.put(addr, 1);
                    }
                }
                for (ParcelUuid pu : datamap.keySet()) {
                    ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
                    bb.putLong(pu.getUuid().getMostSignificantBits());
                    bb.putLong(pu.getUuid().getLeastSignificantBits());
                    byte[] datahead = bb.array();
                    Log.v(TAG, "Got parceluuid data " + pu.getUuid().toString() + " of len " + datahead.length);

                    byte[] datatail = datamap.get(pu);
                    int len = datatail.length + datahead.length;
                    Utils.myAssert(datahead.length == Constants.PUUID_LENGTH);
                    if (len != Constants.PUUID_LENGTH + Constants.ADVERT_LENGTH) {
                        Log.v(TAG, "Scan Result (not SDDR: wrong advert length " + len + "!): Device: " + addr + ": " + result.getDevice().getName());
                        return;
                    }
                    byte[] ID = Arrays.copyOfRange(datahead, 0, Constants.ADDR_LENGTH);
                    byte[] advert = new byte[Constants.PUUID_LENGTH+Constants.ADVERT_LENGTH-Constants.ADDR_LENGTH];
                    System.arraycopy(Arrays.copyOfRange(datahead, Constants.ADDR_LENGTH, Constants.PUUID_LENGTH),
                            0, advert, 0, Constants.PUUID_LENGTH-Constants.ADDR_LENGTH);
                    System.arraycopy(datatail, 0, advert, Constants.PUUID_LENGTH-Constants.ADDR_LENGTH, Constants.ADVERT_LENGTH);

                    int rssi = result.getRssi();
                    byte[] devaddress = addr.getBytes();
                    Log.v(TAG, "Scan Result (SDDR): Device: " + addr + ": " + result.getDevice().getName());
                    Log.v(TAG, "Processing SDDR_API scanresult with data " + Utils.getHexString(datahead) + Utils.getHexString(datatail) + ":\n"
                            + "\tID " + Utils.getHexString(ID) + ", " +
                            "advert " + Utils.getHexString(advert) + ", rssi " + rssi
                            + " devAddr " + addr);
                    // if this is a new device
                    long pkid = SDDR_Native.c_processScanResult(ID, rssi, advert, devaddress);

                   // only attempt to connect to the device if
                    // (1) you are running the GATT server for active connections and
                    // (2) if the device is a new one
                    if (serverRunning) {
                        GattClient.getInstance().addDeviceToConnect(result.getDevice(), pkid, new Identifier(advert));
                    }
                }
            }
        }
        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            Log.d(TAG, results.size() + " Batch Results Found");
            SDDR_Native.c_preDiscovery();
            for (ScanResult result : results) {
                processResult(result);
            }
            SDDR_Native.c_postDiscovery();
            core.postScanProcessing();
            if (serverRunning) {
                GattClient.getInstance().connectDevices();
            }
            Log.d(TAG, "cumulative unique devices: " + uniqueDevicesCtr.size());
            for (String addr : deviceAdverts.keySet()) {
                Log.d(TAG, "\t " + addr + ", " + deviceAdverts.get(addr));
            }
            deviceAdverts.clear();
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            processResult(result);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.v(TAG, "Scanning failed: " + errorCode);
        }
    }
}
