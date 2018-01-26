package org.mpisws.sddrservice.encounters;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import org.mpisws.sddrservice.encounterhistory.ConfirmEncounterEvent;
import org.mpisws.sddrservice.lib.Constants;
import org.mpisws.sddrservice.lib.Identifier;
import org.mpisws.sddrservice.lib.Utils;

import java.util.Arrays;

import static org.mpisws.sddrservice.lib.Constants.CHARACTERISTIC_DHKEY_UUID;
import static org.mpisws.sddrservice.lib.Constants.SERVICE_UUID;

/**
 * Created by tslilyai on 1/26/18.
 */

public class GattClient {
    private static final String TAG = GattServer.class.getSimpleName();
    private Identifier myDHKey;
    private Identifier advert;
    private long pkid;
    private BluetoothGatt mGatt;
    private Context context;

    public GattClient(Context context) {
        this.context = context;
    }

    public void connectToDevice(BluetoothDevice dev, Long pkid, Identifier advert) {
        this.myDHKey = SDDR_Core.mDHKey;
        this.advert = advert;
        this.pkid = pkid;
        String deviceAddress = dev.getAddress();
        Log.v(TAG, "BT address to connect: " + deviceAddress);
        mGatt = dev.connectGatt(context, false, new GattClientCallback());
    }

    private class GattClientCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();
                Log.v(TAG, "Connected to device " + gatt.getDevice().getAddress());
            } else  if (newState ==BluetoothProfile.STATE_DISCONNECTED) {
                disconnectGattServer();
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
            disconnectGattServer();
            if (data != null) {
                data = Arrays.copyOf(data, Constants.DHPUBKEY_LENGTH);
                Log.d(TAG, "Got data " + new Identifier(data).toString());
                Identifier secretKeyID = new Identifier(SDDR_Native.c_computeSecretKey(myDHKey.getBytes(), advert.getBytes(), data));

                Utils.myAssert(SDDR_Core.confirmEvents != null);
                SDDR_Core.confirmEvents.add(new ConfirmEncounterEvent(pkid, secretKeyID, System.currentTimeMillis()));
            }
        }

        public void disconnectGattServer() {
            if (mGatt != null) {
                Log.v(TAG, "Disconnecting device " + mGatt.getDevice().toString());
                mGatt.disconnect();
                mGatt.close();
            }
        }
    }
}
