package org.mpisws.sddrservice;

import android.content.Context;

import com.microsoft.embeddedsocial.autorest.models.Reason;

import org.mpisws.sddrservice.embeddedsocial.ESMsgs;
import org.mpisws.sddrservice.embeddedsocial.ESNotifs;

import java.util.List;

/**
 * Created by tslilyai on 12/21/17.
 * <p>
 * The IEncountersService provides the interface (implemented by singleton class EncountersService)
 * to the Encounters library for forming and communication between encounters.
 * <p>
 * To use the API, call EncountersService.getInstance() to get the (single) object
 * of class EncountersService. All interface functions can be invoked on the object.
 */
public interface IEncountersService {

    /**
     * Starts up the encounter-formation service that begins encounter formation
     * and turns on all encounter-based functionality
     * (e.g., sending messages across encounters).
     * If the service is already running, this has no effect.
     *
     * @param context the application calling the library
     */
    public void startEncounterService(Context context);

    /**
     * Adds a linkable identifier linkID to the Bluetooth broadcast used to form encounters. This allows
     * a recipient with whom you form an encounter, and who also has access to linkID,
     * to either later or at the present recognize that you were broadcasting linkID.
     *<p>
     * Example usages include recognition of group membership (e.g., a linkID that corresponds to
     * "interested in birdwatching") or revealing identity (linking two otherwise anonymous encounters)
     *
     * @param linkID the linkable identifier to be added to the Bluetooth broadcast
     */
    public void addLinkableID(String linkID);

    /**
     * Retrieves all encounters that satisfy the constraints of the filter.
     *
     * @param filter a filter consisting of a time frame, required linkable identifier matches, and location; if this is null, all encounters are retrieved
     * @return a list of String, each of which corresponds to the name of an encounter
     */
    public List<String> getEncounters(EncountersService.Filter filter);


    /**
     * Registers user details using a google token and the user's name.
     *
     * @param googletoken the authentication token retrieved from the Google OAuth service
     * @param firstname user's first name
     * @param lastname user's last name
     */
    public void registerGoogleUser(String googletoken, String firstname, String lastname);

    /**
     * Uses the registered user's details to create (if not created) or sign into their Encounters account
     */
    public void signIn();

    /**
     * Logs the user out and erases their user information. Requires re-registering and signing in.
     */
    public void signOut();

    /**
     * Sends a list of messages to the specified encounter.
     * @param encounterID the String name for the encounter
     * @param msgs a list of Strings that represent the messages to be sent to this encounter
     */
    public void sendMsgs(String encounterID, List<String> msgs);

    /**
     * Gets the messages of this encounter from a given point in the encounter thread, and invokes
     * the GetMessagesCallback parameter on the retrieved list.
     *
     * @param encounterID the String name for the encounter
     * @param getMessagesCallback a callback to be called with the list of retrieved messages
     * @param cursor the point in the thread at which to start retrieving messages; each message is associated with a cursor (see the getCursor() method for a Msg)
     */
    public void getMsgsWithCursor(String encounterID, ESMsgs.GetMessagesCallback getMessagesCallback, String cursor);

    /**
     * Gets the newest messages of this encounter thread, and invokes
     * the GetMessagesCallback parameter on the retrieved list.
     *
     * @param encounterID the String name for the encounter
     * @param getMessagesCallback a callback to be called with the list of retrieved messages
     */
    public void getNewMsgs(String encounterID, ESMsgs.GetMessagesCallback getMessagesCallback);

    /**
     * Report a particular message for its content.
     * @param msg the message being reported
     * @param reason the reason for reporting
     */
    public void reportMsg(ESMsgs.Msg msg, Reason reason);

    /**
     * Gets the notifications a given point in the notifications feed, and invokes
     * the GetNotificationsCallback parameter on the retrieved list.
     *
     * @param getNotificationsCallback a callback to be called with the list of retrieved notifications
     * @param cursor the point in the feed at which to start retrieving notifications; each notification is associated with a cursor (see the getCursor() method for a Notif)
     */
    public void getNotifsWithCursor(ESNotifs.GetNotificationsCallback getNotificationsCallback, String cursor);

    /**
     * Gets the newest notifications in the notifications feed, and invokes
     * the GetNotificationsCallback parameter on the retrieved list.
     *
     * @param getNotificationsCallback a callback to be called with the list of retrieved notifications
     */
    public void getNewNotifs(ESNotifs.GetNotificationsCallback getNotificationsCallback);

    /**
     * Gets the set of encounter names associated with the provided list of notifications (i.e., encounter names for which
     * the user has received a message), and invokes the GetEncountersOfNotifsCallback parameter on the retrieved set.
     *
     * @param notifs the notifications for which their corresponding encounters will be retrieved
     * @param getEncountersOfNotifsCallback a callback to be called with the set of retrieved encounter names
     */
    public void getEncountersOfNotifs(List<ESNotifs.Notif> notifs, ESNotifs.GetEncountersOfNotifsCallback getEncountersOfNotifsCallback);

    /**
     * Creates a thread to communicate with this particular encounter
     *
     * @param encounterID the String name for the encounter
     */
     public void createEncounterMsgingChannel(String encounterID);

    /*public void enable_msging_channels();

    public void disable_msging_channels();*/

}
