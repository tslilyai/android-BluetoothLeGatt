package org.mpisws.sddrservice;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import com.microsoft.embeddedsocial.account.UserAccount;
import com.microsoft.embeddedsocial.autorest.models.Reason;

import org.mpisws.sddrservice.embeddedsocial.ESMsgs;
import org.mpisws.sddrservice.embeddedsocial.ESNotifs;
import org.mpisws.sddrservice.embeddedsocial.ESUser;
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
    private EncountersService(){};

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
        if (!shouldRunCommand(true)) return;
        Log.v(TAG, "Adding linkID " + linkID);

        Intent serviceIntent = new Intent(context, SDDR_Core_Service.class);
        serviceIntent.putExtra("@string.add_linkid", linkID);
        // note that if we change the mode to listen-only, this is the same as retroactive linking
        serviceIntent.putExtra("Mode", LinkabilityEntryMode.AdvertiseAndListen);
        context.startService(serviceIntent);
    }

    @Override
    public List<String> getEncounters(Filter filter) {
        if (!shouldRunCommand(true)) return null;

        /* TODO this can be made more efficient? */
        List<MEncounter> encounters = new EncounterBridge(context).getEncountersFiltered(filter);
        List<String> encounterIds = new LinkedList<>();
        for (MEncounter e : encounters) {
            List<Identifier> receivedEids = e.getEncounterIDs(context);
            if (receivedEids.size() > 0)
                encounterIds.add(e.getEncounterIDs(context).get(0).toString());
        }
        return encounterIds;
    }

    @Override
    public void registerGoogleUser(String googletoken, String firstname, String lastname) {
        if (!shouldRunCommand(false)) return;
        esUser.register_google_user(googletoken, firstname, lastname);
    }

    @Override
    public void signIn() {
        if (!shouldRunCommand(false)) return;
        esUser.sign_in();
    }

    @Override
    public void signOut() {
        if (!shouldRunCommand(true)) return;
        esUser.sign_out();
    }

    @Override
    public void sendMsgs(String encounterID, List<String> msgs) {
        if (!shouldRunCommand(true)) return;
        esMsgs.find_and_act_on_topic(new ESMsgs.TopicAction(
                ESMsgs.TopicAction.TATyp.SendMsg, encounterID, msgs));
    }

    @Override
    public void getMsgsWithCursor(String encounterID, ESMsgs.GetMessagesCallback callback, String cursor) {
        if (!shouldRunCommand(true)) return;
        esMsgs.find_and_act_on_topic(new ESMsgs.TopicAction(
                ESMsgs.TopicAction.TATyp.GetMsgs, encounterID, cursor, false /*is_new*/, callback));
    }

    @Override
    public void getNewMsgs(String encounterID, ESMsgs.GetMessagesCallback callback) {
        if (!shouldRunCommand(true)) return;
        esMsgs.find_and_act_on_topic(new ESMsgs.TopicAction(
                ESMsgs.TopicAction.TATyp.GetMsgs, encounterID, null, true /*is_new*/, callback));
    }

    @Override
    public void reportMsg(ESMsgs.Msg msg, Reason reason) {
        if (!shouldRunCommand(true)) return;
        esMsgs.report_msg(msg, reason);
    }

    @Override
    public void createEncounterMsgingChannel(String encounterId) {
        if (!shouldRunCommand(true)) return;
        esMsgs.find_and_act_on_topic(new ESMsgs.TopicAction(
                ESMsgs.TopicAction.TATyp.CreateOnly, encounterId));
    }

    @Override
    public void getNotifsWithCursor(ESNotifs.GetNotificationsCallback getNotificationsCallback, String cursor) {
        if (!shouldRunCommand(true)) return;
        esNotifs.get_notifications_from_cursor(getNotificationsCallback, cursor, false/*is_new*/);
    }

    @Override
    public void getNewNotifs(ESNotifs.GetNotificationsCallback getNotificationsCallback) {
        if (!shouldRunCommand(true)) return;
        esNotifs.get_notifications_from_cursor(getNotificationsCallback, null, true/*is_new*/);
    }

    @Override
    public void getEncountersOfNotifs(List<ESNotifs.Notif> notifs, ESNotifs.GetEncountersOfNotifsCallback getEncountersOfNotifsCallback) {
        if (!shouldRunCommand(true)) return;
        esNotifs.get_encounters_of_notifications(notifs, getEncountersOfNotifsCallback);
    }

    private boolean shouldRunCommand(boolean should_be_signed_in) {
        return (isRunning &&
                ((should_be_signed_in && UserAccount.getInstance().isSignedIn())
                    || (!should_be_signed_in)));
    }
}
