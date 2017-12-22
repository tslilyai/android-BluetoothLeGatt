package org.mpisws.sddrservice;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import com.microsoft.embeddedsocial.account.UserAccount;
import com.microsoft.embeddedsocial.autorest.models.Reason;

import org.mpisws.sddrservice.embeddedsocial.ESUser;
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

public class EncountersService implements IEncountersService {
    private static final String TAG = EncountersService.class.getSimpleName();
    private static final EncountersService instance = new EncountersService();

    public static class Filter {
        public Date start_date;
        public Date end_date;
        public Location location;
        public List<String> matches;
        public float distance;
    }

    /*
        Set when the SDDR service is started. No API call other than start_service can be
        made when this boolean is false.
     */
    private boolean isRunning = false;
    private Context context;
    private ESUser esUser;
    private ESMsgs esMsgs;
    private ESNotifs esNotifs;

    public static EncountersService getInstance() {
        return instance;
    }

    @Override
    public void startEncounterService(Context context) {
        Intent serviceIntent = new Intent(context, SDDR_Core_Service.class);
        serviceIntent.putExtra("@string.start_sddr_service", 0);
        context.startService(serviceIntent);

        esUser = new ESUser(context);
        esMsgs = new ESMsgs(context);
        esNotifs = new ESNotifs();

        this.context = context;
        this.isRunning = true;
    }

    @Override
    public void addLinkableID(String linkID) {
        if (!shouldRunCommand()) return;
        Log.v(TAG, "Adding linkID " + linkID);

        Intent serviceIntent = new Intent(context, SDDR_Core_Service.class);
        serviceIntent.putExtra("@string.add_linkid", linkID);
        // note that if we change the mode to listen-only, this is the same as retroactive linking
        serviceIntent.putExtra("Mode", LinkabilityEntryMode.AdvertiseAndListen);
        context.startService(serviceIntent);
    }

    @Override
    public List<Identifier> getEncounters(Filter filter) {
        if (!shouldRunCommand()) return null;

        /* TODO this can be made more efficient? */
        List<MEncounter> encounters = new EncounterBridge(context).getEncountersFiltered(filter);
        List<Identifier> encounterIds = new LinkedList<>();
        for (MEncounter e : encounters) {
            List<Identifier> receivedEids = e.getEncounterIDs(context);
            if (receivedEids.size() > 0)
                encounterIds.add(e.getEncounterIDs(context).get(0));
        }
        return encounterIds;
    }

    @Override
    public void registerGoogleUser(String googletoken, String firstname, String lastname) {
        if (!shouldRunCommand()) return;
        esUser.register_google_user(googletoken, firstname, lastname);
    }

    @Override
    public void signIn() {
        if (!shouldRunCommand()) return;
        esUser.sign_in();
    }

    @Override
    public void signOut() {
        if (!shouldRunCommand()) return;
        esUser.sign_out();
    }

    @Override
    public void sendMsgs(Identifier encounterID, List<String> msgs) {
        if (!shouldRunCommand()) return;
        esMsgs.find_and_act_on_topic(new ESMsgs.TopicAction(
                ESMsgs.TopicAction.TATyp.SendMsg, encounterID.toString(), msgs));
    }

    @Override
    public void getMsgsWithCursor(Identifier encounterID, ESMsgs.MsgCallback callback) {
        if (!shouldRunCommand()) return;
    }

    @Override
    public void getNewMsgs(Identifier encounterID, ESMsgs.MsgCallback callback) {
        if (!shouldRunCommand()) return;
    }

    @Override
    public void reportMsg(ESMsgs.Msg msg, Reason reason) {
        if (!shouldRunCommand()) return;
        esMsgs.report_msg(msg, reason);
    }

    @Override
    public void createEncounterMsgingChannel(Identifier encounterId) {
        if (!shouldRunCommand()) return;
        esMsgs.find_and_act_on_topic(new ESMsgs.TopicAction(
                ESMsgs.TopicAction.TATyp.CreateOnly, encounterId.toString()));
    }

    @Override
    public void getNotifsWithCursor(ESNotifs.NotificationCallback notificationCallback, String cursor) {
        if (!shouldRunCommand()) return;
        esNotifs.get_notifications_from_cursor(notificationCallback, cursor, false/*is_new*/);
    }

    @Override
    public void getNewNotifs(ESNotifs.NotificationCallback notificationCallback) {
        if (!shouldRunCommand()) return;
        esNotifs.get_notifications_from_cursor(notificationCallback, null, true/*is_new*/);
    }

    private boolean shouldRunCommand() {
        return (isRunning && UserAccount.getInstance().isSignedIn());
    }
}
