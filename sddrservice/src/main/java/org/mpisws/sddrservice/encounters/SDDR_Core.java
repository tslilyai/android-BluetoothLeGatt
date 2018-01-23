package org.mpisws.sddrservice.encounters;

/**
 * Created by tslilyai on 10/16/17.
 */

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.mpisws.sddrservice.EncountersService;
import org.mpisws.sddrservice.encounterhistory.ConfirmEncounterEvent;
import org.mpisws.sddrservice.encounterhistory.EncounterBridge;
import org.mpisws.sddrservice.encounterhistory.EncounterEndedEvent;
import org.mpisws.sddrservice.encounterhistory.EncounterEvent;
import org.mpisws.sddrservice.encounterhistory.EncounterStartedEvent;
import org.mpisws.sddrservice.encounterhistory.EncounterUpdatedEvent;
import org.mpisws.sddrservice.encounterhistory.MyAdvertsBridge;
import org.mpisws.sddrservice.encounterhistory.RSSIEntry;
import org.mpisws.sddrservice.lib.Identifier;
import org.mpisws.sddrservice.lib.Sleeper;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * SDDR_Core implements the core functionality of the SDDR protocol. It is started and called by the
 * SDDR_Core_Service. The functionalities it provides are:
 * - running discovery
 * - setting advert information
 * - processing and storing encounter information in persistent storage
 */
public class SDDR_Core implements Runnable {
    private static final String TAG = SDDR_Core.class.getSimpleName();
    protected static Identifier mDHPubKey;
    protected static Identifier mAdvert;
    protected static Identifier mDHKey;
    public static ConcurrentLinkedQueue<ConfirmEncounterEvent> confirmEvents;

    private SDDR_Core_Service mService;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private Advertiser mAdvertiser;
    private Scanner mScanner;
    private Sleeper mSleeper;
    protected static GattServerClient mGattServerClient;

    public boolean should_run;
    public int numNewEncounters;

    /* fields set by C functions and pointers */
    static public class RadioAction {
        enum actionType {
            ChangeEpoch,
            Discover
        }

        long duration;
        actionType type;

        RadioAction(actionType typ, long dur) {
            type = typ;
            duration = dur;
        }
    }

    private RadioAction mRA;

    SDDR_Core(SDDR_Core_Service service) {
        mService = service;
        initialize();
    }
    private void updateInformation() {
        mDHPubKey = new Identifier(SDDR_Native.c_getMyDHPubKey());
        mDHKey = new Identifier(SDDR_Native.c_getMyDHKey());
        mAdvert = new Identifier(SDDR_Native.c_getMyAdvert());
        mAdvertiser.setAddr(SDDR_Native.c_getRandomAddr());
        mAdvertiser.setAdData(mAdvert.getBytes());
        mAdvertiser.resetAdvertiser();
        new MyAdvertsBridge(mService).insertMyAdvert(mAdvert, mDHPubKey);
    }
    protected void startServerAndActivelyConnect() {
        if (mGattServerClient == null)
            mGattServerClient = new GattServerClient(bluetoothManager, mService);
        mScanner.serverRunning = true;
        mAdvertiser.setConnectable(true);
        mAdvertiser.resetAdvertiser();
        confirmEvents = new ConcurrentLinkedQueue();
    }
    protected void stopServerActiveConnections() {
        mScanner.serverRunning = false;
        mAdvertiser.setConnectable(false);
        mGattServerClient = null;
        confirmEvents = null;
    }

    public void initialize() {
        Log.v(TAG, "onCreate");
        this.bluetoothManager = (BluetoothManager) mService.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mAdvertiser = new Advertiser();
        mScanner = new Scanner(mService);
        mSleeper = new Sleeper(mService);

        // initialize the C radio class
        Log.v(TAG, "Initializing radio");
        SDDR_Native.c_mallocRadio();

        mAdvertiser.initialize(mBluetoothAdapter);
        mScanner.initialize(mBluetoothAdapter);

        numNewEncounters = 0;
    }

    public void run() {
        Log.v(TAG, "Running core");
        updateInformation();

        should_run = true;
        while (should_run) {
            mRA = SDDR_Native.c_getNextRadioAction();
            if (mRA.duration > 0) {
                Log.v(TAG, "sleeping for " + mRA.duration);
                mSleeper.sleep(mRA.duration);
            } else {
                Log.v(TAG, "executing action immediately");
            }
            switch (mRA.type) {
                case ChangeEpoch:
                    Log.v(TAG, "Changing Epoch");
                    SDDR_Native.c_changeEpoch();
                    updateInformation();
                    break;
                case Discover:
                    Log.v(TAG, "Performing Discovery");
                    mScanner.discoverEncounters();
                    processEncounters(mService);
                    break;
                default:
                    throw new RuntimeException("Unknown Action Type");
            }
        }

        // cleanup
        SDDR_Native.c_freeRadio();
        mAdvertiser.stopAdvertising();
        mScanner.stopScanning();
    }


    public void processEncounters(Context context) {
        Log.v(TAG, "Processing " + SDDR_Native.c_EncounterMsgs.size() + " encounters");

        for (Iterator<byte[]> iterator = SDDR_Native.c_EncounterMsgs.iterator(); iterator.hasNext();) {
            byte[] msg = iterator.next();
            final SDDR_Proto.Event event;

            try {
                event = SDDR_Proto.Event.parseFrom(msg);
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
                return;
            }

            assert (event.hasEncounterEvent());
            final SDDR_Proto.Event.EncounterEvent subEvent = event.getEncounterEvent();
            final SDDR_Proto.Event.EncounterEvent.EventType type = subEvent.getType();
            final long time = subEvent.getTime();
            final long pkid = subEvent.getPkid();
            final String address = subEvent.getAddress();
            final List<SDDR_Proto.Event.EncounterEvent.RSSIEvent> rssiEventsPB = subEvent.getRssiEventsList();
            final List<ByteString> advertsPB = subEvent.getSharedSecretsList();

            // Transforming the lists into the appropriate Java structures for EncounterEvent
            final List<RSSIEntry> rssiEvents = new LinkedList<>();
            for (SDDR_Proto.Event.EncounterEvent.RSSIEvent rssiEvent : rssiEventsPB) {
                rssiEvents.add(new RSSIEntry(rssiEvent.getTime(), rssiEvent.getRssi()));
            }

            final List<Identifier> adverts = new LinkedList<>();
            for (com.google.protobuf.ByteString secret : advertsPB) {
                adverts.add(new Identifier(secret.toByteArray()));
            }

            EncounterEvent encEvent = null;
            switch (type) {
                case UnconfirmedStart: // brand new unconfirmed
                    numNewEncounters++;
                    encEvent = new EncounterStartedEvent(pkid, time, adverts, mAdvert, mDHPubKey, mDHKey);
                    Log.v(TAG, "[EncounterEvent] Tentative encounter started at " + time);
                    break;
                case Update: // updated
                    encEvent = new EncounterUpdatedEvent(pkid, time, adverts, rssiEvents, address);
                    Log.v(TAG, "[EncounterEvent] Encounter Updated at " + time);
                    break;
                case End: // ended
                    encEvent = new EncounterEndedEvent(pkid, time, rssiEvents, address);
                    Log.v(TAG, "[EncounterEvent] Encounter Ended at " + time);
                    break;
                default:
                    throw new IllegalStateException("Unknown encounter event: " + type);
            }

            Log.v(TAG, "\tPKID = " + pkid + ", Address = " + address);
            Log.v(TAG, "\tNew adverts: ");
            for (Identifier i : adverts) {
                Log.v(TAG, "\t\t" + i.toString());
            }

            encEvent.broadcast(context);
            iterator.remove();
        }

        if (numNewEncounters >= EncountersService.BUFFERED_MESSAGES_THRESHOLD) {
            EncountersService.getInstance().sendRepeatingBroadcastMessages();
            numNewEncounters = 0;
        }
        if (confirmEvents != null) {
            ConfirmEncounterEvent ce = confirmEvents.poll();
                while (ce != null) {
                ce.broadcast(mService);
                ce = confirmEvents.poll();
            }
        }
    }
}