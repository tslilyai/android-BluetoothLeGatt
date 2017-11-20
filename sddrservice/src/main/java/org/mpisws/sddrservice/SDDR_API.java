package org.mpisws.sddrservice;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import org.mpisws.sddrservice.embedded_social.Tasks;
import org.mpisws.sddrservice.encounterhistory.EncounterBridge;
import org.mpisws.sddrservice.encounterhistory.MEncounter;
import org.mpisws.sddrservice.encounters.SDDR_Core_Service;
import org.mpisws.sddrservice.linkability.LinkabilityEntryMode;

import java.util.Date;
import java.util.List;

import static org.mpisws.sddrservice.embedded_social.Tasks.TaskTyp.REGISTER_USER;
import static org.mpisws.sddrservice.embedded_social.Tasks.TaskTyp.SEND_MSG;
import static org.mpisws.sddrservice.embedded_social.Tasks.TaskTyp.SIGNOUT_USER;

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

    public SDDR_API(Context context) {
        this.context = context;
    }

    public void start_service() {
        Intent serviceIntent = new Intent(context, SDDR_Core_Service.class);
        serviceIntent.putExtra("@string.start_sddr_service", 0);
        context.startService(serviceIntent);
    }

    public void add_linkid(String linkID) {
        Intent serviceIntent = new Intent(context, SDDR_Core_Service.class);
        serviceIntent.putExtra("@string.add_linkid", linkID);
        // TODO if we change the mode to listen-only, this is the same as retroactive linking
        serviceIntent.putExtra("Mode", LinkabilityEntryMode.AdvertiseAndListen);
        Log.d(TAG, "Adding linkID " + linkID);
        context.startService(serviceIntent);
    }

    public List<MEncounter> get_encounters(Filter filter) {
        /* TODO efficiency? + location */
        return new EncounterBridge(context).getEncountersFiltered(filter);
    }

    public void register_user(String firstname, String lastname) {
        Tasks.TaskTyp newTaskTyp = REGISTER_USER;
        newTaskTyp.firstname = firstname;
        newTaskTyp.lastname = lastname;
        Tasks.addTask(newTaskTyp);
    }

    public void signout_user() {
        Tasks.TaskTyp newTaskTyp = SIGNOUT_USER;
        Tasks.addTask(newTaskTyp);
    }

    public void send_msg(MEncounter encounter, String msg) {
        Tasks.TaskTyp newTaskTyp = SEND_MSG;
        newTaskTyp.msg = msg;
        newTaskTyp.encounter = encounter;
        Tasks.addTask(newTaskTyp);
    }

    public void enable_msging() {
        if (msging_enabled > 0) {
            msging_enabled++;
            return;
        }
        Tasks.setAddTopics(true);
    }

    public void disable_msging() {
        msging_enabled--;
        if (msging_enabled == 0) {
            Tasks.setAddTopics(false);
        }
    }
}
