package org.mpisws.sddrservice;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import org.mpisws.sddrservice.embedded_social.ESTask;
import org.mpisws.sddrservice.encounterhistory.EncounterBridge;
import org.mpisws.sddrservice.encounterhistory.MEncounter;
import org.mpisws.sddrservice.encounters.SDDR_Core_Service;
import org.mpisws.sddrservice.linkability.LinkabilityEntryMode;

import java.util.Date;
import java.util.List;

import static org.mpisws.sddrservice.embedded_social.ESTask.Typ.*;

/**
 * Created by tslilyai on 11/6/17.
 */

public class SDDR_API {
    private static final String TAG = SDDR_API.class.getSimpleName();
    private static Context context;
    private static int msging_enabled = 0;

    public static class Filter {
        public Date start_date;
        public Date end_date;
        public Location location;
        public List<String> matches;
        public float distance;
    }

    public static void start_service(Context context) {
        SDDR_API.context = context;
        Intent serviceIntent = new Intent(context, SDDR_Core_Service.class);
        serviceIntent.putExtra("@string.start_sddr_service", 0);
        context.startService(serviceIntent);
    }

    public static void add_linkid(String linkID) {
        Intent serviceIntent = new Intent(context, SDDR_Core_Service.class);
        serviceIntent.putExtra("@string.add_linkid", linkID);
        // TODO if we change the mode to listen-only, this is the same as retroactive linking
        serviceIntent.putExtra("Mode", LinkabilityEntryMode.AdvertiseAndListen);
        Log.d(TAG, "Adding linkID " + linkID);
        context.startService(serviceIntent);
    }

    public static List<MEncounter> get_encounters(Filter filter) {
        /* TODO efficiency? + location */
        return new EncounterBridge(context).getEncountersFiltered(filter);
    }

    public static void register_user(String googletoken, String firstname, String lastname) {
        ESTask newTask = new ESTask(LOGIN_GOOGLE);
        newTask.googletoken = googletoken;
        ESTask newTask2 = new ESTask(REGISTER_USER);
        newTask2.firstname = firstname;
        newTask2.lastname = lastname;

        ESTask.addTask(newTask);
        ESTask.addTask(newTask2);
    }

    public static void send_msg(MEncounter encounter, String msg) {
        ESTask newTask = new ESTask(SEND_MSG);
        newTask.msg = msg;
        newTask.encounter = encounter;
        ESTask.addTask(newTask);
    }

    public static void enable_msging() {
        if (msging_enabled > 0) {
            msging_enabled++;
            return;
        }
        ESTask.setAddTopics(true);
    }

    public static void disable_msging() {
        msging_enabled--;
        if (msging_enabled == 0) {
            ESTask.setAddTopics(false);
        }
    }

    public static void get_msgs(ESTask.NotificationCallback callback) {
        ESTask newTask = new ESTask(GET_NOTIFICATIONS);
        newTask.notificationCallback = callback;
        ESTask.addTask(newTask);
    }
}
