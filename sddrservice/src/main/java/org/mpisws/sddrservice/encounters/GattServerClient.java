package org.mpisws.sddrservice.encounters;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import org.mpisws.sddrservice.encounterhistory.ConfirmEncounterEvent;
import org.mpisws.sddrservice.lib.Constants;
import org.mpisws.sddrservice.lib.Identifier;
import org.mpisws.sddrservice.lib.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;
import static android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ;
import static android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY;
import static org.mpisws.sddrservice.lib.Constants.CHARACTERISTIC_DHKEY_UUID;
import static org.mpisws.sddrservice.lib.Constants.SERVICE_UUID;

/**
 * Created by tslilyai on 1/16/18.
 */

public class GattServerClient {
    private static final String TAG = GattServerClient.class.getSimpleName();
    private Map<String, BluetoothGatt> mGattMap;
    private BluetoothGattServer mGattServer;
    private Context mService;
    private Identifier myDHKey;
    private Identifier myDHPubKey;
    private Identifier advert;
    private long pkid;
    private ConcurrentLinkedQueue<BluetoothGatt> devicesToConnect;


    public GattServerClient(BluetoothManager btmanager, Context context) {
        mService = context;
        myDHKey = SDDR_Core.mDHKey;
        myDHPubKey = SDDR_Core.mDHPubKey;
        devicesToConnect = new ConcurrentLinkedQueue<>();
        mGattMap = new ConcurrentHashMap<>();
        mGattServer = btmanager.openGattServer(mService, new GattServerCallback());
        mGattServer.addService(createService());
    }

    private BluetoothGattService createService() {
        BluetoothGattService service = new BluetoothGattService(Constants.SERVICE_UUID, SERVICE_TYPE_PRIMARY);
        BluetoothGattCharacteristic DHkey = new BluetoothGattCharacteristic(CHARACTERISTIC_DHKEY_UUID, PROPERTY_READ | PROPERTY_NOTIFY, PERMISSION_READ);
        service.addCharacteristic(DHkey);
        return service;
    }

    private class GattServerCallback extends BluetoothGattServerCallback {
        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device,
                                                int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            if (CHARACTERISTIC_DHKEY_UUID.equals(characteristic.getUuid())) {
                if (myDHPubKey != null) {
                    byte[] value = myDHPubKey.getBytes();
                    Log.d(TAG, "Sending DH key " + myDHPubKey.toString() + " in response!");
                    mGattServer.sendResponse(device, requestId, GATT_SUCCESS, 0, value);
                }
            }
        }
    }

    public void connectToDevice(BluetoothDevice dev, Long pkid, Identifier advert) {
        this.myDHKey = SDDR_Core.mDHKey;
        this.myDHPubKey = SDDR_Core.mDHPubKey;
        this.advert = advert;
        this.pkid = pkid;
        String deviceAddress = dev.getAddress();
        Log.v(TAG, "BT address to connect: " + deviceAddress);
        mGattMap.put(dev.getAddress(), dev.connectGatt(mService, false, new GattClientCallback()));
    }

    public void connectToFailedDevices() {
        BluetoothGatt gatt = devicesToConnect.poll();
        while (gatt != null) {
            gatt.getDevice().connectGatt(mService, false, new GattClientCallback());
            gatt = devicesToConnect.poll();
        }
    }

    private class GattClientCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();
                Log.v(TAG, "Connected to device " + gatt.getDevice().getAddress());
            } else {
                disconnectGattServer(gatt.getDevice());
                devicesToConnect.add(gatt);
                Log.v(TAG, "Unsuccessful connect to device " + status);
                return;
            }
        }
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            for (BluetoothGattService service : gatt.getServices()) {
                if (service.getUuid().compareTo(SERVICE_UUID) == 0)
                    gatt.readCharacteristic(gatt.getService(SERVICE_UUID).getCharacteristic(CHARACTERISTIC_DHKEY_UUID));
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            byte[] data = characteristic.getValue();
            disconnectGattServer(gatt.getDevice());
            if (data != null) {
                data = Arrays.copyOf(data, Constants.DHPUBKEY_LENGTH);
                Log.d(TAG, "Got data " + new Identifier(data).toString());
                Identifier secretKeyID = new Identifier(SDDR_Native.c_computeSecretKey(myDHKey.getBytes(), advert.getBytes(), data));

                Utils.myAssert(SDDR_Core.confirmEvents != null);
                SDDR_Core.confirmEvents.add(new ConfirmEncounterEvent(pkid, Arrays.asList((secretKeyID)), System.currentTimeMillis()));
            }
        }

        public void disconnectGattServer(BluetoothDevice dev) {
            Log.v(TAG, "Disconnecting device");
            if (dev != null) {
                BluetoothGatt mGatt;
                if ((mGatt = mGattMap.get(dev.getAddress())) != null) {
                    mGatt.disconnect();
                    mGatt.close();
                }
                mGattMap.remove(dev.getAddress());
            }
        }
    }
}
