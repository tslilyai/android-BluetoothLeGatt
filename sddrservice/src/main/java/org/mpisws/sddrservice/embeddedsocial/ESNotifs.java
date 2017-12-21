package org.mpisws.sddrservice.embeddedsocial;

import android.util.Log;

import com.microsoft.embeddedsocial.autorest.models.ActivityType;
import com.microsoft.embeddedsocial.base.GlobalObjectRegistry;
import com.microsoft.embeddedsocial.fetcher.FetchersFactory;
import com.microsoft.embeddedsocial.fetcher.base.Callback;
import com.microsoft.embeddedsocial.fetcher.base.Fetcher;
import com.microsoft.embeddedsocial.fetcher.base.FetcherState;
import com.microsoft.embeddedsocial.server.EmbeddedSocialServiceProvider;
import com.microsoft.embeddedsocial.server.IContentService;
import com.microsoft.embeddedsocial.server.exception.NetworkRequestException;
import com.microsoft.embeddedsocial.server.model.content.comments.GetCommentRequest;
import com.microsoft.embeddedsocial.server.model.content.comments.GetCommentResponse;
import com.microsoft.embeddedsocial.server.model.content.replies.GetReplyRequest;
import com.microsoft.embeddedsocial.server.model.content.replies.GetReplyResponse;
import com.microsoft.embeddedsocial.server.model.content.topics.GetTopicRequest;
import com.microsoft.embeddedsocial.server.model.view.ActivityView;

import java.util.concurrent.ExecutorService;

import static java.util.concurrent.Executors.newCachedThreadPool;

/**
 * Created by tslilyai on 11/14/17.
 */


public class ESNotifs {
    private static final String TAG = ESNotifs.class.getSimpleName();
    private final Fetcher<ActivityView> notifFeedFetcher;
    private final ExecutorService executorService;

    public static class Notif {
        private ActivityView activityView;

        public Notif(ActivityView activityView) {
            this.activityView = activityView;
        }

        public long getNotifTime() {
            return activityView.getElapsedSeconds();
        }
        public String getNotifCursor() {
            return activityView.getHandle();
        }
    }

    public ESNotifs() {
        notifFeedFetcher = FetchersFactory.createNotificationFeedFetcher();
        executorService = newCachedThreadPool();
    }

    public interface NotificationCallback {
        void onReceiveNotification(Notif notif);
    }

    protected void get_notifications_from_cursor(NotificationCallback notificationCallback, String cursor, boolean is_new) {
        if (notifFeedFetcher.isLoading()) { return; }
        Callback callback = new Callback() {
            @Override
            public void onStateChanged(FetcherState newState) {
                super.onStateChanged(newState);
                switch (newState) {
                    case LOADING:
                        break;
                    case LAST_ATTEMPT_FAILED:
                        notifFeedFetcher.requestMoreData();
                        break;
                    default: // ENDED or MORE_DATA
                        Log.d(TAG, "Data ended? " + (newState != FetcherState.HAS_MORE_DATA));
                        for (ActivityView av : notifFeedFetcher.getAllData()) {
                            if (!av.isUnread() && is_new) {
                                break;
                            }
                            notificationCallback.onReceiveNotification(new Notif(av));
                        }
                }
            }
        };
        notifFeedFetcher.setCallbackSilent(callback);
        notifFeedFetcher.clearData();

        if (is_new) {
            // this will call the callback after a new page is gotten from the beginning
            notifFeedFetcher.refreshData();
        } else if (notifFeedFetcher.hasMoreData()) {
            if (cursor != null) {
                notifFeedFetcher.setCursor(cursor);
            }
            notifFeedFetcher.requestMoreData();
        }
    }

    public void get_msg_of_notification(Notif notif, ESMsgs.MsgCallback msgCallback) {
        Runnable r = () -> {
            ActivityView n = notif.activityView;
            IContentService contentService = GlobalObjectRegistry.getObject(EmbeddedSocialServiceProvider.class).getContentService();

            // get the EID of the message
            String eid;
            try {
                // get the content of the message (unless it's the reply comment) and create a message
                // to call using the callback
                String msgText, msgHandle;
                long timeStamp;
                if (n.getActivityType() == ActivityType.REPLY) {
                    Log.d(TAG, "Notification of type reply");
                    final GetReplyRequest request = new GetReplyRequest(n.getHandle());
                    GetReplyResponse response = contentService.getReply(request);

                    final GetCommentRequest req = new GetCommentRequest(response.getReply().getCommentHandle());
                    eid = contentService.getComment(req).getComment().getCommentText();

                    msgText = response.getReply().getReplyText();
                    msgHandle = response.getReply().getHandle();
                    timeStamp = response.getReply().getElapsedSeconds();
                } else if (n.getActivityType() == ActivityType.COMMENT) {
                    Log.d(TAG, "Notification of type comment");
                    final GetCommentRequest request = new GetCommentRequest(n.getHandle());
                    GetCommentResponse response = contentService.getComment(request);

                    final GetTopicRequest req = new GetTopicRequest(response.getComment().getTopicHandle());
                    eid = contentService.getTopic(req).getTopic().getTopicTitle();

                    if (eid.compareTo(response.getComment().getCommentText()) == 0) {
                        Log.d(TAG, "This is the reply comment: " + response.getComment().getCommentText());
                        return;
                    }
                    msgText = response.getComment().getCommentText();
                    msgHandle = response.getComment().getHandle();
                    timeStamp = response.getComment().getElapsedSeconds();
                } else {
                    Log.e(TAG, "Notif of no known activity type!");
                    return;
                }
                Log.d(TAG, "Got msg " + msgText + " with eid " + eid);
                msgCallback.onReceiveMessage(new ESMsgs.Msg(msgHandle, msgText, eid, false, timeStamp));
            } catch (NetworkRequestException e) {
            }
        };
        executorService.execute(r);
    }
}
