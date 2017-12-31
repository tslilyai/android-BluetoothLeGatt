package org.mpisws.sddrservice;

import android.content.Context;
import android.location.Location;

import com.microsoft.embeddedsocial.autorest.models.Reason;

import org.mpisws.sddrservice.embeddedsocial.ESMsgs;
import org.mpisws.sddrservice.embeddedsocial.ESNotifs;

import java.util.Date;
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

    public static class Filter {
        public Date start_date;
        public Date end_date;
        public Location location;
        public float radius;
        public List<String> matches;

        Filter() {};
        public void setTimeInterval(Date start_date, Date end_date) {
            this.start_date = start_date;
            this.end_date = end_date;
        }
        public void setCircularRegion(Location location, float radius) {
            this.location = location;
            this.radius = radius;
        }
        public void setMatches(List<String> matches) {
            this.matches = matches;
        }
    }

    public enum GetNotificationsRequestFlag {
        UNREAD_ONLY,
        ALL
    }

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
     * Register a google token to use to sign in the user.
     *
     * @param googletoken the authentication token retrieved from the Google OAuth service
     */
    public void registerGoogleUser(String googletoken);

    /**
     * Determines if the user is signed in; if false, the user must be registered with Google again
     * @return a boolean representing whether the user is signed in or not
     */
    public boolean isSignedIn();

    /**
     * Uses the registered user's details to create an account (if not created) or sign into their Encounters account
     */
    public void signIn();

    /**
     * Logs the user out and erases their user information. Requires re-registering and signing in.
     */
    public void signOut();

    /**
     * Deletes the currently-logged-in user's account and the user's content.
     */
    public void deleteAccount();

    /**
     * Sends a message to the specified encounter.
     *
     * @param encounterID the String name for the encounter
     * @param msg a string that represents the message to be sent to this encounter
     */
    public void sendMsg(String encounterID, String msg);

    /**
     * Gets the newest messages of this encounter thread, and invokes
     * the GetMessagesCallback parameter on the retrieved list.
     *
     * @param encounterID the String name for the encounter
     * @param thresholdMessageAge a timestamp (ms) representing the oldest message to be retrieved. If this is negative, all messages are retrieved.
     * @param getMessagesCallback a callback to be called with the list of retrieved messages
     */
    public void getMsgsFromNewest(String encounterID, long thresholdMessageAge, ESMsgs.GetMessagesCallback getMessagesCallback);

    /**
     * Gets the messages of this encounter from a given point in the encounter thread, and invokes
     * the GetMessagesCallback parameter on the retrieved list.
     *
     * @param encounterID the String name for the encounter
     * @param thresholdMessageAge a timestamp (ms) representing the oldest message to be retrieved. If this is negative, all messages are retrieved.
     * @param getMessagesCallback a callback to be called with the list of retrieved messages
     * @param cursor the point in the thread at which to start retrieving messages; each message is associated with a cursor (see the getCursor() method for a Msg)
     */
    public void getMsgsFromCursor(String encounterID, long thresholdMessageAge, ESMsgs.GetMessagesCallback getMessagesCallback, String cursor);

    /**
     * Report a particular message for its content.
     * @param msg the message being reported
     * @param reason the reason for reporting
     */
    public void reportMsg(ESMsgs.Msg msg, Reason reason);

    /**
     * Gets a page of notifications in the notifications feed from the newest notification, and invokes
     * the GetNotificationsCallback parameter on the retrieved list.
     *
     * @param getNotificationsCallback a callback to be called with the page of retrieved notifications
     * @param flag Indicates the type of notifications to be fetched
     */
    public void getNotificationsFromNewest(ESNotifs.GetNotificationsCallback getNotificationsCallback, GetNotificationsRequestFlag flag);

    /**
     * Gets a page of notifications in the notifications feed from the given cursor, and invokes
     * the GetNotificationsCallback parameter on the retrieved list.
     *
     * @param getNotificationsCallback a callback to be called with the page of retrieved notifications
     * @param flag indicates the type of notifications to be fetched
     * @param cursor a string corresponding to the first notification from where notifications should be retrieved
     */
    public void getNotificationsWithCursor(ESNotifs.GetNotificationsCallback getNotificationsCallback, GetNotificationsRequestFlag flag, String cursor);

    /**
     * Gets the messages associated with each notification in the provided list. All messages that exist in the encounter topic
     * associated with the notification, and occurred at or after the time of the notification, are retrieved. The
     * callback is invoked on the retrieved list of messages for each notification.
     *
     * @param notifs the notifications for which their corresponding encounters will be retrieved
     * @param getMessagesCallback a callback to be called with the messages for the notification
     */
    public void getMessagesFromNotifications(List<ESNotifs.Notif> notifs, ESMsgs.GetMessagesCallback getMessagesCallback);

    /**
     * Marks all notifications prior to and including the specified notification as read.
     *
     * @param notif the notification that sets the upper bound on read notifications
     */
    public void markAllPreviousNotificationsAsRead(ESNotifs.Notif notif);

    /**
     * Creates a topic for communication with this particular encounter
     *
     * @param encounterID the String name for the encounter
     */
    public void createEncounterMsgingChannel(String encounterID);

    /**
     * Sends a message to all encounters that meet the requirement specified by filter,
     * and additionally indicates that those encounters should forward the message to any
     * of their encounters that fit the filter. This forwarding continues within the bounds of the filter..
     *
     * @param msg a string representing the message to be sent
     * @param filter a filter to specify the time interval, location range, and/or link matches
     *               of encounters to which the message should be forwarded
     */
    public void sendBroadcastMsg(String msg, EncountersService.Filter filter);

    /**
     * Sends a message to all encounters that meet the requirement specified by filter,
     * and additionally indicates that those encounters should forward the message to any
     * of their encounters that fit the filter. This forwarding continues for the specified
     * number of encounter hops.
     *
     * @param msg a string representing the message to be sent
     * @param filter a filter to specify the time interval, location range, and/or link matches
     *               of encounters to which the message should be forwarded
     * @param numHopsThreshold the limit of the number of hops this message should be sent
     */
    public void sendBroadcastMsg(String msg, EncountersService.Filter filter, int numHopsThreshold);

    /**
     * Processes a message to see if it should be broadcasted
     *
     * @param msg the message to be processed
     */
    public void processMessageForBroadcasts(ESMsgs.Msg msg);

}
