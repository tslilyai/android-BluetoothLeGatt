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
import org.mpisws.sddrservice.embeddedsocial.ESTopics;
import org.mpisws.sddrservice.encounterhistory.EncounterBridge;
import org.mpisws.sddrservice.encounterhistory.EncounterEndedEvent;
import org.mpisws.sddrservice.encounterhistory.EncounterEvent;
import org.mpisws.sddrservice.encounterhistory.EncounterStartedEvent;
import org.mpisws.sddrservice.encounterhistory.EncounterUpdatedEvent;
import org.mpisws.sddrservice.encounterhistory.MEncounter;
import org.mpisws.sddrservice.encounterhistory.RSSIEntry;
import org.mpisws.sddrservice.lib.Identifier;
import org.mpisws.sddrservice.lib.Sleeper;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * SDDR_Core implements the core functionality of the SDDR protocol. It is started and called by the
 * SDDR_Core_Service. The functionalities it provides are:
 * - running discovery
 * - setting advert information
 * - processing and storing encounter information in persistent storage
 */
public class SDDR_Core implements Runnable {
    private static final String TAG = SDDR_Core.class.getSimpleName();
    private SDDR_Core_Service mService;
    private BluetoothAdapter mBluetoothAdapter;
    private Advertiser mAdvertiser;
    private Scanner mScanner;
    private Sleeper mSleeper;
    private EncounterBridge mEncounterBridge;
    private byte[] mDHKey;
    private byte[] mAdvert;

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
        mDHKey = SDDR_Native.c_getMyDHKey();
        mAdvert = SDDR_Native.c_getMyAdvert();
        mAdvertiser.setAddr(SDDR_Native.c_getRandomAddr());
        mAdvertiser.setAdData(mAdvert);
        mAdvertiser.resetAdvertiser();
    }

    public void initialize() {
        Log.v(TAG, "onCreate");
        final BluetoothManager bluetoothManager = (BluetoothManager) mService.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mAdvertiser = new Advertiser();
        mScanner = new Scanner();
        mSleeper = new Sleeper(mService);
        mEncounterBridge = new EncounterBridge(mService);

        // initialize the C radio class
        Log.v(TAG, "Initializing radio");
        SDDR_Native.c_mallocRadio();
        mAdvertiser.initialize(mBluetoothAdapter);
        mScanner.initialize(mBluetoothAdapter);

        // initialize the databases for encounters and links
        mEncounterBridge.finalizeAbandonedEncounters();
        numNewEncounters = 0;
    }

    public void run() {
        Log.v(TAG, "Running core");
        updateInformation();

        // enable advertising
        mAdvertiser.stopAdvertising();
        mAdvertiser.startAdvertising();

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

    protected void getUnconfirmedEncountersSharedSecrets() {
        List<MEncounter> encounters = new EncounterBridge(mService).getEncountersUnconfirmed();
        for (MEncounter encounter : encounters) {
            List<Identifier> adverts = encounter.getAdverts(mService);
            for (Identifier advert : adverts) {
                new ESTopics(mService).find_and_act_on_topic(
                        new ESTopics.TopicAction(ESTopics.TopicAction.TATyp.CreateAdvertTopic, advert.toString(), mDHKey, encounter.getPKID()));
            }
        }
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
                    encEvent = new EncounterStartedEvent(pkid, time);
                    Log.v(TAG, "[EncounterEvent] Tentative encounter started at " + time);
                    break;
                case Start:
                    numNewEncounters++;
                    if (new EncounterBridge(context).getItemByPKID(pkid) == null) {
                        // brand new confirmed from incoming connection, TODO get from native instead of DB
                        Log.v(TAG, "[EncounterEvent] Already confirmed encounter started at " + time);
                        encEvent = new EncounterStartedEvent(pkid, time, rssiEvents, address);
                    } else { // previously unconfirmed becomes confirmed
                        Log.v(TAG, "[EncounterEvent] Encounter confirmed at " + time);
                        encEvent = new EncounterUpdatedEvent(pkid, time, adverts, rssiEvents, address);
                    }

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

            encEvent.setMyAdvert(mAdvert);
            encEvent.broadcast(context);
            iterator.remove();
        }

        if (numNewEncounters >= EncountersService.BUFFERED_MESSAGES_THRESHOLD) {
            EncountersService.getInstance().sendRepeatingBroadcastMessages();
            numNewEncounters = 0;
        }
    }
}