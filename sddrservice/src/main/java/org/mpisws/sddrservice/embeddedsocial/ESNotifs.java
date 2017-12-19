package org.mpisws.sddrservice.embeddedsocial;

import android.content.Context;
import android.os.Parcelable;
import android.util.Log;

import com.microsoft.embeddedsocial.actions.ActionsLauncher;
import com.microsoft.embeddedsocial.autorest.models.ActivityType;
import com.microsoft.embeddedsocial.fetcher.FetchersFactory;
import com.microsoft.embeddedsocial.fetcher.base.Callback;
import com.microsoft.embeddedsocial.fetcher.base.Fetcher;
import com.microsoft.embeddedsocial.fetcher.base.FetcherState;
import com.microsoft.embeddedsocial.server.model.view.ActivityView;

import org.mpisws.sddrservice.lib.Identifier;

import java.util.List;

/**
 * Created by tslilyai on 11/14/17.
 */


public class ESNotifs {
    private static final String TAG = ESNotifs.class.getSimpleName();
    private final Context context;

    public static class Notif {
        private Identifier eid;
        private String msg;
        private long timestamp;

        public Notif(Identifier eid, String msg, long timestamp) {
            this.eid = eid;
            this.msg = msg;
            this.timestamp = timestamp;
        }
        public Identifier getEid() {
            return eid;
        }
        public String getMsg() {
            return msg;
        }
        public long getTimestamp() {
            return timestamp;
        }
    }

    public ESNotifs(Context context) {this.context = context;}

    public interface NotificationCallback extends Parcelable {
        void onReceiveNotif(Notif notif);
    }

    protected void get_notifications(final NotificationCallback notificationCallback) {
        final Fetcher<ActivityView> notifFeedFetcher = FetchersFactory.createNotificationFeedFetcher();
        Callback callback = new Callback() {
                    @Override
                    public void onStateChanged(FetcherState newState) {
                    super.onStateChanged(newState);
                    switch (newState) {
                        case LOADING:
                            break;
                        case DATA_ENDED:
                            process_notifs(notificationCallback, notifFeedFetcher.getAllData());
                            break;
                        default:
                            notifFeedFetcher.requestMoreData();
                            break;
                    }
                    }
                };
        notifFeedFetcher.setCallback(callback);
        // TODO update unread notifs?
    }

    private void process_notifs(NotificationCallback notificationCallback, List<ActivityView> notifs) {
        Log.d(TAG, "Received " + notifs.size() + " notifications");
        for (ActivityView n : notifs) {
            if (n.getActivityType() == ActivityType.REPLY) {
                Log.d(TAG, "Notification of type reply");
                ActionsLauncher.getReplyOfNotif(context, n.getHandle(), n.getActedOnContentText(), notificationCallback);
            } else if (n.getActivityType() == ActivityType.COMMENT) {
                Log.d(TAG, "Notification of type comment");
                ActionsLauncher.getCommentOfNotif(context, n.getHandle(), n.getActedOnContentText(), notificationCallback);
            } else {
                Log.d(TAG, "Notif of no known activity type!");
            }
        }
    }
}
