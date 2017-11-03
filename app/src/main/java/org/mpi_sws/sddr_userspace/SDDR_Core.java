package org.mpi_sws.sddr_userspace;

/**
 * Created by tslilyai on 10/16/17.
 */

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.mpi_sws.sddr_userspace.encounterhistory.EncounterBridge;
import org.mpi_sws.sddr_userspace.encounterhistory.EncounterEndedEvent;
import org.mpi_sws.sddr_userspace.encounterhistory.EncounterEvent;
import org.mpi_sws.sddr_userspace.encounterhistory.EncounterStartedEvent;
import org.mpi_sws.sddr_userspace.encounterhistory.EncounterUpdatedEvent;
import org.mpi_sws.sddr_userspace.encounterhistory.MEncounter;
import org.mpi_sws.sddr_userspace.encounterhistory.RSSIEntry;
import org.mpi_sws.sddr_userspace.linkability.LinkabilityBridge;
import org.mpi_sws.sddr_userspace.linkability.LinkabilityEntryMode;
import org.mpi_sws.sddr_userspace.linkability.MLinkabilityEntry;
import org.mpi_sws.sddr_userspace.lib.Identifier;
import org.mpi_sws.sddr_userspace.lib.NotFoundException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class SDDR_Core implements Runnable {
    private static final String TAG = "SDDR: " + SDDR_Core.class.getSimpleName();
    private SDDR_Core_Service mService;
    private BluetoothAdapter mBluetoothAdapter;
    private Advertiser mAdvertiser;
    private Scanner mScanner;
    private Sleeper mSleeper;
    private LinkabilityBridge mLinkBridge;
    private EncounterBridge mEncounterBridge;
    private List<MLinkabilityEntry> mLinks;

    public boolean should_run;

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

    private void updateAddr() {
        mAdvertiser.setAddr(SDDR_Native.c_getRandomAddr());
    }

    private void updateAdvert() {
        Log.d(TAG, "Updating Advert");
        mAdvertiser.setAdData(SDDR_Native.c_changeAndGetAdvert());
        mAdvertiser.resetAdvertiser();
    }

    public void initialize() {
        Log.d(TAG, "onCreate");
        final BluetoothManager bluetoothManager = (BluetoothManager) mService.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mAdvertiser = new Advertiser();
        mScanner = new Scanner();
        mSleeper = new Sleeper(mService);
        mLinkBridge = new LinkabilityBridge(mService);
        mEncounterBridge = new EncounterBridge(mService);

         // initialize the C radio class
         Log.d(TAG, "Initializing radio");
         SDDR_Native.c_mallocRadio();
         mAdvertiser.initialize(mBluetoothAdapter);
         mScanner.initialize(mBluetoothAdapter, mService);

        // initialize the databases for encounters and links
        mEncounterBridge.finalizeAbandonedEncounters();
        mLinks = mLinkBridge.getAllItems();
        updateBTListenAdvertiseSets(mLinks);
    }

    public void run() {
        // set the initial UUIDs and advert data
        updateAddr();
        updateAdvert();

        // enable advertising
        mAdvertiser.startAdvertising();

        should_run = true;
        while (should_run) {
            mRA = SDDR_Native.c_getNextRadioAction();
            if (mRA.duration > 0) {
                Log.d(TAG, "sleeping for " + mRA.duration);
                mSleeper.sleep(mRA.duration);
            } else {
                Log.d(TAG, "executing action immediately");
            }

            switch (mRA.type) {
                case ChangeEpoch:
                    Log.d(TAG, "Changing Epoch");
                    SDDR_Native.c_changeEpoch();
                    updateAddr();
                    break;
                case Discover:
                    Log.d(TAG, "Performing Discovery");
                    updateAdvert();
                    mScanner.discoverAndProcessEncounters();
                    /*
                        TODO Deal with active/hybrid schemes
                        TODO Deal with different levels of connectivity due to hybrid/active
                    */
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

    public void updateEncounterMatchings() {
        List<MEncounter> encounters = mEncounterBridge.getAllItems();
        for (MEncounter e : encounters) {
            Log.d(TAG, "Retroactive matching for encounter " + e.getPKID());
            e.updateEncounterMatchings(mService);
        }
    }

    public void addNewLink(Identifier id, LinkabilityEntryMode mode) {
        addNewLinkabilityEntryIfAbsent(new MLinkabilityEntry(null, id, id.toString(), mode, 0));
        mLinks = mLinkBridge.getAllItems();
        updateBTListenAdvertiseSets(mLinks);
    }

    private void updateBTListenAdvertiseSets(final List<MLinkabilityEntry> entries) {
        final List<SDDR_Proto.Event.LinkabilityEvent.Entry> eventEntries = new LinkedList<>();

        /* PROTOBUF STUFF */
        for (final MLinkabilityEntry entry : entries) {
            final SDDR_Proto.Event.LinkabilityEvent.Entry.Builder entryBuilder =
                    SDDR_Proto.Event.LinkabilityEvent.Entry.newBuilder();
            if (entry.getMode() == LinkabilityEntryMode.ListenOnly) {
                entryBuilder.setMode(SDDR_Proto.Event.LinkabilityEvent.Entry.ModeType.Listen);
            } else if (entry.getMode() == LinkabilityEntryMode.AdvertiseAndListen) {
                entryBuilder.setMode(SDDR_Proto.Event.LinkabilityEvent.Entry.ModeType.AdvertAndListen);
            }
            eventEntries.add(entryBuilder.setLinkValue(ByteString.copyFrom(entry.getIdValue().getBytes())).build());
            Log.d(TAG, "Adding linkability entry with ID " + entry.getIdValue() + " to listen/advertise set");
        }
        final SDDR_Proto.Event event = SDDR_Proto.Event.newBuilder().
                setLinkabilityEvent(SDDR_Proto.Event.LinkabilityEvent.newBuilder().
                        addAllEntries(eventEntries).build()).build();
        /* END PROTOBUF */

        SDDR_Native.c_updateLinkability(event.toByteArray());
    }

    private boolean addNewLinkabilityEntryIfAbsent(MLinkabilityEntry entry) {
        try {
            // TODO this is pretty inefficient
            mLinkBridge.getPKIDForIDValue(entry.getIdValue());
            Log.d(TAG, "ID " + entry.getIdValue() + " already in database");
            return false;
        } catch (NotFoundException e) {
            Log.d(TAG, "ID " + entry.getIdValue() + " not found, adding to database");
            mLinkBridge.addItem(entry);
            return true;
        }
    }

    protected void processEncounters() {
        Log.d(TAG, "Processing encounters");

        for (byte[] msg : SDDR_Native.c_EncounterMsgs) {
            final SDDR_Proto.Event event;

            try {
                event = SDDR_Proto.Event.parseFrom(msg);
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
                return;
            }

            assert(event.hasEncounterEvent());
            final SDDR_Proto.Event.EncounterEvent subEvent = event.getEncounterEvent();
            final SDDR_Proto.Event.EncounterEvent.EventType type = subEvent.getType();
            final long time = subEvent.getTime();
            final long pkid = subEvent.getPkid();
            final boolean matchingSetUpdated = subEvent.getMatchingSetUpdated();
            final String address = subEvent.getAddress();
            final List<SDDR_Proto.Event.EncounterEvent.RSSIEvent> rssiEventsPB = subEvent.getRssiEventsList();
            final List<ByteString> matchingSetPB = subEvent.getMatchingSetList();
            final List<ByteString> sharedSecretsPB = subEvent.getSharedSecretsList();

            List<SDDR_Proto.Event.RetroactiveInfo.BloomInfo> bloomsPB = null;
            if (event.hasRetroactiveInfo())
                bloomsPB = event.getRetroactiveInfo().getBloomsList();

            // Transforming the lists into the appropriate Java structures for EncounterEvent
            final List<RSSIEntry> rssiEvents = new LinkedList<>();
            for (SDDR_Proto.Event.EncounterEvent.RSSIEvent rssiEvent : rssiEventsPB) {
                rssiEvents.add(new RSSIEntry(rssiEvent.getTime(), rssiEvent.getRssi()));
            }

            List<Identifier> matchingSet;
            if (matchingSetUpdated) {
                matchingSet = new LinkedList<>();
                for (com.google.protobuf.ByteString matching : matchingSetPB) {
                    matchingSet.add(new Identifier(matching.toByteArray()));
                }
            } else {
                matchingSet = null;
            }

            final List<Identifier> sharedSecrets = new LinkedList<>();
            for (com.google.protobuf.ByteString secret : sharedSecretsPB) {
                sharedSecrets.add(new Identifier(secret.toByteArray()));
            }

            final List<SDDR_Proto.Event.RetroactiveInfo.BloomInfo> blooms = new LinkedList<>();
            for (SDDR_Proto.Event.RetroactiveInfo.BloomInfo bloom : bloomsPB) {
                blooms.add(bloom);
            }

            EncounterEvent encEvent = null;
            switch (type) {
                case UnconfirmedStart: // brand new unconfirmed
                    encEvent = new EncounterStartedEvent(pkid, time);
                    Log.v(TAG, "[EncounterEvent] Tentative encounter started at " + time);
                    break;
                case Start:
                    if (mEncounterBridge.getItemByPKID(pkid) == null) {
                        // brand new confirmed from incoming connection, TODO get from native instead of DB
                        Log.v(TAG, "[EncounterEvent] Already confirmed encounter started at " + time);
                        encEvent = new EncounterStartedEvent(pkid, time, rssiEvents, sharedSecrets, blooms, matchingSet, address,
                                System.currentTimeMillis());
                    } else { // previously unconfirmed becomes confirmed
                        Log.v(TAG, "[EncounterEvent] Encounter confirmed at " + time);
                        encEvent = new EncounterUpdatedEvent(pkid, time, rssiEvents, sharedSecrets, blooms, matchingSet, address, System.currentTimeMillis());
                    }

                    break;
                case Update: // updated
                    encEvent = new EncounterUpdatedEvent(pkid, time, rssiEvents, sharedSecrets, blooms, matchingSet, address, null);
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
            Log.v(TAG, "\tNew SharedSecrets: ");
            for (Identifier i : sharedSecrets) {
                Log.v(TAG, "\t\t" + i.toString());
            }

            if (matchingSet != null) {
                Log.v(TAG, "\tCurrent Matching Set: ");
                for (Identifier i : matchingSet) {
                    Log.v(TAG, "\t\t" + i.toString());
                }
            } else {
                Log.v(TAG, "\tCurrent Matching Set: same as before (no update since the last report)");
            }

            Log.v(TAG, "\tRSSI Measurements: ");
            for (RSSIEntry e : rssiEvents) {
                Log.v(TAG, "\t\t" + e.getRssi() + " at time " + e.getTimestamp());
            }

            encEvent.broadcast(mService);
        }

        // reset so we don't broadcast encounters more than once
        SDDR_Native.c_EncounterMsgs.clear();
    }
}