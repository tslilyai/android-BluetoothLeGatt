package org.mpisws.sddrservice.embeddedsocial;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Parcelable;
import android.os.Process;
import android.util.Log;

import com.microsoft.embeddedsocial.actions.ActionsLauncher;
import com.microsoft.embeddedsocial.autorest.models.ActivityType;
import com.microsoft.embeddedsocial.base.GlobalObjectRegistry;
import com.microsoft.embeddedsocial.base.event.EventBus;
import com.microsoft.embeddedsocial.event.content.GetCommentEvent;
import com.microsoft.embeddedsocial.event.content.GetReplyEvent;
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
import com.microsoft.embeddedsocial.server.model.view.ActivityView;
import com.microsoft.embeddedsocial.service.handler.GetCommentHandler;
import com.microsoft.embeddedsocial.service.handler.GetReplyHandler;

import org.mpisws.sddrservice.lib.Identifier;

import java.net.URL;
import java.util.List;
import java.util.concurrent.Callable;

import static android.R.attr.action;

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

    private class ProcessNotifsClass extends AsyncTask<List<ActivityView>, Void, Void> {
        private NotificationCallback notificationCallback;

        public ProcessNotifsClass(NotificationCallback notificationCallback) {
            this.notificationCallback = notificationCallback;
        }

        @Override
        protected Void doInBackground(List<ActivityView>... lists) {
            for (List<ActivityView> notifs : lists) {
                for (ActivityView n : notifs) {
                    if (n.getActivityType() == ActivityType.REPLY) {
                        Log.d(TAG, "Notification of type reply");
                        getReplyOfNotif(n.getHandle(), n.getActedOnContentText(), notificationCallback);
                    } else if (n.getActivityType() == ActivityType.COMMENT) {
                        Log.d(TAG, "Notification of type comment");
                        getCommentOfNotif(n.getHandle(), n.getActedOnContentText(), notificationCallback);
                    } else {
                        Log.d(TAG, "Notif of no known activity type!");
                    }
                }
            }
            return null;
        }
        protected void onPostExecute() {
            Log.d(TAG, "Done processing notifs");
        }
    }

    private void process_notifs(NotificationCallback notificationCallback, List<ActivityView> notifs) {
        ProcessNotifsClass processNotifs = new ProcessNotifsClass(notificationCallback);
        processNotifs.execute(notifs);
    }

    private void getCommentOfNotif(String commentHandle, String parentText, NotificationCallback notificationCallback) {
        IContentService contentService
                = GlobalObjectRegistry.getObject(EmbeddedSocialServiceProvider.class).getContentService();
        try {
            final GetCommentRequest request = new GetCommentRequest(commentHandle);
            GetCommentResponse response = contentService.getComment(request);
            if (notificationCallback != null) {
                if (parentText == response.getComment().getCommentText()) {
                    Log.d(TAG, "This is the reply comment: " + commentHandle + " with parentText " + parentText);
                    return;
                }
                Log.d(TAG, "Called notifCallback on commentHandle " + commentHandle + " with parentText " + parentText);
                notificationCallback.onReceiveNotif(new ESNotifs.Notif(new Identifier(parentText.getBytes()), response.getComment().getCommentText(), response.getComment().getElapsedSeconds()));
            } else {
                Log.d(TAG, "No notification callback");
            }
            EventBus.post(new GetCommentEvent(response.getComment(), response.getComment() != null));
        } catch (NetworkRequestException e) {
            EventBus.post(new GetCommentEvent(null, false));
        }
    }
    private void getReplyOfNotif(String replyHandle, String parentText, NotificationCallback notificationCallback) {
        IContentService contentService
                = GlobalObjectRegistry.getObject(EmbeddedSocialServiceProvider.class).getContentService();
        try {
            final GetReplyRequest request = new GetReplyRequest(replyHandle);
            GetReplyResponse response = contentService.getReply(request);
            if (notificationCallback != null) {
                if (parentText == response.getReply().getReplyText()) {
                    Log.d(TAG, "This is the reply reply: " + replyHandle + " with parentText " + parentText);
                    return;
                }
                Log.d(TAG, "Called notifCallback on replyHandle " + replyHandle + " with parentText " + parentText);
                notificationCallback.onReceiveNotif(new ESNotifs.Notif(new Identifier(parentText.getBytes()), response.getReply().getReplyText(), response.getReply().getElapsedSeconds()));
            } else {
                Log.d(TAG, "No notification callback");
            }
            EventBus.post(new GetReplyEvent(response.getReply(), response.getReply() != null));
        } catch (NetworkRequestException e) {
            EventBus.post(new GetReplyEvent(null, false));
        }
    }
}
