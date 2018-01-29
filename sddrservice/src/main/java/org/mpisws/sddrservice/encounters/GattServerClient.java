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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.Pair;

import org.mpisws.sddrservice.encounterhistory.ConfirmEncounterEvent;
import org.mpisws.sddrservice.lib.Constants;
import org.mpisws.sddrservice.lib.Identifier;
import org.mpisws.sddrservice.lib.Utils;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;
import static android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PERMISSION_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;
import static android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static org.mpisws.sddrservice.encounters.Advertiser.mAddr;
import static org.mpisws.sddrservice.lib.Constants.ADDR_LENGTH;
import static org.mpisws.sddrservice.lib.Constants.CHARACTERISTIC_DHKEY_UUID;
import static org.mpisws.sddrservice.lib.Constants.CHARACTERISTIC_MESSAGE_UUID;
import static org.mpisws.sddrservice.lib.Constants.DHPUBKEY_LENGTH;
import static org.mpisws.sddrservice.lib.Constants.SERVICE_UUID;

/**
 * Created by tslilyai on 1/16/18.
 */

public class GattServerClient extends BluetoothGattServerCallback {
    private static final String TAG = GattServerClient.class.getSimpleName();
    private Map<String, BluetoothGatt> mActiveGatt; // Protected by synchronized(mActiveGatt)
    private Queue<Pair<BluetoothDevice, GattClientCallback>> mFutureGatt; // Protected by synchronized(mActiveGatt)
    private Queue<Pair<BluetoothDevice, GattClientCallbackForMessaging>> mFutureGattForMsging; // Protected by synchronized(mActiveGatt)
    private BluetoothGattServer mGattServer;
    private Context mService;
    private static GattServerClient instance = new GattServerClient();
    public static ConcurrentLinkedQueue<Pair<Identifier, Identifier>> receivedSDDRAddrsAndDHPubKeys = new ConcurrentLinkedQueue();

    public static GattServerClient getInstance() {
        return instance;
    }

    private GattServerClient() {
        mActiveGatt = new HashMap<>();
        mFutureGatt = new ArrayDeque();
        mFutureGattForMsging = new ArrayDeque();
    };

    private static final int MAX_CONCURRENT_GATT = 7; // TODO: Find where this was specified in Android framework

    public void initialize(BluetoothManager btmanager, Context context) {
        mService = context;

        mGattServer = btmanager.openGattServer(mService, this);
        if (mGattServer != null) {
            BluetoothGattService service = new BluetoothGattService(Constants.SERVICE_UUID, SERVICE_TYPE_PRIMARY);
            BluetoothGattCharacteristic DHKeyChar = new BluetoothGattCharacteristic(CHARACTERISTIC_DHKEY_UUID, PROPERTY_READ | PROPERTY_WRITE, PERMISSION_READ | PERMISSION_WRITE);
            service.addCharacteristic(DHKeyChar);

            mGattServer.addService(service);
        } else {
            Log.e(TAG, "Failed to start the GATT server");
        }
    }

    @Override
    public void onConnectionStateChange(BluetoothDevice dev, int status, int newState) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            Log.v(TAG, "SERVER Connected to device " + dev.getAddress());
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            Log.v(TAG, "SERVER Disconnected from device " + dev.getAddress() + " (" + status + ")");
        }
    }

    public void connectToDevice(BluetoothDevice dev, Long pkid, Identifier advert) {
        String deviceAddress = dev.getAddress();
        Log.v(TAG, "BT address to connect: " + deviceAddress);
        synchronized (mActiveGatt) {
            GattClientCallback callback = new GattClientCallback(pkid, advert);
            if (mActiveGatt.size() < MAX_CONCURRENT_GATT) {
                mActiveGatt.put(deviceAddress, dev.connectGatt(mService, false, callback));
            } else {
                mFutureGatt.add(new Pair<>(dev, callback));
            }
        }
    }

    public void dropConnections() {
        synchronized (mActiveGatt) {
            for (BluetoothGatt gatt : mActiveGatt.values()) {
                gatt.disconnect();
                gatt.close();
            }
            mActiveGatt.clear();
            mFutureGatt.clear();
            mFutureGattForMsging.clear();
        }
    }

    public void sendMsgToDevice(BluetoothDevice dev, String msg) {
        String deviceAddress = dev.getAddress();
        Log.v(TAG, "BT address to msg : " + deviceAddress);
        synchronized (mActiveGatt) {
            GattClientCallbackForMessaging callback = new GattClientCallbackForMessaging(msg);
            if (mActiveGatt.size() < MAX_CONCURRENT_GATT) {
                mActiveGatt.put(deviceAddress, dev.connectGatt(mService, false, callback));
            } else {
                mFutureGattForMsging.add(new Pair<>(dev, callback));
            }
        }
    }

    public void getSSForPKIDWithDHKey(Long pkid, Identifier peerDHPubKey) {
        onPeerDHKeyReceived(pkid, null, peerDHPubKey, false);
    }

    private void onPeerDHKeyReceived(Long pkid, Identifier advert, Identifier peerDHPubKey, boolean checkAdvert) {
        Log.d(TAG, "Received DH key " + peerDHPubKey.toString());
        Identifier secretKeyID;
        if (checkAdvert) {
            secretKeyID = new Identifier(SDDR_Native.c_computeSecretKeyWithSHA(SDDR_Core.mDHKey.getBytes(), advert.getBytes(), peerDHPubKey.getBytes()));
        } else {
            secretKeyID = new Identifier(SDDR_Native.c_computeSecretKey(SDDR_Core.mDHKey.getBytes(), peerDHPubKey.getBytes()));
        }

        Utils.myAssert(SDDR_Core.confirmEvents != null);
        SDDR_Core.confirmEvents.add(new ConfirmEncounterEvent(pkid, secretKeyID, System.currentTimeMillis()));
    }

    @Override
    public void onCharacteristicReadRequest(BluetoothDevice device,
                                            int requestId, int offset,
                                            BluetoothGattCharacteristic characteristic) {
        if (CHARACTERISTIC_DHKEY_UUID.equals(characteristic.getUuid())) {
            if (SDDR_Core.mDHPubKey != null) {
                Log.d(TAG, "Sending DH key " + SDDR_Core.mDHPubKey.toString() + " in response!");

                byte[] value = SDDR_Core.mDHPubKey.getBytes();
                mGattServer.sendResponse(device, requestId, GATT_SUCCESS, 0, value);
            }
        }
    }

    @Override
    public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                             BluetoothGattCharacteristic characteristic,
                                             boolean preparedWrite, boolean responseNeeded,
                                             int offset, byte[] value) {
        if (CHARACTERISTIC_DHKEY_UUID.equals(characteristic.getUuid())) {
            Identifier peerAddr = new Identifier(Arrays.copyOf(value, ADDR_LENGTH));
            Identifier peerDHPubKey = new Identifier(Arrays.copyOfRange(value, ADDR_LENGTH, DHPUBKEY_LENGTH));
            Log.d(TAG, "Someone wrote their key down for me :) " + peerAddr.toString() + " " + peerDHPubKey.toString());
            receivedSDDRAddrsAndDHPubKeys.add(new Pair(peerAddr, peerDHPubKey));
        } else if (CHARACTERISTIC_MESSAGE_UUID.equals(characteristic.getUuid())) {
            Log.d(TAG, "Received message " + value.toString());
        }
        mGattServer.sendResponse(device, requestId, GATT_SUCCESS, 0, null);
    }

    // TODO: Should perform our actions from callbacks in a Handler thread potentially
    // (See slide 29 in https://droidcon.de/sites/global.droidcon.cod.newthinking.net/files/media/documents/practical_bluetooth_le_on_android_0.pdf)
    private class GattClientCallback extends BluetoothGattCallback implements Handler.Callback {
        private static final int MSG_DISCOVER_SERVICES = 0;
        private static final int MSG_SERVICES_DISCOVERED = 1;
        private static final int MSG_WRITE_DONE = 2;
        private long pkid;
        private Identifier advert;
        private Handler bleHandler;
        private byte[] addrAndDHKey;

        public GattClientCallback(long pkid, Identifier advert) {
            this.pkid = pkid;
            this.advert = advert;
            HandlerThread handlerThread = new HandlerThread("BLE-Worker");
            handlerThread.start();
            bleHandler = new Handler(handlerThread.getLooper(), this);
        }

        public void dispose() {
            bleHandler.removeCallbacksAndMessages(null);
            bleHandler.getLooper().quit();
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            BluetoothDevice dev = gatt.getDevice();

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.v(TAG, "Connected to device " + dev.getAddress());
                // Step -1: Change the MTU so we can send everything
                bleHandler.post(() -> gatt.requestMtu(ADDR_LENGTH+DHPUBKEY_LENGTH+10));
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.v(TAG, "Disconnected from device " + dev.getAddress() + " (" + status + ")");
                close(gatt);
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int newState) {
            super.onMtuChanged(gatt, mtu, newState);
            Log.d(TAG, "Mtu changed to " + mtu);
            // Step 0: Discover the services to look for the SDDR service (wait for onServicesDiscovered)
            if (newState != STATE_CONNECTED) {
                Log.d(TAG, "UHOH");
            }
            bleHandler.obtainMessage(MSG_DISCOVER_SERVICES, gatt).sendToTarget();
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            bleHandler.obtainMessage(MSG_SERVICES_DISCOVERED, gatt).sendToTarget();
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status != GATT_SUCCESS) {
                disconnect(gatt);
                return;
            }

            bleHandler.post(() -> {
                byte[] data = characteristic.getValue();
                if (data != null) {
                    Identifier peerDHPubKey = new Identifier(Arrays.copyOf(data, DHPUBKEY_LENGTH));
                    onPeerDHKeyReceived(pkid, advert, peerDHPubKey, true);
                }

                // Step 2: Write our DHKEY value to the remote peer (wait for onCharacteristicWrite)
                this.addrAndDHKey = new byte[ADDR_LENGTH + DHPUBKEY_LENGTH];
                System.arraycopy(mAddr, 0, addrAndDHKey, 0, ADDR_LENGTH);
                System.arraycopy(SDDR_Core.mDHPubKey.getBytes(), 0, addrAndDHKey, ADDR_LENGTH, DHPUBKEY_LENGTH);

                Log.d(TAG, "This is the final thing I'm writing: " + addrAndDHKey.length + " " + new Identifier(addrAndDHKey).toString());

                characteristic.setValue(Arrays.copyOfRange(addrAndDHKey, 0, ADDR_LENGTH+DHPUBKEY_LENGTH));
                gatt.writeCharacteristic(characteristic);
            });
       }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "Wrote all my DHKey!");
            bleHandler.obtainMessage(MSG_WRITE_DONE, gatt).sendToTarget();
            // Step 3: Disconnect since we are done the protocol (wait for N/A)
            // (It is also possible the write failed, but we would disconnect anyway)
        }

        private void disconnect(BluetoothGatt gatt) {
            BluetoothDevice dev = gatt.getDevice();
            Log.v(TAG, "Disconnecting from device " + dev.getAddress());
            gatt.disconnect();
        }

        private void close(BluetoothGatt gatt) {
            BluetoothDevice dev = gatt.getDevice();
            Log.v(TAG, "Closing device " + dev.getAddress());
            gatt.close();

            synchronized (mActiveGatt) {
                mActiveGatt.remove(dev.getAddress());
                if (mFutureGatt.size() > 0) {
                    Pair<BluetoothDevice, GattClientCallback> futureInfo = mFutureGatt.remove();
                    mActiveGatt.put(futureInfo.first.getAddress(), futureInfo.first.connectGatt(mService, false, futureInfo.second));
                }
            }
        }

        @Override
        public boolean handleMessage(Message msg) {
            BluetoothGatt gatt = (BluetoothGatt) msg.obj;
            switch (msg.what) {
                case MSG_DISCOVER_SERVICES:
                    gatt.discoverServices();
                    break;
                case MSG_SERVICES_DISCOVERED:
                    for (BluetoothGattService service : gatt.getServices()) {
                        if (service.getUuid().compareTo(SERVICE_UUID) == 0) {
                            // Step 1: Read the DHKEY value from the remote peer (wait for onCharacteristicRead)
                            // TODO: Potentially need to wait until all devices report that services were discovered?
                            // (See bold text in 2nd answer of https://stackoverflow.com/questions/21237093/)
                            if (service.getCharacteristic(CHARACTERISTIC_DHKEY_UUID) == null) {
                                disconnect(gatt);
                            } else {
                                gatt.readCharacteristic(service.getCharacteristic(CHARACTERISTIC_DHKEY_UUID));
                            }
                        }
                    }
                    break;
                case MSG_WRITE_DONE:
                    disconnect(gatt);
            }
            return true;
        }
    }

    private class GattClientCallbackForMessaging extends BluetoothGattCallback implements Handler.Callback {
        private static final int MSG_DISCOVER_SERVICES = 0;
        private static final int MSG_SERVICES_DISCOVERED = 1;
        private static final int MSG_WRITE_DONE = 2;
        private Handler bleHandler;
        private String msg;

        public GattClientCallbackForMessaging(String msg) {
            HandlerThread handlerThread = new HandlerThread("BLE-Worker");
            handlerThread.start();
            bleHandler = new Handler(handlerThread.getLooper(), this);
            this.msg = msg;
        }

        public void dispose() {
            bleHandler.removeCallbacksAndMessages(null);
            bleHandler.getLooper().quit();
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            BluetoothDevice dev = gatt.getDevice();

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.v(TAG, "Connected to device " + dev.getAddress());
                // Step -1: Discover the services to look for the SDDR service (wait for onServicesDiscovered)
                bleHandler.post(() -> gatt.requestMtu(msg.length() + 10));
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.v(TAG, "Disconnected from device " + dev.getAddress() + " (" + status + ")");
                close(gatt);
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int newState) {
            super.onMtuChanged(gatt, mtu, newState);
            Log.d(TAG, "Mtu changed to " + mtu + " " + newState);
            if (newState != STATE_CONNECTED) {
                Log.d(TAG, "UHOH");
            }
            // Step 0: Discover the services to look for the SDDR service (wait for onServicesDiscovered)
            bleHandler.obtainMessage(MSG_DISCOVER_SERVICES, gatt).sendToTarget();
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            bleHandler.obtainMessage(MSG_SERVICES_DISCOVERED, gatt).sendToTarget();
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            // Step 3: Disconnect since we are done the protocol (wait for N/A)
            // (It is also possible the write failed, but we would disconnect anyway)
            Log.d(TAG, "Wrote my message!");
            bleHandler.obtainMessage(MSG_WRITE_DONE, gatt).sendToTarget();
        }

        private void disconnect(BluetoothGatt gatt) {
            BluetoothDevice dev = gatt.getDevice();
            Log.v(TAG, "Disconnecting from device " + dev.getAddress());
            gatt.disconnect();
        }

        private void close(BluetoothGatt gatt) {
            BluetoothDevice dev = gatt.getDevice();
            Log.v(TAG, "Closing device " + dev.getAddress());
            gatt.close();

            synchronized (mActiveGatt) {
                mActiveGatt.remove(dev.getAddress());
                if (mFutureGattForMsging.size() > 0) {
                    Pair<BluetoothDevice, GattClientCallbackForMessaging> futureInfo = mFutureGattForMsging.remove();
                    mActiveGatt.put(futureInfo.first.getAddress(), futureInfo.first.connectGatt(mService, false, futureInfo.second));
                }
            }
        }

        @Override
        public boolean handleMessage(Message msg) {
            BluetoothGatt gatt = (BluetoothGatt) msg.obj;
            switch (msg.what) {
                case MSG_DISCOVER_SERVICES:
                    gatt.discoverServices();
                    break;
                case MSG_SERVICES_DISCOVERED:
                    for (BluetoothGattService service : gatt.getServices()) {
                        if (service.getUuid().compareTo(SERVICE_UUID) == 0) {
                            // Step 1: Read the DHKEY value from the remote peer (wait for onCharacteristicRead)
                            // TODO: Potentially need to wait until all devices report that services were discovered?
                            // (See bold text in 2nd answer of https://stackoverflow.com/questions/21237093/)
                            if (service.getCharacteristic(CHARACTERISTIC_MESSAGE_UUID) == null) {
                                disconnect(gatt);
                            } else {
                                BluetoothGattCharacteristic c = service.getCharacteristic(CHARACTERISTIC_MESSAGE_UUID);
                                c.setValue(this.msg);
                                gatt.writeCharacteristic(c);
                            }
                        }
                    }
                    break;
                case MSG_WRITE_DONE:
                    disconnect(gatt);
            }
            return true;
        }
    }
}
