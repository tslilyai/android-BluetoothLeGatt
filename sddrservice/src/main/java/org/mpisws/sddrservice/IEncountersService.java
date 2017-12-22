package org.mpisws.sddrservice;

import android.content.Context;

import com.microsoft.embeddedsocial.autorest.models.Reason;

import org.mpisws.sddrservice.embeddedsocial.ESMsgs;
import org.mpisws.sddrservice.embeddedsocial.ESNotifs;
import org.mpisws.sddrservice.lib.Identifier;

import java.util.List;

/**
 * Created by tslilyai on 12/21/17.
 */

public interface IEncountersService {

    public void startEncounterService(Context context);

    public void addLinkableID(String linkID);

    public List<Identifier> getEncounters(EncountersService.Filter filter);

    public void registerGoogleUser(String googletoken, String firstname, String lastname);

    public void signIn();

    public void signOut();

    public void sendMsgs(Identifier encounterID, List<String> msgs);

    public void getMsgsWithCursor(Identifier encounterID, ESMsgs.MsgCallback callback);

    public void getNewMsgs(Identifier encounterID, ESMsgs.MsgCallback callback);

    public void reportMsg(ESMsgs.Msg msg, Reason reason);

    public void createEncounterMsgingChannel(Identifier eid);

    public void getNotifsWithCursor(ESNotifs.NotificationCallback notificationCallback, String cursor);

    public void getNewNotifs(ESNotifs.NotificationCallback notificationCallback);

    /*public void enable_msging_channels();

    public void disable_msging_channels();*/

}
