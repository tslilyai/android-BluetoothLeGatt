package org.mpisws.sddrservice.encounters;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import org.mpisws.sddrservice.encounterhistory.ConfirmEncounterEvent;
import org.mpisws.sddrservice.lib.Constants;
import org.mpisws.sddrservice.lib.Identifier;
import org.mpisws.sddrservice.lib.Utils;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.mpisws.sddrservice.lib.Constants.CHARACTERISTIC_DHKEY_UUID;
import static org.mpisws.sddrservice.lib.Constants.SERVICE_UUID;

/**
 * Created by tslilyai on 1/26/18.
 */

public class GattClient {
    private static final String TAG = GattClient.class.getSimpleName();
    private static final int NUMRETRIES = 2;
    private static final GattClient instance = new GattClient();
    private Handler handler;
    private  ConcurrentLinkedQueue<DeviceData> devicesToConnect;
    private Identifier myDHKey;
    private BluetoothGatt mGatt;
    private Context context;
    private boolean isWorking;
    private DeviceData curDev;

    private GattClient() {
        isWorking = false;
        devicesToConnect = new ConcurrentLinkedQueue<>();
        handler = new Handler();
    }

    public static GattClient getInstance() {
        return instance;
    }

    public void stop() {
        devicesToConnect.clear();
        isWorking = false;
    }

    public boolean amIWorking() {
        return isWorking;
    }

    public class DeviceData {
        BluetoothDevice dev;
        long pkid;
        Identifier advert;
        int numTries;

        DeviceData(BluetoothDevice dev, long pkid, Identifier ad, int numTries) {
            this.dev = dev;
            this.pkid = pkid;
            this.advert = ad;
            this.numTries = numTries;
        }
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void addDeviceToConnect(BluetoothDevice dev, long pkid, Identifier advert) {
        Log.v(TAG, dev.getAddress() + ": Added to connect queue");
        devicesToConnect.add(new DeviceData(dev, pkid,advert, 0));
    }

    public void addDeviceToConnect(DeviceData dev) {
        Log.v(TAG, dev.dev.getAddress() + ": Added to connect queue");
        devicesToConnect.add(dev);
    }

    public  void connectDevices() {
        if (!isWorking) {
            isWorking = true;
            connectNextDevice();
        }
    }

    public void connectNextDevice() {
        DeviceData dev = devicesToConnect.poll();
        if (dev != null) {
            isWorking = true;
            if (dev.numTries < NUMRETRIES)
                handler.postDelayed(() -> connectToDevice(dev), 30000);
        } else {
            isWorking = false;
        }
    }

    public void connectToDevice(DeviceData curDev) {
        this.curDev = curDev;
        this.myDHKey = SDDR_Core.mDHKey;
        String deviceAddress = curDev.dev.getAddress();
        Log.d(TAG, deviceAddress + ": attempt connection");
        mGatt = curDev.dev.connectGatt(context, false, new GattClientCallback());
    }

    private class GattClientCallback extends BluetoothGattCallback {
        boolean success = false;
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();
                Log.v(TAG, "Connected to device " + gatt.getDevice().getAddress());
            } else if (newState ==BluetoothProfile.STATE_DISCONNECTED) {
                if (gatt.getDevice() != null)
                    Log.d(TAG, gatt.getDevice().getAddress() + ": Disconnected");
                else
                    Log.d(TAG, gatt.getDevice() + ": Disconnected");
                disconnectGattServer();
                return;
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, gatt.getDevice().getAddress() + ": Unsuccessful Connect");
                disconnectGattServer();
                return;
            }
        }
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (gatt.getServices().size() == 0) {
                if (gatt.getDevice() != null)
                    Log.d(TAG, gatt.getDevice().getAddress() + ": Zero service of the right type!");
                else
                    Log.d(TAG, gatt.getDevice() + ": Zero service of the right type!");
                disconnectGattServer();
            }
            boolean found = false;
            for (BluetoothGattService service : gatt.getServices()) {
                if (service.getUuid().compareTo(SERVICE_UUID) == 0) {
                    gatt.readCharacteristic(gatt.getService(SERVICE_UUID).getCharacteristic(CHARACTERISTIC_DHKEY_UUID));
                    found = true;
                }
            }
            if (!found){
                if (gatt.getDevice() != null)
                    Log.d(TAG, gatt.getDevice().getAddress() + ": No service of the right type!");
                else
                    Log.d(TAG, gatt.getDevice() + ": No service of the right type!");
                disconnectGattServer();
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            byte[] data = characteristic.getValue();
            success = true;
            disconnectGattServer();
            if (data != null) {
                data = Arrays.copyOf(data, Constants.DHPUBKEY_LENGTH);
                Log.d(TAG, gatt.getDevice().getAddress() + ": data " + new Identifier(data).toString());
                Identifier secretKeyID = new Identifier(SDDR_Native.c_computeSecretKey(myDHKey.getBytes(), curDev.advert.getBytes(), data));

                Utils.myAssert(SDDR_Core.confirmEvents != null);
                SDDR_Core.confirmEvents.add(new ConfirmEncounterEvent(curDev.pkid, secretKeyID, System.currentTimeMillis()));
            }
        }

        public void disconnectGattServer() {
            if (mGatt != null) {
                Log.d(TAG, "Disconnecting+Close device " + mGatt.getDevice().toString());
                mGatt.disconnect();
                mGatt.close();
                mGatt = null;
            }
            if (!success) {
                curDev.numTries = curDev.numTries + 1;
                addDeviceToConnect(curDev);
            }
            connectNextDevice();
        }
    }
}
