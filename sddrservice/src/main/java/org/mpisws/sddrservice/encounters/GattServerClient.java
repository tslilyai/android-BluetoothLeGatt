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
import android.util.Log;

import org.mpisws.sddrservice.lib.Constants;

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
    private BluetoothGatt mGatt;
    private BluetoothGattServer mGattServer;
    private SDDR_Core_Service mService;
    private byte[] mDHKey;

    public void initialize(BluetoothManager btmanager, SDDR_Core_Service service) {
        mService = service;
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
                byte[] value = CHARACTERISTIC_DHKEY_UUID.toString().getBytes();
                Log.d(TAG, "Sending DH key " + CHARACTERISTIC_DHKEY_UUID.toString() + " in response!");
                mGattServer.sendResponse(device, requestId, GATT_SUCCESS, 0, value);
            }
        }
    }

    public void connectToDevice(BluetoothDevice device) {
        String deviceAddress = device.getAddress();
        Log.v(TAG, "Got BT address to connect: " + deviceAddress);
        mGatt = device.connectGatt(mService, false, new GattClientCallback());
    }

    private class GattClientCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (status == BluetoothGatt.GATT_FAILURE) {
                Log.v(TAG, "ResponseTyp to connect to device");
                disconnectGattServer();
                return;
            } else if (status != GATT_SUCCESS) {
                disconnectGattServer();
                Log.v(TAG, "Unsuccessful connect to device " + status);
                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();
                Log.v(TAG, "Connected to device " + gatt.getDevice().getAddress());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnectGattServer();
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
            Log.d(TAG, "Got data " + characteristic.getValue());
            disconnectGattServer();
            //processData(characteristic.getValue());
        }

        public void disconnectGattServer() {
            Log.v(TAG, "Disconnecting device");
            if (mGatt != null) {
                mGatt.disconnect();
                mGatt.close();
            }
        }
    }
}
