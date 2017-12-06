package org.mpisws.sddrservice;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import org.mpisws.sddrservice.embeddedsocial.ESNotifs;
import org.mpisws.sddrservice.embeddedsocial.ESTask;
import org.mpisws.sddrservice.encounterhistory.EncounterBridge;
import org.mpisws.sddrservice.encounterhistory.MEncounter;
import org.mpisws.sddrservice.encounters.SDDR_Core_Service;
import org.mpisws.sddrservice.lib.Identifier;
import org.mpisws.sddrservice.linkability.LinkabilityEntryMode;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import static org.mpisws.sddrservice.embeddedsocial.ESTask.Typ.*;

/**
 * Created by tslilyai on 11/6/17.
 */

public class SDDR_API {
    private static final String TAG = SDDR_API.class.getSimpleName();
    private static Context context;
    private static int msging_enabled = 0;
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
        ESTask.initialize_static_vars();

        // start the service
        Intent serviceIntent = new Intent(context, SDDR_Core_Service.class);
        serviceIntent.putExtra("@string.start_sddr_service", 0);
        context.startService(serviceIntent);
        isRunning = true;
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
            eids.add(e.getEncounterIDs(context).get(0));
        }
        return eids;
    }

    public static void register_user(String googletoken, String firstname, String lastname) {
        if (!isRunning) return;
        ESTask newTask1 = new ESTask(LOGIN_GOOGLE);
        newTask1.googletoken = googletoken;
        ESTask.addTask(newTask1);

        ESTask newTask2 = new ESTask(REGISTER_USER);
        newTask2.firstname = firstname;
        newTask2.lastname = lastname;
        ESTask.addTask(newTask2);
    }

    public static void send_msg(Identifier encounterID, String msg) {
        if (!isRunning) return;
        ESTask newTask = new ESTask(SEND_MSG);
        newTask.msg = msg;
        newTask.encounterID = encounterID;
        ESTask.addTask(newTask);
    }

    public static void get_msgs(Identifier encounterID, ESTask.MsgsCallback callback) {
        if (!isRunning) return;
        ESTask newTask = new ESTask(GET_MSGS);
        newTask.encounterID = encounterID;
        newTask.msgsCallback = callback;
        ESTask.addTask(newTask);
    }

    public static void enable_msging() {
        if (!isRunning) return;
        if (msging_enabled > 0) {
            msging_enabled++;
            return;
        }
        ESTask newTask = new ESTask(MESSAGING_ON_DEFAULT);
        ESTask.addTask(newTask);
    }

    public static void disable_msging() {
        if (!isRunning) return;
        msging_enabled--;
        if (msging_enabled == 0) {
            ESTask newTask = new ESTask(MESSAGING_OFF_DEFAULT);
            ESTask.addTask(newTask);
        }
    }

    public static void get_notifs(ESTask.NotificationCallback callback) {
        if (!isRunning) return;
        ESTask newTask = new ESTask(GET_NOTIFICATIONS);
        newTask.notificationCallback = callback;
        ESTask.addTask(newTask);
    }
}
