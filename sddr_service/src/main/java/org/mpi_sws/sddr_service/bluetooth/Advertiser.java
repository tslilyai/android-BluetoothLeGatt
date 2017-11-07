package org.mpi_sws.sddr_service.bluetooth;

/**
 * Created by tslilyai on 10/18/17.
 */

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.os.ParcelUuid;
import android.util.Log;

import org.mpi_sws.sddr_service.lib.Constants;
import org.mpi_sws.sddr_service.lib.Utils;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Manages BLE Advertising.
 */
public class Advertiser {

    private static final String TAG = "SDDR: " + Advertiser.class.getSimpleName();
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private AdvertiseCallback mAdvertiseCallback;
    private AdvertiseSettings mAdvertiseSettings;
    private UUID mUUID;
    private byte[] mAddr = new byte[Constants.PUUID_LENGTH];
    private byte[] mAdData = new byte[Constants.ADVERT_LENGTH];

    public void initialize(BluetoothAdapter btAdapter) {
        mBluetoothLeAdvertiser = btAdapter.getBluetoothLeAdvertiser();
        Log.d(TAG, "Initialized Advertiser");
    }

    public void setAddr(byte[] addr) {
        Utils.myAssert(addr.length == Constants.ADDR_LENGTH);
        Log.d(TAG, "Setting Addr " + Utils.getHexString(addr));
        System.arraycopy(addr, 0, mAddr, 0, Constants.ADDR_LENGTH);
    }
    public void setAdData(byte[] newData) {
        Utils.myAssert(newData.length==Constants.ADVERT_LENGTH + Constants.PUUID_LENGTH - Constants.ADDR_LENGTH);
        // copy what data can fit into the puuid slot
        System.arraycopy(newData, 0, mAddr, Constants.ADDR_LENGTH, Constants.PUUID_LENGTH-Constants.ADDR_LENGTH);
        // copy the rest of the data into the advert
        System.arraycopy(newData, Constants.PUUID_LENGTH-Constants.ADDR_LENGTH, mAdData, 0, Constants.ADVERT_LENGTH);

        ByteBuffer bb = ByteBuffer.wrap(mAddr);
        long high = bb.getLong();
        long low = bb.getLong();
        mUUID = new UUID(high, low);
        Log.d(TAG, "Setting UUID " +  mUUID.toString());
        Log.d(TAG, "Setting Advert " +  Utils.getHexString(mAddr) + Utils.getHexString(mAdData));
    }

    public void resetAdvertiser() {
        if (mAdvertiseCallback != null)
            mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
        startAdvertising();
    }

    public void startAdvertising() {
        Log.d(TAG, "Starting Advertising");

        if (mAdvertiseCallback == null) {
            mAdvertiseCallback = new SDDRAdvertiseCallback();
            mAdvertiseSettings = buildAdvertiseSettings();
        }
        if (mBluetoothLeAdvertiser != null) {
            mBluetoothLeAdvertiser.startAdvertising(
                    mAdvertiseSettings,
                    buildAdvertiseData(),
                    buildAdvertiseData(),
                    mAdvertiseCallback
            );
        }
    }

    /**
     * Stops BLE Advertising.
     */
    public void stopAdvertising() {
        Log.d(TAG, "Stopping Advertising");
        if (mBluetoothLeAdvertiser != null) {
            mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
            mAdvertiseCallback = null;
        }
    }

    /**
     * Returns an AdvertiseData object which includes the Service UUID and Device Name.
     */
    private AdvertiseData buildAdvertiseData() {

        /**
         * Note: There is a strict limit of 31 Bytes on packets sent over BLE Advertisements.
         *  This includes everything put into AdvertiseData including UUIDs, device info, &
         *  arbitrary service or manufacturer data.
         *  Attempting to send packets over this limit will result in a failure with error code
         *  AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE. Catch this error in the
         *  onStartFailure() method of an AdvertiseCallback implementation.
         */
        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
        dataBuilder.setIncludeDeviceName(false);
        dataBuilder.setIncludeTxPowerLevel(false);

        dataBuilder.addServiceData(new ParcelUuid(mUUID), mAdData);
        return dataBuilder.build();
    }

    /**
     * Returns an AdvertiseSetParameters object
     */
    private AdvertiseSettings buildAdvertiseSettings() {
        // TODO active scheme connectable or scannable
        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();
        settingsBuilder.setConnectable(false); // TODO
        settingsBuilder.setTimeout(0);
        return settingsBuilder.build();
    }

    /**
     * Custom callback after Advertising succeeds or fails to start.
     */
    private class SDDRAdvertiseCallback extends AdvertiseCallback {
        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE) {
                Log.d(TAG, "Advertising failed: Data too large!");
            }
            else Log.d(TAG, "Advertising failed: Unknown");
        }

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.d(TAG, "Advertising successfully started");
        }
    }
}
