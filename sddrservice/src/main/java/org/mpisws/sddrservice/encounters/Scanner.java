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
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import org.mpisws.sddrservice.lib.Constants;
import org.mpisws.sddrservice.lib.Utils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Scans for Bluetooth Low Energy Advertisements matching a filter and displays them to the user.
 */
public class Scanner {
    private static final String TAG = "SDDR_API: " + Scanner.class.getSimpleName();
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private ScanCallback mScanCallback;
    private Handler mHandler;
    private SDDR_Core_Service mService;
    private BluetoothGatt mGatt;

    public Scanner(SDDR_Core_Service service) {
        mService = service;
    }

    public void initialize(BluetoothAdapter btAdapter) {
        this.mBluetoothAdapter = btAdapter;
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mHandler = new Handler();
        Log.d(TAG, "Initialized Scanner");
    }

    private class RunPostDiscovery implements Runnable {
        public boolean done = false;

        public void run() {
            stopScanning();
            Log.d(TAG, "Post discovery");

            /* sets the c_EncounterMsgs list in SDDR_Core */
            SDDR_Native.c_postDiscovery();
            mService.getCore().processEncounters();

            done = true;
            synchronized (this) {
                notifyAll();
            }
            done = false;
        }
    }
    private final RunPostDiscovery RunPD = new RunPostDiscovery();
    public void discoverAndProcessEncounters() {
        Log.d(TAG, "Prediscovery");
        SDDR_Native.c_preDiscovery();
        startScanning();
        mHandler.postDelayed(RunPD, Constants.SCAN_PERIOD);
        synchronized (RunPD) {
            if (!RunPD.done) {
                try {
                    RunPD.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Log.d(TAG, "Interrupted before PostDiscovery runnable completed");
                }
            }
        }
    }

    /**
     * Start scanning for BLE Advertisements (and stop after a set period of time).
     */
    public void startScanning() {
        if (mScanCallback == null) {
            Log.d(TAG, "Creating new scan callback");
            mScanCallback = new SDDRScanCallback();
            mBluetoothLeScanner.startScan(buildScanFilters(), buildScanSettings(), mScanCallback);
        } else {
            mBluetoothLeScanner.startScan(buildScanFilters(), buildScanSettings(), mScanCallback);
        }
        Log.d(TAG, "Starting Scanning");
    }

    /**
     * Stop scanning for BLE Advertisements.
     */
    public void stopScanning() {
        Log.d(TAG, "Stopping Scanning");
        mBluetoothLeScanner.stopScan(mScanCallback);
    }

    /**
     * Filter our scans so we only discover SDDR_API devices
     */
    private List<ScanFilter> buildScanFilters() {
        List<ScanFilter> scanFilters = new ArrayList<>();
        //ScanFilter filter = new ScanFilter.Builder().setServiceUuid(Constants.SDDR_UUID).build();
        //scanFilters.add(filter);
        return scanFilters;
    }

    /**
     * Return a {@link ScanSettings} object (default settings for now)
     */
    private ScanSettings buildScanSettings() {
        ScanSettings.Builder builder = new ScanSettings.Builder();
        return builder.build();
    }

    /**
     * Custom ScanCallback object. Calls the native function to process discoveries for encounters.
     */
    private class SDDRScanCallback extends ScanCallback {
        /* For active connections */
        private void connectResult(ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String deviceAddress = device.getAddress();
            Log.d(TAG, "Got BT address to connect: " + deviceAddress);
            mGatt = device.connectGatt(mService, false, new GattClientCallback());
        }

        private void processResult(ScanResult result) {
            Log.d(TAG, "Got scan result");
            ScanRecord record = result.getScanRecord();
            if (record == null) {
                Log.d(TAG, "No scan record");
                return;
            } else {
                Map<ParcelUuid, byte[]> datamap = record.getServiceData();
                if (datamap.size() != 1) {
                    Log.d(TAG, "Not SDDR_API: More than one service");
                    return;
                }

                for (ParcelUuid pu : datamap.keySet()) {
                    ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
                    bb.putLong(pu.getUuid().getMostSignificantBits());
                    bb.putLong(pu.getUuid().getLeastSignificantBits());
                    byte[] datahead = bb.array();
                    Log.d(TAG, "Got parceluuid data " + pu.getUuid().toString() + " of len " + datahead.length);

                    byte[] datatail = datamap.get(pu);
                    int len = datatail.length + datahead.length;
                    Utils.myAssert(datahead.length == Constants.PUUID_LENGTH);
                    if (len != Constants.PUUID_LENGTH + Constants.ADVERT_LENGTH) {
                        Log.d(TAG,"Not SDDR_API: Wrong advert length " + len);
                        return;
                    }
                    byte[] ID = Arrays.copyOfRange(datahead, 0, Constants.ADDR_LENGTH);
                    byte[] advert = new byte[Constants.PUUID_LENGTH+Constants.ADVERT_LENGTH-Constants.ADDR_LENGTH];
                    System.arraycopy(Arrays.copyOfRange(datahead, Constants.ADDR_LENGTH, Constants.PUUID_LENGTH),
                            0, advert, 0, Constants.PUUID_LENGTH-Constants.ADDR_LENGTH);
                    System.arraycopy(datatail, 0, advert, Constants.PUUID_LENGTH-Constants.ADDR_LENGTH, Constants.ADVERT_LENGTH);

                    int rssi = result.getRssi();
                    Log.d(TAG, "Processing SDDR_API scanresult with data " + Utils.getHexString(datahead) + Utils.getHexString(datatail) + ":\n"
                            + "\tID " + Utils.getHexString(ID) + ", " +
                            "advert " + Utils.getHexString(advert) + ", rssi " + rssi);
                    SDDR_Native.c_processScanResult(ID, rssi, advert);
                }
            }
        }
        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);

            for (ScanResult result : results) {
                processResult(result);
            }
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            processResult(result);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.d(TAG, "Scanning failed: " + errorCode);
        }
    }

    /* For active connections */
    private class GattClientCallback extends BluetoothGattCallback {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (status == BluetoothGatt.GATT_FAILURE) {
                Log.d(TAG, "ResponseTyp to connect to device");
                disconnectGattServer();
                return;
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                disconnectGattServer();
                Log.d(TAG, "Unsuccessful connect to device");
                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to device");
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnectGattServer();
            }
        }
        public void disconnectGattServer() {
            Log.d(TAG, "Disconnecting device");
            if (mGatt != null) {
                mGatt.disconnect();
                mGatt.close();
            }
        }
    }
}
