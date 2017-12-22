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

    public void get_notifications_from_cursor(
            NotificationCallback notificationCallback,
            String cursor,
            boolean is_new)
    {
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
}
