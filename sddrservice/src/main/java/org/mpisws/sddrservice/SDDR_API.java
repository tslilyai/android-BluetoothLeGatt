package org.mpisws.sddrservice;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import org.mpisws.sddrservice.embeddedsocial.ESCore;
import org.mpisws.sddrservice.embeddedsocial.ESMsgs;
import org.mpisws.sddrservice.embeddedsocial.ESNotifs;
import org.mpisws.sddrservice.encounterhistory.EncounterBridge;
import org.mpisws.sddrservice.encounterhistory.MEncounter;
import org.mpisws.sddrservice.encounters.SDDR_Core_Service;
import org.mpisws.sddrservice.lib.Identifier;
import org.mpisws.sddrservice.linkability.LinkabilityEntryMode;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by tslilyai on 11/6/17.
 */

public class SDDR_API {
    private static final String TAG = SDDR_API.class.getSimpleName();
    private static Context context;
    private static ESCore esCore;

    /* Set when the SDDR service is started. No API call other than start_service can be
        made when this boolean is false.
     */
    private static boolean isRunning = false;

    public static class Filter {
        public Date start_date;
        public Date end_date;
        public Location location;
        public List<String> matches;
        public float distance;
    }

    public static void start_service(Context context) {
        SDDR_API.context = context;

        // start the service
        Intent serviceIntent = new Intent(context, SDDR_Core_Service.class);
        serviceIntent.putExtra("@string.start_sddr_service", 0);
        context.startService(serviceIntent);
        esCore = new ESCore(context);
        SDDR_API.isRunning = true;
    }

    public static void add_linkid(String linkID) {
        if (!isRunning) return;
        Intent serviceIntent = new Intent(context, SDDR_Core_Service.class);
        serviceIntent.putExtra("@string.add_linkid", linkID);
        // note that if we change the mode to listen-only, this is the same as retroactive linking
        serviceIntent.putExtra("Mode", LinkabilityEntryMode.AdvertiseAndListen);
        Log.v(TAG, "Adding linkID " + linkID);
        context.startService(serviceIntent);
    }

    public static List<Identifier> get_encounters(Filter filter) {
        if (!isRunning) return null;
        /* TODO efficiency? */
        List<MEncounter> encounters = new EncounterBridge(context).getEncountersFiltered(filter);
        List<Identifier> eids = new LinkedList<>();
        for (MEncounter e : encounters) {
            List<Identifier> receivedEids = e.getEncounterIDs(context);
            if (receivedEids.size() > 0)
                eids.add(e.getEncounterIDs(context).get(0));
        }
        return eids;
    }

    public static void register_user(String googletoken, String firstname, String lastname) {
        if (!isRunning) return;
        esCore.register_user_details(googletoken, firstname, lastname);
    }

    public static void sign_in() {
        if (!isRunning) return;
        esCore.sign_in();
    }

    public static void sign_out() {
        if (!isRunning) return;
        esCore.sign_out();
    }

    public static void send_msgs(Identifier encounterID, List<String> msgs) {
        if (!isRunning) return;
        esCore.send_msgs(encounterID.toString(), msgs);
    }

    public static void get_msgs(Identifier encounterID, ESMsgs.MsgCallback callback) {
        if (!isRunning) return;
        esCore.get_msgs(encounterID.toString(), callback);
    }

    public static void enable_msging_channels() {
        if (!isRunning) return;
        esCore.setDefaultCreateMsgChannels();
    }

    public static void disable_msging_channels() {
        if (!isRunning) return;
        esCore.unsetDefaultCreateMsgChannels();
    }

    public static void create_topic(Identifier eid) {
        if (!isRunning) return;
        esCore.create_topic(eid.toString());
    }

    public static void get_notifs(ESNotifs.NotificationCallback notificationCallback, boolean fromBeginning) {
        if (!isRunning) return;
        esCore.get_notifs(notificationCallback, fromBeginning);
    }

    public static void get_msg_of_notification(ESNotifs.Notif notif, ESMsgs.MsgCallback msgCallback) {
        if (!isRunning) return ;
        esCore.get_msg_of_notification(notif, msgCallback);
    }
}
