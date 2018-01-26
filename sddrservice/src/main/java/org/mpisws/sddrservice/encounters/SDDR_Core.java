package org.mpisws.sddrservice.encounters;

/**
 * Created by tslilyai on 10/16/17.
 */

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Vibrator;
import android.util.Log;
import android.util.Pair;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.mpisws.sddrservice.EncountersService;
import org.mpisws.sddrservice.embeddedsocial.ESAdvertTopics;
import org.mpisws.sddrservice.encounterhistory.ConfirmEncounterEvent;
import org.mpisws.sddrservice.encounterhistory.EncounterEndedEvent;
import org.mpisws.sddrservice.encounterhistory.EncounterEvent;
import org.mpisws.sddrservice.encounterhistory.EncounterStartedEvent;
import org.mpisws.sddrservice.encounterhistory.EncounterUpdatedEvent;
import org.mpisws.sddrservice.encounterhistory.MyAdvertsBridge;
import org.mpisws.sddrservice.encounterhistory.NewAdvertsBridge;
import org.mpisws.sddrservice.encounterhistory.RSSIEntry;
import org.mpisws.sddrservice.lib.Constants;
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

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private Advertiser mAdvertiser;
    private Scanner mScanner;
    private long changeEpochTime;
    private static Context mService;
    public static Context appContext ;
    protected static GattServer gattServer;
    public static boolean confirmEncounters = false;
    //private Sleeper sleeper;

    public boolean should_run;
    public int numNewEncounters;

    SDDR_Core(SDDR_Core_Service service, Context appContext) {
        this.mService = service;
        this.appContext = appContext;
        //sleeper = new Sleeper(mService);
        initialize();
    }

    private void initialize() {
        Log.v(TAG, "onCreate");
        this.bluetoothManager = (BluetoothManager) mService.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mAdvertiser = new Advertiser();
        mScanner = new Scanner(mService);

        // REMOVE ME
        mAdvertiser.setConnectable(true);
        confirmEvents = new ConcurrentLinkedQueue();
        gattServer = new GattServer(bluetoothManager, appContext);
        mScanner.serverRunning = true;

        // initialize the C radio class
        Log.v(TAG, "Initializing radio");
        SDDR_Native.c_mallocRadio();

        mAdvertiser.initialize(mBluetoothAdapter);
        mScanner.initialize(mBluetoothAdapter, this);

        numNewEncounters = 0;
        changeEpochTime = System.currentTimeMillis() + Constants.CHANGE_EPOCH_TIME + 1000;
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
        if (gattServer == null)
            gattServer = new GattServer(bluetoothManager, appContext);
        mScanner.serverRunning = true;
        mAdvertiser.setConnectable(true);
        mAdvertiser.resetAdvertiser();
        confirmEvents = new ConcurrentLinkedQueue();
    }
    protected void stopServerActiveConnections() {
        mScanner.serverRunning = false;
        mAdvertiser.setConnectable(false);
        //gattServer = null;
        confirmEvents = null;
    }

    public void run() {
        Log.v(TAG, "Running core");
        /*Random ran = new Random(System.currentTimeMillis());
        //while(true) {
                sleeper.sleep(30000);
                Log.d("TOPICS_TEST", System.currentTimeMillis() + ": VIBRATING!");
                Log.d("TOPICS_TEST", System.currentTimeMillis() + ": SLEEPING (10 seconds)");
                for (int i = 0; i < 1000; i++)
                    ack(BigInteger.valueOf(3), BigInteger.valueOf(3));
                try
                {
                    Thread.sleep(1000);
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
                Log.d("TOPICS_TEST", System.currentTimeMillis() + ": CREATING " + ESTopics.NUM_TOPICS_TO_CREATE + " topics");
                for (int i = 0; i < ESTopics.NUM_TOPICS_TO_CREATE; ++i)
                {
                    Log.d("TOPICS_TEST", "\tCreate channel named " + String.valueOf(Math.abs(ran.nextInt())));
                    EncountersService.getInstance().createEncounterMsgingChannel(String.valueOf(Math.abs(ran.nextInt())));
                }
            //}
        /*
        };
        for (int i = 0; i < 1000; i++)
            handler.postDelayed(r, i*40000);
        while(true) {
            updateInformation();
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }*/
        updateInformation();
        mScanner.startScanning();
    }

    public void stop(){
        SDDR_Native.c_freeRadio();
        mAdvertiser.stopAdvertising();
        mScanner.stopScanning();
    }

    public void postScanProcessing() {
        processEncounters(mService);
        if (changeEpochTime < System.currentTimeMillis()) {
            Log.d(TAG, "Changing EPOCH");
            SDDR_Native.c_changeEpoch();
            updateInformation();
            changeEpochTime += Constants.CHANGE_EPOCH_TIME;

            if (confirmEncounters && EncountersService.getInstance().isSignedIn()) {
                Log.d(TAG, "Confirming encounters");
                // create topics for adverts and post the DHPubKey on them if we haven't yet
                List<Pair<Identifier, Identifier>> adverts = new MyAdvertsBridge(mService).getMyUnpostedAdverts();
                for (Pair<Identifier, Identifier> pair : adverts) {
                    Log.d(TAG, "Create topic for advert and dhpubkey " + pair.first.toString() + ", " + pair.second.toString());
                    ESAdvertTopics.postAdvertAndDHPubKey(mService, pair.first, pair.second);
                }
                List<NewAdvertsBridge.NewAdvertData> advertsToConfirm = new NewAdvertsBridge(mService).getMyUnconfirmedAdverts();
                for (NewAdvertsBridge.NewAdvertData advert : advertsToConfirm) {
                    Log.d(TAG, "Confirm encounter " + advert.epkid);
                    ESAdvertTopics.tryToComputeSecret(mService, advert.myDHKey, advert.epkid, advert.advert);
                }
            }
        }
    }

    private void processEncounters(Context mService) {
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

            encEvent.broadcast(mService);
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