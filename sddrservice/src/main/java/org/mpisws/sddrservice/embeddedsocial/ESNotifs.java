package org.mpisws.sddrservice.embeddedsocial;

import android.util.Log;

import com.microsoft.embeddedsocial.autorest.models.ActivityType;
import com.microsoft.embeddedsocial.autorest.models.CommentView;
import com.microsoft.embeddedsocial.autorest.models.PutNotificationsStatusRequest;
import com.microsoft.embeddedsocial.autorest.models.TopicView;
import com.microsoft.embeddedsocial.base.GlobalObjectRegistry;
import com.microsoft.embeddedsocial.fetcher.FetchersFactory;
import com.microsoft.embeddedsocial.fetcher.base.Callback;
import com.microsoft.embeddedsocial.fetcher.base.Fetcher;
import com.microsoft.embeddedsocial.fetcher.base.FetcherState;
import com.microsoft.embeddedsocial.server.IContentService;
import com.microsoft.embeddedsocial.server.exception.NetworkRequestException;
import com.microsoft.embeddedsocial.server.model.content.comments.GetCommentRequest;
import com.microsoft.embeddedsocial.server.model.content.comments.GetCommentResponse;
import com.microsoft.embeddedsocial.server.model.content.replies.GetReplyRequest;
import com.microsoft.embeddedsocial.server.model.content.replies.GetReplyResponse;
import com.microsoft.embeddedsocial.server.model.view.ActivityView;
import com.microsoft.embeddedsocial.server.EmbeddedSocialServiceProvider;
import com.microsoft.rest.ServiceCallback;
import com.microsoft.rest.ServiceResponse;

import org.joda.time.DateTime;
import org.mpisws.sddrservice.lib.Identifier;

import java.util.List;

/**
 * Created by tslilyai on 11/14/17.
 */


public class ESNotifs {
    private static final String TAG = ESNotifs.class.getSimpleName();

    public class Notif {
        private Identifier eid;
        private String msg;
        private long timestamp;

        Notif(Identifier eid, String msg, long timestamp) {
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

    public ESNotifs() {}

    public interface NotificationCallback {
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
                        default:
                            notifFeedFetcher.requestMoreData();
                            break;
                    }
                    }
                };
        notifFeedFetcher.setCallback(callback);
        notifFeedFetcher.requestMoreData();
        // TODO update unread notifs?
    }

    private void process_notifs(NotificationCallback notificationCallback, List<ActivityView> notifs) {
        IContentService contentService = GlobalObjectRegistry.getObject(EmbeddedSocialServiceProvider.class).getContentService();
        GetReplyResponse resprep;
        GetCommentResponse respcom;
        String msg = null;
        for (ActivityView n : notifs) {
            try {
                if (n.getActivityType() == ActivityType.REPLY) {
                    resprep = contentService.getReply(new GetReplyRequest(n.getHandle()));
                    msg = resprep.getReply().getReplyText();
                }
                else if (n.getActivityType() == ActivityType.COMMENT) {
                    respcom = contentService.getComment(new GetCommentRequest(n.getHandle()));
                    msg = respcom.getComment().getCommentText();
               }
               else {
                    Log.d(TAG, "Notif of no known activity type!");
               }
               notificationCallback.onReceiveNotif(new Notif(
                       new Identifier(n.getActedOnContentText().getBytes()), msg, n.getElapsedSeconds()));
            } catch (NetworkRequestException e) {
                e.printStackTrace();
            }
        }
    }
}
