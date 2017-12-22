package org.mpisws.sddrservice;

import android.content.Context;

import com.microsoft.embeddedsocial.autorest.models.Reason;

import org.mpisws.sddrservice.embeddedsocial.ESMsgs;
import org.mpisws.sddrservice.embeddedsocial.ESNotifs;

import java.util.List;

/**
 * Created by tslilyai on 12/21/17.
 */

public interface IEncountersService {

    public void startEncounterService(Context context);

    public void addLinkableID(String linkID);

    public List<String> getEncounters(EncountersService.Filter filter);

    public void registerGoogleUser(String googletoken, String firstname, String lastname);

    public void signIn();

    public void signOut();

    public void sendMsgs(String encounterID, List<String> msgs);

    public void getMsgsWithCursor(String encounterID, ESMsgs.GetMessagesCallback callback, String cursor);

    public void getNewMsgs(String encounterID, ESMsgs.GetMessagesCallback callback);

    public void reportMsg(ESMsgs.Msg msg, Reason reason);

    public void createEncounterMsgingChannel(String eid);

    public void getNotifsWithCursor(ESNotifs.GetNotificationsCallback getNotificationsCallback, String cursor);

    public void getNewNotifs(ESNotifs.GetNotificationsCallback getNotificationsCallback);

    public void getEncountersOfNotifs(List<ESNotifs.Notif> notifs, ESNotifs.GetEncountersOfNotifsCallback getEncountersOfNotifsCallback);

    /*public void enable_msging_channels();

    public void disable_msging_channels();*/

}
