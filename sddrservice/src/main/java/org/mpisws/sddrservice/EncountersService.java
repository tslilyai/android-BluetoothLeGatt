package org.mpisws.sddrservice;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import com.microsoft.embeddedsocial.account.UserAccount;
import com.microsoft.embeddedsocial.autorest.models.Reason;
import com.microsoft.embeddedsocial.base.GlobalObjectRegistry;
import com.microsoft.embeddedsocial.server.EmbeddedSocialServiceProvider;

import org.mpisws.sddrservice.embeddedsocial.ESMsgs;
import org.mpisws.sddrservice.embeddedsocial.ESNotifs;
import org.mpisws.sddrservice.embeddedsocial.ESUser;
import org.mpisws.sddrservice.encounterhistory.EncounterBridge;
import org.mpisws.sddrservice.encounterhistory.MEncounter;
import org.mpisws.sddrservice.encounters.SDDR_Core_Service;
import org.mpisws.sddrservice.lib.Identifier;
import org.mpisws.sddrservice.lib.Utils;
import org.mpisws.sddrservice.linkability.LinkabilityEntryMode;

import java.io.IOException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Created by tslilyai on 11/6/17.
 */

public class EncountersService implements IEncountersService {
    private static final String TAG = EncountersService.class.getSimpleName();
    private static final EncountersService instance = new EncountersService();
    private static final String FILTER_END_STR = "ENDOFFILTERSTR";
    private static final String NUMHOPS_END_STR = "ENDOFNUMHOPSSTR";
    private EncountersService(){};

    /*
        Set when the SDDR service is started. No API call other than start_service can be
        made when this boolean is false.
    */
    private boolean isRunning = false;
    private Context context;
    private ESUser esUser;
    private ESMsgs esMsg;
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
        esMsg = new ESMsgs(context);
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
    public void deleteAccount() {
        if (!shouldRunCommand(true)) return;
        esUser.delete_account();
    }

    @Override
    public void registerGoogleUser(String googletoken) {
       if (!shouldRunCommand(false)) return;
        esUser.register_google_user(googletoken);
    }

    @Override
    public boolean isSignedIn() {
        return UserAccount.getInstance().isSignedIn();
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
    public void sendMsg(String encounterID, String msg) {
        if (!shouldRunCommand(true)) return;
        esMsg.find_and_act_on_topic(new ESMsgs.TopicAction(
                ESMsgs.TopicAction.TATyp.SendMsg, encounterID, msg));
    }

    @Override
    public void getMsgsFromNewest(String encounterID, long thresholdMessageAge, ESMsgs.GetMessagesCallback getMessagesCallback) {
        if (!shouldRunCommand(true)) return;
        esMsg.find_and_act_on_topic(new ESMsgs.TopicAction(
                ESMsgs.TopicAction.TATyp.GetMsgs, thresholdMessageAge, encounterID, null, getMessagesCallback));
    }

    @Override
    public void getMsgsFromCursor(String encounterID, long thresholdMessageAge, ESMsgs.GetMessagesCallback getMessagesCallback, String cursor) {
        if (!shouldRunCommand(true)) return;
        esMsg.find_and_act_on_topic(new ESMsgs.TopicAction(
                ESMsgs.TopicAction.TATyp.GetMsgs, thresholdMessageAge, encounterID, cursor, getMessagesCallback));
    }

    @Override
    public void reportMsg(ESMsgs.Msg msg, Reason reason) {
        if (!shouldRunCommand(true)) return;
        esMsg.report_msg(msg, reason);
    }

    @Override
    public void getNotificationsFromNewest(ESNotifs.GetNotificationsCallback getNotificationsCallback, GetNotificationsRequestFlag flag) {
        if (!shouldRunCommand(true)) return;
        esNotifs.get_notifications_from_cursor(getNotificationsCallback, null, flag);

    }

    @Override
    public void getNotificationsWithCursor(ESNotifs.GetNotificationsCallback getNotificationsCallback, GetNotificationsRequestFlag flag, String cursor) {
        if (!shouldRunCommand(true)) return;
        esNotifs.get_notifications_from_cursor(getNotificationsCallback, cursor, flag);
    }

    @Override
    public void getMessagesFromNotifications(List<ESNotifs.Notif> notifs, ESMsgs.GetMessagesCallback getMessagesCallback) {
        if (!shouldRunCommand(true)) return;
        esNotifs.get_messages_of_notifications(notifs, getMessagesCallback, esMsg);
    }

    @Override
    public void markAllPreviousNotificationsAsRead(ESNotifs.Notif notif) {
        if (notif == null)
            return;
        GlobalObjectRegistry.getObject(EmbeddedSocialServiceProvider.class).getNotificationService().updateReadNotifications(notif.getCursor());
    }

    @Override
    public void createEncounterMsgingChannel(String encounterId) {
        if (!shouldRunCommand(true)) return;
        esMsg.find_and_act_on_topic(new ESMsgs.TopicAction(
                ESMsgs.TopicAction.TATyp.CreateOnly, encounterId));
    }

    @Override
    public void sendBroadcastMsg(String msg, EncountersService.Filter filter) {
        sendBroadcastMsg(msg, filter, -1);
    }

    @Override
    public void sendBroadcastMsg(String msg, EncountersService.Filter filter, int numHopsThreshold) {
        if (!shouldRunCommand(true)) return;

        String filterstr = "";
        try { filterstr = Utils.serializeObjectToString(filter);}
        catch (IOException e) {}

        String newMsg = (numHopsThreshold + NUMHOPS_END_STR + filterstr + FILTER_END_STR + msg);
        List<String> encounters = getEncounters(filter);
        for (String eid : encounters) {
            sendMsg(eid, newMsg);
        }
    }

    @Override
    public void processMessageForBroadcasts(ESMsgs.Msg msg) {
        if (msg.getMsg().contains(FILTER_END_STR) && !msg.isFromMe()) {
            Log.d(TAG, "Processing msg for broadcasts");
            String[] msg_parts = msg.getMsg().split(FILTER_END_STR);
            Utils.myAssert(msg_parts.length == 2);

            String msgText = msg_parts[1];
            int numHopsThreshold = Integer.parseInt(msg_parts[0].split(NUMHOPS_END_STR)[0]);
            if (numHopsThreshold > 0) {
                Filter filter = null;
                try {
                    filter = Filter.class.cast(Utils.deserializeObjectFromString(msg_parts[0].split(NUMHOPS_END_STR)[1]));
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
                sendBroadcastMsg(msgText, filter, numHopsThreshold - 1);
            }
        }
    }

    private boolean shouldRunCommand(boolean should_be_signed_in) {
        return (isRunning &&
                ((should_be_signed_in && UserAccount.getInstance().isSignedIn())
                    || (!should_be_signed_in)));
    }
}
