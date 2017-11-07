package org.mpi_sws.sddr_service;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import org.mpi_sws.sddr_service.linkability.LinkabilityEntryMode;

import java.util.Date;
import java.util.List;

/**
 * Created by tslilyai on 11/6/17.
 */

public class SDDR {
    public class Filter {
        private Date start_date;
        private Date end_date;
        private Location location;
        private List<String> matches;
    }

    private static final String TAG = SDDR.class.getSimpleName();
    private Context context;

    public SDDR(Context context) {
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
        serviceIntent.putExtra("Mode", LinkabilityEntryMode.AdvertiseAndListen); // TODO
        Log.d(TAG, "Adding linkID " + linkID);
        context.startService(serviceIntent);
    }

    public List<String> get_encounters_ids(Filter filter) {
        // TODO this should just work with EncounterBridge directly
        return null;
    }

    public void register() {
        // TODO this should just work with EmbeddedSocial directly
    }

    public void send_msg(String encounterID) {
        // TODO embedded social stuff
    }

    public void send_group_msg(String linkID) {
        // TODO embedded social stuff
    }
}
