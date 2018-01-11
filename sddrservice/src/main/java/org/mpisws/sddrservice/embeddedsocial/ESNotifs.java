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

import org.mpisws.sddrservice.IEncountersService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.Executors.newCachedThreadPool;

/**
 * Created by tslilyai on 11/14/17.
 */


public class ESNotifs {
    private static final String TAG = ESNotifs.class.getSimpleName();

    private final Fetcher<ActivityView> notifFeedFetcher;
    private ExecutorService executorService;

    public static class Notif {
        private ActivityView activityView;

        public Notif(ActivityView activityView) {
            this.activityView = activityView;
        }

        public long getCreatedTime() {
            return activityView.getCreatedTime();
        }

        public boolean isNewerThan(long time) {
            return activityView.getCreatedTime() > time;
        }
        public String getCursor() {
            return activityView.getHandle();
        }
    }

    public ESNotifs() {
        notifFeedFetcher = FetchersFactory.createNotificationFeedFetcher();
        executorService = newCachedThreadPool();
    }

    public interface GetNotificationsCallback {
        /* Called when new notifications are fetched */
        void onReceiveNotifications(List<Notif> notifs);
    }

    public void get_notifications_from_cursor(
            GetNotificationsCallback getNotificationsCallback,
            String cursor,
            IEncountersService.GetNotificationsRequestFlag flag)
    {
        if (notifFeedFetcher.isLoading()) { return; }
        Callback callback = new Callback() {
            @Override
            public void onStateChanged(FetcherState newState) {
                List<Notif> notifications = new ArrayList<>();
                super.onStateChanged(newState);
                switch (newState) {
                    case LOADING:
                        break;
                    case LAST_ATTEMPT_FAILED:
                        Log.v(TAG, "Last attempt failed");
                        notifFeedFetcher.requestMoreData();
                        break;
                    default: // ENDED or MORE_DATA
                        Log.v(TAG, "Data ended? " + (newState != FetcherState.HAS_MORE_DATA));
                        Log.v(TAG, "Found " + notifFeedFetcher.getAllData().size() + " notifs");
                        for (ActivityView av : notifFeedFetcher.getAllData()) {
                            if (!av.isUnread() && flag == IEncountersService.GetNotificationsRequestFlag.UNREAD_ONLY) {
                                break;
                            }
                            notifications.add(new Notif(av));
                        }
                        getNotificationsCallback.onReceiveNotifications(notifications);
                }
            }
        };
        notifFeedFetcher.setCallbackSilent(callback);
        notifFeedFetcher.clearData();

        if (cursor == null) {
            // this will call the callback after a new page is gotten from the beginning
            notifFeedFetcher.refreshData();
        } else if (notifFeedFetcher.hasMoreData()) {
            notifFeedFetcher.setCursor(cursor);
            notifFeedFetcher.requestMoreData();
        }
    }

    public void get_messages_of_notifications(List<Notif> notifs, ESMsgs.GetMessagesCallback getMessagesCallback, ESMsgs esMsgs) {
        IContentService contentService = GlobalObjectRegistry.getObject(EmbeddedSocialServiceProvider.class).getContentService();
        Runnable r = () -> {
            for (Notif notif : notifs) {
                ActivityView n = notif.activityView;

                try {
                    String eid;
                    if (n.getActivityType() == ActivityType.REPLY) {
                        Log.v(TAG, "Got notification of type reply");
                        final GetReplyRequest request = new GetReplyRequest(n.getHandle());
                        GetReplyResponse response = contentService.getReply(request);

                        final GetCommentRequest req = new GetCommentRequest(response.getReply().getCommentHandle());
                        eid = contentService.getComment(req).getComment().getCommentText();
                    } else if (n.getActivityType() == ActivityType.COMMENT) {
                        Log.v(TAG, "Got notif of type comment");
                        final GetCommentRequest request = new GetCommentRequest(n.getHandle());
                        GetCommentResponse response = contentService.getComment(request);

                        final GetTopicRequest req = new GetTopicRequest(response.getComment().getTopicHandle());
                        eid = contentService.getTopic(req).getTopic().getTopicTitle();

                        if (eid.compareTo(response.getComment().getCommentText()) == 0) {
                            Log.v(TAG, "This is the reply comment: " + response.getComment().getCommentText());
                            continue;
                        }
                    } else {
                        Log.e(TAG, "Notif of no known activity type!");
                        continue;
                    }
                    Log.v(TAG, "Got eid for notif " + eid);
                    esMsgs.find_and_act_on_topic(new ESMsgs.TopicAction(
                            ESMsgs.TopicAction.TATyp.GetMsgs, notif.getCreatedTime(), eid, null, getMessagesCallback));
                } catch (NetworkRequestException e) {}
            }
        };
        executorService.execute(r);
    }
}
