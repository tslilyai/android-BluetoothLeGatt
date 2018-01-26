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

import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

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

public class GattServer {
    private static final String TAG = GattServer.class.getSimpleName();
    private BluetoothGattServer mGattServer;

    public GattServer(BluetoothManager btmanager, Context context) {
        mGattServer = btmanager.openGattServer(context, new GattServerCallback());
        if (mGattServer != null)
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
                if (SDDR_Core.mDHPubKey != null) {
                    byte[] value = SDDR_Core.mDHPubKey.getBytes();
                    Log.d(TAG, "Sending DH key " + SDDR_Core.mDHPubKey.toString() + " in response!");
                    mGattServer.sendResponse(device, requestId, GATT_SUCCESS, 0, value);
                }
            }
        }
    }
}
