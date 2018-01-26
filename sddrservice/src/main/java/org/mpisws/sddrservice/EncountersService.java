package org.mpisws.sddrservice;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.microsoft.embeddedsocial.account.UserAccount;
import com.microsoft.embeddedsocial.autorest.models.Reason;
import com.microsoft.embeddedsocial.base.GlobalObjectRegistry;
import com.microsoft.embeddedsocial.server.EmbeddedSocialServiceProvider;

import org.joda.time.DateTime;
import org.mpisws.sddrservice.embeddedsocial.ESTopics;
import org.mpisws.sddrservice.embeddedsocial.ESTopics.Msg;
import org.mpisws.sddrservice.embeddedsocial.ESNotifs;
import org.mpisws.sddrservice.embeddedsocial.ESUser;
import org.mpisws.sddrservice.encounterhistory.EncounterBridge;
import org.mpisws.sddrservice.encounterhistory.MEncounter;
import org.mpisws.sddrservice.encounterhistory.SSBridge;
import org.mpisws.sddrservice.encounters.SDDR_Core_Service;
import org.mpisws.sddrservice.lib.Identifier;
import org.mpisws.sddrservice.lib.time.TimeInterval;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.mpisws.sddrservice.IEncountersService.Filter.FILTER_END_STR;

/**
 * Created by tslilyai on 11/6/17.
 */

public class EncountersService implements IEncountersService {
    private static final String TAG = EncountersService.class.getSimpleName();
    private static final EncountersService instance = new EncountersService();
    public static final int BUFFERED_MESSAGES_THRESHOLD = 20;
   /*
        Set when the SDDR service is started. No API call other than start_service can be
        made when this boolean is false.
    */
    private boolean isRunning = false;
    private Context context;
    private ESUser esUser;
    private ESTopics esMsg;
    private ESNotifs esNotifs;
    private HashMap<String, Long> fwdedMsgs;
    private Set<Msg> repeatingMsgs;

    private EncountersService(){
        fwdedMsgs = new LinkedHashMap<>();
        repeatingMsgs = new HashSet<>();
    }

    public static EncountersService getInstance() {
        return instance;
    }

    @Override
    public void startTestTopics(Context context) {
        esUser = new ESUser(context);
        esMsg = new ESTopics(context);
        esNotifs = new ESNotifs();

        this.context = context;
        this.isRunning = true;
    }

    @Override
    public void startTestEncountersES(Context context) {
        Intent serviceIntent = new Intent(context, SDDR_Core_Service.class);
        serviceIntent.putExtra("@string.start_sddr_service", 0);
        context.startService(serviceIntent);

        esUser = new ESUser(context);
        esMsg = new ESTopics(context);
        esNotifs = new ESNotifs();

        this.context = context;
        this.isRunning = true;
    }

    @Override
    public void startEncounterService(Context context) {
        Intent serviceIntent = new Intent(context, SDDR_Core_Service.class);
        serviceIntent.putExtra("@string.start_sddr_service", 0);
        context.startService(serviceIntent);

        esUser = new ESUser(context);
        esMsg = new ESTopics(context);
        esNotifs = new ESNotifs();

        this.context = context;
        this.isRunning = true;
    }

    @Override
    public void setConfirmEncountersOverBT(boolean active) {
        if (!shouldRunCommand(false)) {
            return;
        }
        Intent serviceIntent = new Intent(context, SDDR_Core_Service.class);
        serviceIntent.putExtra("confirmation_active", active);
        context.startService(serviceIntent);
    }

    @Override
    public void confirmEncountersOverES() {
        if (!shouldRunCommand(true)) {
            return;
        }
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
    public void registerGoogleUser(String googletoken) {
       if (!shouldRunCommand(false)) return;
        esUser.register_google_user(googletoken);
    }

    @Override
    public boolean isSignedIn() {
        if (!isRunning) {
            return false;
        }
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
    public void deleteAccount() {
        if (!shouldRunCommand(true)) return;
        esUser.delete_account();
    }

    @Override
    public void blockSender(Msg msg) {
        if (!shouldRunCommand(true)) return;
        esUser.block_sender(msg);
    }

    @Override
    public void unblockSender(Msg msg) {
        if (!shouldRunCommand(true)) return;
        esUser.unblock_sender(msg);
    }

    @Override
    public void sendMsg(String encounterID, String msg) {
        if (!shouldRunCommand(true)) return;
        esMsg.find_and_act_on_topic(new ESTopics.TopicAction(
                ESTopics.TopicAction.TATyp.SendMsg, encounterID, msg));
    }

    @Override
    public void getMsgsFromNewest(String encounterID, long thresholdMessageAge, ESTopics.GetMessagesCallback getMessagesCallback) {
        if (!shouldRunCommand(true)) return;
        esMsg.find_and_act_on_topic(new ESTopics.TopicAction(
                ESTopics.TopicAction.TATyp.GetMsgs, thresholdMessageAge, encounterID, null, getMessagesCallback));
    }

    @Override
    public void getMsgsFromCursor(String encounterID, long thresholdMessageAge, ESTopics.GetMessagesCallback getMessagesCallback, String cursor) {
        if (!shouldRunCommand(true)) return;
        esMsg.find_and_act_on_topic(new ESTopics.TopicAction(
                ESTopics.TopicAction.TATyp.GetMsgs, thresholdMessageAge, encounterID, cursor, getMessagesCallback));
    }

    @Override
    public void reportMsg(Msg msg, Reason reason) {
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
    public void getMessagesFromNotifications(List<ESNotifs.Notif> notifs, ESTopics.GetMessagesCallback getMessagesCallback) {
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
        Log.d(TAG, "Creating EID topic "+ encounterId);
        esMsg.find_and_act_on_topic(new ESTopics.TopicAction(
                ESTopics.TopicAction.TATyp.CreateEIDTopic, encounterId));
    }

    @Override
    public void sendBroadcastMsg(String msg, EncountersService.ForwardingFilter filter) {
        if (!shouldRunCommand(true)) return;
        if (filter != null && filter.getNumHops() > 0) {
            String filterstr = filter.toString();
            String newMsg = (filterstr + FILTER_END_STR + msg);
            List<String> encounters = getEncounters(filter);

            // send to a limited number of encounters
            for (int i = 0; i < filter.getFanoutLimit(); ++i) {
                if (i >= encounters.size()) {
                    break;
                }
                sendMsg(encounters.get(i), newMsg);
            }
            // store our own broadcast messages if they need to be repeatedly sent
            if (filter.isRepeating()) {
                storeBroadcastMessage(new Msg(null, 0, "", msg, filter, "", true));
            }
        }
    }

    @Override
    public void processMessageForBroadcasts(Msg msg) {
        if (!msg.isFromMe() && msg.getFilter() != null) {
            Log.d("ESACTIVE_TEST", "Msg: " + msg.getEid() + " " + msg.getMsg() + " " + DateTime.now().getMillis());
            long pkid = new SSBridge(context).getEncounterPKIDByEncounterID(msg.getEid());
            EncounterBridge bridge = new EncounterBridge(context);
            MEncounter encounter = bridge.getItemByPKID(pkid);
            if (bridge.isEncounterValid(msg.getFilter(), encounter)) {
                Log.v(TAG, "Processing msg for broadcasts");
                boolean shouldSend = msg.getFilter().getNumHops() > 0 && storeBroadcastMessage(msg);
                if (shouldSend) {
                    sendBroadcastMsg(msg.getMsg(), msg.getFilter().setNumHopsLimit(msg.getFilter().getNumHops() - 1));
                }
            }
        }
    }

    public void sendRepeatingBroadcastMessages() {
        for (Iterator<Msg> i = repeatingMsgs.iterator(); i.hasNext();) {
            Msg msg = i.next();
            ForwardingFilter filter = msg.getFilter();
            if (filter.isAlive(DateTime.now().getMillis())) {
                // decrease number of hops before encoding filter
                filter.setNumHopsLimit(filter.getNumHops()-1);

                String filterstr = filter.toString();
                String newMsg = (filterstr + FILTER_END_STR + msg);

                // only send to new encounters. note that the msg encoding has encoded the original time interval
                // TODO this sends to continuing encounters that overlap with the beginning of the time interval
                filter.setTimeInterval(new TimeInterval(DateTime.now().getMillis(), filter.getTimeInterval().getEndL()));

                List<String> encounters = getEncounters(filter);
                // again, limit number of encounters that receive this message
                for (int j = 0; j < filter.getFanoutLimit(); ++j) {
                    if (j >= encounters.size()) {
                        break;
                    }
                    Log.v(TAG, "Sending repeating messages");
                    sendMsg(encounters.get(j), newMsg);
                }
            } else {
                i.remove();
            }
        }
    }

    private boolean storeBroadcastMessage(Msg msg) {
        if (msg.getFilter().getNumHops() <= 0) {
            return false;
        }

        // TODO for right now, just don't send if we are out of space
        if (fwdedMsgs.size() > MSG_STORAGE_LIMIT) {
            cleanupOldMessages();
            if (fwdedMsgs.size() > MSG_STORAGE_LIMIT) {
                return false;
            }
        }
        if (!msg.getFilter().isAlive(DateTime.now().getMillis()) || fwdedMsgs.containsKey(msg.getCursor())) {
            return false;
        }
        fwdedMsgs.put(msg.getCursor(), msg.getFilter().getEndTime());
        if (msg.getFilter().isRepeating()) {
            Log.v(TAG, "Stored repeating message");
            repeatingMsgs.add(msg);
        }
        return true;
    }

    private void cleanupOldMessages() {
         for (Iterator<Msg> i = repeatingMsgs.iterator(); i.hasNext();) {
             Msg msg = i.next();
             if (!msg.getFilter().isAlive(DateTime.now().getMillis())) {
                 i.remove();
             }
         }
         for (String msgCursor : fwdedMsgs.keySet()) {
             if (fwdedMsgs.get(msgCursor) >= DateTime.now().getMillis()) {
                 fwdedMsgs.remove(msgCursor);
             }
         }
    }


    private boolean shouldRunCommand(boolean should_be_signed_in) {
        return (isRunning &&
                ((should_be_signed_in && UserAccount.getInstance().isSignedIn())
                    || (!should_be_signed_in)));
    }
}